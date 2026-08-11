package br.com.proyfebrasil.fleetops.contratos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Serviço de contratos de locação")
class ServicoDeContratosTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);
    private static final String CPF = "52998224725";

    @Mock
    private ContratoRepository contratos;

    private ServicoDeContratos servico;
    private Obra obra;
    private Locadora locadora;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDeContratos(contratos, relogio);
        obra = new Obra("24.019", "SKER Ventos", "Uibaí", "BA");
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        when(contratos.save(any(ContratoDeLocacao.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Veiculo veiculo(String placa) {
        return new Veiculo(placa, "Onix", CategoriaVeiculo.PASSEIO, Combustivel.FLEX, locadora);
    }

    private ServicoDeContratos.DadosDoContrato dados(
            Veiculo veiculo,
            LocalDate retirada,
            LocalDate encerramento,
            StatusContrato status,
            ServicoDeContratos.TrocaDeVeiculo... trocas) {
        return new ServicoDeContratos.DadosDoContrato(
                obra, locadora, veiculo, null, "24.019", "Uibaí - BA",
                retirada, encerramento, 3000, new BigDecimal("2606.08"), status, null, List.of(trocas));
    }

    /** Abre e devolve só o contrato, para os testes que não olham as recusas. */
    private ContratoDeLocacao abrir(ServicoDeContratos.DadosDoContrato dados) {
        return servico.abrir(dados).contrato();
    }

    @Test
    @DisplayName("abrir cria o contrato e coloca o veículo na data da retirada")
    void abreNaDataDaRetirada() {
        LocalDate retirada = LocalDate.of(2025, 2, 26);

        ContratoDeLocacao contrato =
                abrir(dados(veiculo("ABC1D23"), retirada, null, StatusContrato.ATIVO));

        assertThat(contrato.getDataRetirada()).isEqualTo(retirada);
        assertThat(contrato.estaAtivo()).isTrue();
        assertThat(contrato.getPacoteKmContratado()).isEqualTo(3000);
        // O período começa na retirada, não hoje: a carga histórica precisa reproduzir
        // o que de fato aconteceu.
        assertThat(contrato.getSubstituicoes().get(0).getInicio()).isEqualTo(retirada);
    }

    @Test
    @DisplayName("sem data de retirada, o período começa no encerramento — nunca depois dele")
    void semRetiradaUsaOEncerramento() {
        LocalDate encerramento = LocalDate.of(2024, 11, 5);

        ContratoDeLocacao contrato =
                abrir(dados(veiculo("ABC1D23"), null, encerramento, StatusContrato.DEVOLVIDO));

        // Usar "hoje" como início faria um contrato de 2024 nascer com o período
        // começando depois do próprio encerramento — foi o caso encontrado no acervo.
        assertThat(contrato.getSubstituicoes().get(0).getInicio()).isEqualTo(encerramento);
        assertThat(contrato.getSubstituicoes().get(0).getFim()).isEqualTo(encerramento);
        assertThat(contrato.getStatus()).isEqualTo(StatusContrato.DEVOLVIDO);
    }

    @Test
    @DisplayName("sem retirada nem encerramento, o período começa hoje")
    void semDatasUsaHoje() {
        ContratoDeLocacao contrato =
                abrir(dados(veiculo("ABC1D23"), null, null, StatusContrato.ATIVO));

        assertThat(contrato.getSubstituicoes().get(0).getInicio()).isEqualTo(HOJE);
    }

    @Test
    @DisplayName("abrir com status encerrado já fecha o contrato e o período")
    void abreJaEncerrado() {
        LocalDate retirada = LocalDate.of(2024, 1, 10);
        LocalDate fim = LocalDate.of(2024, 12, 20);

        ContratoDeLocacao contrato =
                abrir(dados(veiculo("ABC1D23"), retirada, fim, StatusContrato.DEVOLVIDO));

        assertThat(contrato.estaAtivo()).isFalse();
        assertThat(contrato.getDataEncerramento()).isEqualTo(fim);
        assertThat(contrato.periodoEmCurso()).isEmpty();
    }

    @Test
    @DisplayName("RN01_substituirVeiculoEncerraOPeriodoAnterior")
    void substituiVeiculo() {
        ContratoDeLocacao contrato = abrir(
                dados(veiculo("ABC1D23"), LocalDate.of(2025, 1, 10), null, StatusContrato.ATIVO));
        ReflectionTestUtils.setField(contrato, "id", 7L);
        when(contratos.buscarPorId(7L)).thenReturn(Optional.of(contrato));

        LocalDate troca = LocalDate.of(2025, 6, 1);
        servico.substituirVeiculo(7L, veiculo("XYZ9K87"), troca, "Avaria");

        assertThat(contrato.getVeiculoAtual().getPlaca()).isEqualTo("XYZ9K87");
        assertThat(contrato.getSubstituicoes().get(0).getFim()).isEqualTo(troca.minusDays(1));
        assertThat(contrato.quantidadeDeSubstituicoes()).isEqualTo(1);
    }

    @Test
    @DisplayName("encerrar muda o status e fecha o período em curso")
    void encerra() {
        ContratoDeLocacao contrato = abrir(
                dados(veiculo("ABC1D23"), LocalDate.of(2025, 1, 10), null, StatusContrato.ATIVO));
        ReflectionTestUtils.setField(contrato, "id", 7L);
        when(contratos.buscarPorId(7L)).thenReturn(Optional.of(contrato));

        LocalDate fim = LocalDate.of(2026, 1, 31);
        servico.encerrar(7L, StatusContrato.DESMOBILIZADO, fim);

        assertThat(contrato.getStatus()).isEqualTo(StatusContrato.DESMOBILIZADO);
        assertThat(contrato.periodoEmCurso()).isEmpty();
    }

    @Test
    @DisplayName("RN01_asTrocasEntramAntesDoFechoDoContratoEncerrado")
    void aplicaTrocasAntesDeEncerrar() {
        LocalDate retirada = LocalDate.of(2024, 1, 10);
        LocalDate devolucao = LocalDate.of(2024, 12, 20);
        LocalDate troca = LocalDate.of(2024, 6, 1);

        ContratoDeLocacao contrato = abrir(dados(
                veiculo("ABC1D23"), retirada, devolucao, StatusContrato.DEVOLVIDO,
                new ServicoDeContratos.TrocaDeVeiculo(veiculo("XYZ9K87"), troca, "Avaria")));

        // Encerrar antes da troca fecharia o primeiro período em 20/12 e abriria o
        // segundo em 01/06, um por cima do outro — sobreposição que o banco rejeita.
        var periodos = contrato.getSubstituicoes();
        assertThat(periodos).hasSize(2);
        assertThat(periodos.get(0).getFim()).isEqualTo(troca.minusDays(1));
        assertThat(periodos.get(1).getInicio()).isEqualTo(troca);
        assertThat(periodos.get(1).getFim()).isEqualTo(devolucao);
        assertThat(contrato.periodoEmCurso()).isEmpty();
    }

    @Test
    @DisplayName("RN24_trocaForaDaLinhaDoTempoEDevolvidaComMotivo")
    void recusaTrocaIncoerente() {
        LocalDate retirada = LocalDate.of(2024, 1, 10);
        LocalDate devolucao = LocalDate.of(2024, 12, 20);

        // Uma recusa não pode derrubar a carga inteira: o contrato entra, e o que ficou
        // de fora volta descrito, para virar linha do relatório de rejeições.
        var resultado = servico.abrir(dados(
                veiculo("ABC1D23"), retirada, devolucao, StatusContrato.DEVOLVIDO,
                new ServicoDeContratos.TrocaDeVeiculo(veiculo("XYZ9K87"), retirada, "mesmo dia"),
                new ServicoDeContratos.TrocaDeVeiculo(veiculo("QRS4T56"), LocalDate.of(2025, 3, 1), "tarde demais")));

        assertThat(resultado.contrato().getSubstituicoes()).hasSize(1);
        assertThat(resultado.recusadas()).hasSize(2);
        assertThat(resultado.recusadas().get(0)).contains("XYZ9K87", "início do período em curso");
        assertThat(resultado.recusadas().get(1)).contains("QRS4T56", "posterior ao encerramento");
    }

    @Test
    @DisplayName("o condutor informado vira o condutor atual do contrato")
    void defineCondutor() {
        Condutor condutor = new Condutor("Ana Souza", CPF);
        var comCondutor = new ServicoDeContratos.DadosDoContrato(
                obra, locadora, veiculo("ABC1D23"), condutor, "24.019", "Uibaí - BA",
                LocalDate.of(2025, 1, 10), null, 3000, null, StatusContrato.ATIVO, null, List.of());

        assertThat(abrir(comCondutor).getCondutorAtual()).isEqualTo(condutor);
    }
}
