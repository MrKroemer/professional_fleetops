package br.com.proyfebrasil.fleetops.contratos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-18 — histórico de condutores do contrato")
class HistoricoDeCondutorTest {

    private static final LocalDate RETIRADA = LocalDate.of(2025, 1, 10);
    private static final LocalDate CNH_LONGE = LocalDate.of(2030, 12, 31);

    private Locadora locadora;
    private ContratoDeLocacao contrato;

    @BeforeEach
    void preparar() {
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        contrato = new ContratoDeLocacao(new Obra("24.019", "SKER", "Uibaí", "BA"), locadora, RETIRADA);
        contrato.colocarVeiculo(
                new Veiculo("ABC1D23", "Polo", CategoriaVeiculo.PASSEIO, Combustivel.FLEX, locadora),
                RETIRADA, "Retirada inicial");
    }

    private static Condutor condutor(String nome, String cpf, LocalDate validadeDaCnh) {
        Condutor condutor = new Condutor(nome, cpf);
        condutor.alterarCnh("123456789", "AD", validadeDaCnh);
        return condutor;
    }

    @Test
    @DisplayName("RN18_aTrocaEncerraOPeriodoAnteriorSemLacunaNemSobreposicao")
    void trocaSemLacuna() {
        LocalDate troca = LocalDate.of(2025, 6, 1);
        contrato.colocarCondutor(condutor("Ana Souza", "52998224725", CNH_LONGE), RETIRADA, null);
        contrato.colocarCondutor(condutor("Bruno Lima", "11144477735", CNH_LONGE), troca, "Férias");

        var periodos = contrato.getTrocasDeCondutor();
        assertThat(periodos).hasSize(2);
        assertThat(periodos.get(0).getFim()).isEqualTo(troca.minusDays(1));
        assertThat(periodos.get(1).getInicio()).isEqualTo(troca);
        assertThat(periodos.get(1).emCurso()).isTrue();
        assertThat(contrato.quantidadeDeTrocasDeCondutor()).isEqualTo(1);
    }

    @Test
    @DisplayName("RN18_respondeQuemDirigiaEmUmaDataQualquer")
    void consultaTemporal() {
        Condutor ana = condutor("Ana Souza", "52998224725", CNH_LONGE);
        Condutor bruno = condutor("Bruno Lima", "11144477735", CNH_LONGE);
        contrato.colocarCondutor(ana, RETIRADA, null);
        contrato.colocarCondutor(bruno, LocalDate.of(2025, 6, 1), "Férias");

        // A pergunta da especificação: "quem dirigia a placa X em 15/03?" — respondida
        // pelo histórico, não pelo ponteiro, que já aponta para o Bruno.
        assertThat(contrato.condutorEm(LocalDate.of(2025, 3, 15))).contains(ana);
        assertThat(contrato.condutorEm(LocalDate.of(2025, 5, 31))).contains(ana);
        assertThat(contrato.condutorEm(LocalDate.of(2025, 6, 1))).contains(bruno);
        assertThat(contrato.condutorEm(LocalDate.of(2026, 1, 1))).contains(bruno);
        assertThat(contrato.getCondutorAtual()).isEqualTo(bruno);
        // Antes da retirada não havia contrato — e portanto não havia condutor.
        assertThat(contrato.condutorEm(LocalDate.of(2024, 12, 31))).isEmpty();
    }

    @Test
    @DisplayName("RN18_oHistoricoDeCondutorSobreviveAoEncerramentoDoContrato")
    void historicoSobreviveAoEncerramento() {
        Condutor ana = condutor("Ana Souza", "52998224725", CNH_LONGE);
        contrato.colocarCondutor(ana, RETIRADA, null);
        LocalDate fim = LocalDate.of(2025, 9, 30);

        contrato.encerrar(StatusContrato.DEVOLVIDO, fim);

        assertThat(contrato.periodoDeCondutorEmCurso()).isEmpty();
        assertThat(contrato.getTrocasDeCondutor().get(0).getFim()).isEqualTo(fim);
        // Encerrado, mas ainda respondível.
        assertThat(contrato.condutorEm(LocalDate.of(2025, 3, 15))).contains(ana);
    }

    @Test
    @DisplayName("RN18_recusaTrocaNoMesmoDia_ouAnteriorAoPeriodoEmCurso")
    void recusaTrocaForaDeOrdem() {
        contrato.colocarCondutor(condutor("Ana Souza", "52998224725", CNH_LONGE), RETIRADA, null);
        Condutor bruno = condutor("Bruno Lima", "11144477735", CNH_LONGE);

        assertThatThrownBy(() -> contrato.colocarCondutor(bruno, RETIRADA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depois de");
        assertThatThrownBy(() -> contrato.colocarCondutor(bruno, RETIRADA.minusDays(3), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RN16_cnhVencidaNaDataDoVinculoBloqueiaATroca")
    void cnhVencidaBloqueia() {
        Condutor vencido = condutor("Carlos Dias", "11144477735", LocalDate.of(2024, 12, 31));

        assertThatThrownBy(() -> contrato.colocarCondutor(vencido, RETIRADA, null))
                .isInstanceOf(NegocioException.class)
                .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                .isEqualTo("RN-016-CONDUTOR_COM_CNH_VENCIDA");
    }

    @Test
    @DisplayName("RN16_aCnhEAvaliadaNaDataDoVinculo_naoNaDataDeHoje")
    void cnhAvaliadaNaDataDoVinculo() {
        // Um condutor cuja CNH venceu depois do vínculo dirigiu legitimamente naquele
        // período. Recusar o registro faria o sistema negar um fato consumado.
        Condutor condutor = condutor("Ana Souza", "52998224725", LocalDate.of(2025, 3, 1));

        contrato.colocarCondutor(condutor, RETIRADA, null);

        assertThat(contrato.condutorEm(RETIRADA)).contains(condutor);
    }
}
