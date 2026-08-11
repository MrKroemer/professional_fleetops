package br.com.proyfebrasil.fleetops.contratos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RN-01 e RN-18 — substituição de veículo e histórico do contrato")
class ContratoDeLocacaoTest {

    private static final LocalDate RETIRADA = LocalDate.of(2025, 1, 10);

    private Locadora locadora;
    private Obra obra;
    private ContratoDeLocacao contrato;

    @BeforeEach
    void preparar() {
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        obra = new Obra("24.019", "SKER Ventos", "Uibaí", "BA");
        contrato = new ContratoDeLocacao(obra, locadora, RETIRADA);
    }

    private Veiculo veiculo(String placa) {
        return new Veiculo(placa, "Onix", CategoriaVeiculo.PASSEIO, Combustivel.FLEX, locadora);
    }

    @Nested
    @DisplayName("substituição")
    class Substituicao {

        @Test
        @DisplayName("RN01_aSubstituicaoEncerraOPeriodoAnteriorSemLacunaNemSobreposicao")
        void semLacunaNemSobreposicao() {
            Veiculo primeiro = veiculo("ABC1D23");
            Veiculo segundo = veiculo("XYZ9K87");

            contrato.colocarVeiculo(primeiro, RETIRADA, "Retirada inicial");
            LocalDate troca = LocalDate.of(2025, 6, 1);
            contrato.colocarVeiculo(segundo, troca, "Substituição por avaria");

            var periodos = contrato.getSubstituicoes();
            assertThat(periodos).hasSize(2);
            // O período anterior fecha na véspera do novo: sem buraco e sem dois
            // veículos válidos no mesmo dia.
            assertThat(periodos.get(0).getFim()).isEqualTo(troca.minusDays(1));
            assertThat(periodos.get(1).getInicio()).isEqualTo(troca);
            assertThat(periodos.get(1).getFim()).isNull();
        }

        @Test
        @DisplayName("RN01_oContratoTemExatamenteUmVeiculoAtivoPorVez")
        void umVeiculoAtivoPorVez() {
            contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);
            contrato.colocarVeiculo(veiculo("XYZ9K87"), LocalDate.of(2025, 6, 1), null);

            assertThat(contrato.getSubstituicoes().stream().filter(SubstituicaoVeiculo::emCurso))
                    .hasSize(1);
            assertThat(contrato.getVeiculoAtual().getPlaca()).isEqualTo("XYZ9K87");
        }

        @Test
        @DisplayName("RN01_deveRecusarSubstituicaoAnteriorAoInicioDoPeriodoEmCurso")
        void recusaSubstituicaoRetroativa() {
            contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);

            assertThatThrownBy(() ->
                            contrato.colocarVeiculo(veiculo("XYZ9K87"), RETIRADA.minusDays(5), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("depois de");
        }

        @Test
        @DisplayName("RN01_deveRecusarSubstituicaoNoMesmoDiaDaColocacao")
        void recusaSubstituicaoNoMesmoDia() {
            contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);

            // Fechar o período anterior na véspera do próprio início produziria um
            // intervalo negativo, que nenhuma consulta temporal saberia interpretar.
            assertThatThrownBy(() -> contrato.colocarVeiculo(veiculo("XYZ9K87"), RETIRADA, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("depois de");
        }

        @Test
        @DisplayName("RN01_umDiaDepoisJaEUmaSubstituicaoValida")
        void aceitaSubstituicaoNoDiaSeguinte() {
            contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);
            contrato.colocarVeiculo(veiculo("XYZ9K87"), RETIRADA.plusDays(1), null);

            var periodos = contrato.getSubstituicoes();
            assertThat(periodos.get(0).getInicio()).isEqualTo(RETIRADA);
            assertThat(periodos.get(0).getFim()).isEqualTo(RETIRADA);
            assertThat(periodos.get(1).getInicio()).isEqualTo(RETIRADA.plusDays(1));
        }

        @Test
        @DisplayName("conta as trocas, sem contar a retirada inicial como substituição")
        void contaTrocas() {
            assertThat(contrato.quantidadeDeSubstituicoes()).isZero();

            contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);
            assertThat(contrato.quantidadeDeSubstituicoes()).isZero();

            contrato.colocarVeiculo(veiculo("XYZ9K87"), LocalDate.of(2025, 6, 1), null);
            assertThat(contrato.quantidadeDeSubstituicoes()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("consulta temporal")
    class ConsultaTemporal {

        @Test
        @DisplayName("RN18_deveResponderQualVeiculoEstavaNoContratoEmUmaDataQualquer")
        void respondeConsultaTemporal() {
            Veiculo primeiro = veiculo("ABC1D23");
            Veiculo segundo = veiculo("XYZ9K87");
            LocalDate troca = LocalDate.of(2025, 6, 1);

            contrato.colocarVeiculo(primeiro, RETIRADA, null);
            contrato.colocarVeiculo(segundo, troca, null);

            // Antes da retirada: nenhum veículo estava no contrato.
            assertThat(contrato.veiculoEm(RETIRADA.minusDays(1))).isEmpty();
            assertThat(contrato.veiculoEm(RETIRADA)).contains(primeiro);
            assertThat(contrato.veiculoEm(LocalDate.of(2025, 3, 15))).contains(primeiro);
            // A véspera da troca ainda é do primeiro; o dia da troca já é do segundo.
            assertThat(contrato.veiculoEm(troca.minusDays(1))).contains(primeiro);
            assertThat(contrato.veiculoEm(troca)).contains(segundo);
            assertThat(contrato.veiculoEm(LocalDate.of(2026, 1, 1))).contains(segundo);
        }

        @Test
        @DisplayName("RN18_oHistoricoSobreviveAoEncerramentoDoContrato")
        void historicoSobreviveAoEncerramento() {
            Veiculo primeiro = veiculo("ABC1D23");
            contrato.colocarVeiculo(primeiro, RETIRADA, null);
            contrato.colocarVeiculo(veiculo("XYZ9K87"), LocalDate.of(2025, 6, 1), null);

            contrato.encerrar(StatusContrato.DEVOLVIDO, LocalDate.of(2025, 12, 31));

            assertThat(contrato.getSubstituicoes()).hasSize(2);
            assertThat(contrato.veiculoEm(LocalDate.of(2025, 3, 15))).contains(primeiro);
        }
    }

    @Nested
    @DisplayName("encerramento")
    class Encerramento {

        @Test
        @DisplayName("encerrar fecha o período do veículo em curso")
        void fechaPeriodoEmCurso() {
            contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);
            LocalDate fim = LocalDate.of(2025, 12, 31);

            contrato.encerrar(StatusContrato.DEVOLVIDO, fim);

            // Um contrato encerrado com período aberto deixaria o veículo eternamente
            // "em uso" em qualquer relatório que consulte o histórico.
            assertThat(contrato.periodoEmCurso()).isEmpty();
            assertThat(contrato.getSubstituicoes().get(0).getFim()).isEqualTo(fim);
            assertThat(contrato.estaAtivo()).isFalse();
        }

        @Test
        @DisplayName("encerrar exige um status diferente de ATIVO")
        void recusaEncerrarComoAtivo() {
            assertThatThrownBy(() -> contrato.encerrar(StatusContrato.ATIVO, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("RN17_desmobilizadoAindaOcupaVeiculo_devolvidoNao")
        void statusIndicaOcupacao() {
            // A distinção importa: desmobilizado voltou da obra mas ainda está com a
            // empresa; devolvido saiu da frota.
            assertThat(StatusContrato.ATIVO.ocupaVeiculo()).isTrue();
            assertThat(StatusContrato.DESMOBILIZADO.ocupaVeiculo()).isTrue();
            assertThat(StatusContrato.DEVOLVIDO.ocupaVeiculo()).isFalse();
            assertThat(StatusContrato.INATIVO.ocupaVeiculo()).isFalse();
        }
    }

    @Test
    @DisplayName("recusa período com fim anterior ao início")
    void recusaPeriodoInvertido() {
        contrato.colocarVeiculo(veiculo("ABC1D23"), RETIRADA, null);

        assertThatThrownBy(() -> contrato.encerrar(StatusContrato.DEVOLVIDO, RETIRADA.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior ao início");
    }

    @Test
    @DisplayName("recusa pacote de KM ou valor mensal inválidos")
    void recusaDadosInvalidos() {
        assertThatThrownBy(() -> contrato.alterarDadosDaLocacao("24.019", "Recife", 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        contrato.alterarDadosDaLocacao("24.019", "Recife", 3000, new java.math.BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
