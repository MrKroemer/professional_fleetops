package br.com.proyfebrasil.fleetops.operacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.DadosPosto;
import br.com.proyfebrasil.fleetops.cadastros.domain.DiaDaSemana;
import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeAbastecimento;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeKm;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeServico;
import br.com.proyfebrasil.fleetops.operacao.domain.RegistroDeKm;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import br.com.proyfebrasil.fleetops.operacao.infra.AbastecimentoRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.RegistroDeKmRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.ServicoOperacionalRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Lançamentos da operação mensal — RN-03, RN-04 e RN-05")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoDeLancamentosTest {

    private static final Long CONTRATO = 7L;
    /** 11/03/2026 é uma quarta-feira — usada para exercitar os dias autorizados. */
    private static final LocalDate QUARTA = LocalDate.of(2026, 3, 11);

    @Mock
    private ContratoRepository contratos;

    @Mock
    private RegistroDeKmRepository registros;

    @Mock
    private AbastecimentoRepository abastecimentos;

    @Mock
    private ServicoOperacionalRepository servicos;

    private ServicoDeLancamentos servico;
    private ContratoDeLocacao contrato;
    private Obra obra;

    @BeforeEach
    void preparar() {
        servico = new ServicoDeLancamentos(contratos, registros, abastecimentos, servicos);
        obra = new Obra("24.019", "SKER", "Uibaí", "BA");
        ReflectionTestUtils.setField(obra, "id", 3L);
        contrato = new ContratoDeLocacao(obra, new Locadora("Unidas", TipoLocadora.NACIONAL), QUARTA);
        ReflectionTestUtils.setField(contrato, "id", CONTRATO);

        when(contratos.buscarPorId(CONTRATO)).thenReturn(Optional.of(contrato));
        when(registros.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(abastecimentos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(servicos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(registros.anteriorA(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(registros.posteriorA(anyLong(), any(), any())).thenReturn(Optional.empty());
    }

    private RegistroDeKm registro(LocalDate data, int inicial, int fim) {
        var registro = new RegistroDeKm(contrato, data, inicial, fim);
        ReflectionTestUtils.setField(registro, "id", 100L);
        return registro;
    }

    private static DadosDeKm km(LocalDate data, int inicial, int fim) {
        return new DadosDeKm(null, data, inicial, fim, "Uibaí", "Salvador", null);
    }

    /** Posto credenciado para a obra, atendendo às quartas. */
    private Fornecedor postoCredenciado(Set<DiaDaSemana> dias) {
        var posto = new Fornecedor(TipoFornecedor.POSTO, "Posto Uibaí");
        ReflectionTestUtils.setField(posto, "id", 11L);
        posto.definirObras(Set.of(obra));
        posto.definirDadosPosto(new DadosPosto(dias, null));
        return posto;
    }

    @Nested
    @DisplayName("RN-03 — encadeamento do hodômetro")
    class Quilometragem {

        @Test
        @DisplayName("RN03_recusaKmInicialMenorQueOFinalDoRegistroAnterior")
        void naoRetrocede() {
            when(registros.anteriorA(anyLong(), any(), any()))
                    .thenReturn(Optional.of(registro(QUARTA.minusDays(1), 10_000, 10_250)));

            assertThatThrownBy(() -> servico.lancarKm(CONTRATO, km(QUARTA, 10_100, 10_400)))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("não anda para trás")
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-003-KM_RETROCEDE");
        }

        @Test
        @DisplayName("RN03_umLancamentoRetroativoPrecisaCaberEntreOsVizinhos")
        void naoAtropelaOPosterior() {
            when(registros.anteriorA(anyLong(), any(), any()))
                    .thenReturn(Optional.of(registro(QUARTA.minusDays(2), 10_000, 10_100)));
            when(registros.posteriorA(anyLong(), any(), any()))
                    .thenReturn(Optional.of(registro(QUARTA.plusDays(2), 10_300, 10_500)));

            // Conferir só para trás deixaria este registro passar, e o erro só apareceria
            // no fechamento — com o hodômetro andando para trás no meio do mês.
            assertThatThrownBy(() -> servico.lancarKm(CONTRATO, km(QUARTA, 10_100, 10_900)))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("não cabe entre os registros");
        }

        @Test
        @DisplayName("encaixar exatamente nos limites dos vizinhos é válido")
        void encaixePerfeito() {
            when(registros.anteriorA(anyLong(), any(), any()))
                    .thenReturn(Optional.of(registro(QUARTA.minusDays(1), 10_000, 10_100)));
            when(registros.posteriorA(anyLong(), any(), any()))
                    .thenReturn(Optional.of(registro(QUARTA.plusDays(1), 10_300, 10_500)));

            assertThatCode(() -> servico.lancarKm(CONTRATO, km(QUARTA, 10_100, 10_300)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RN03_kmFinalMenorQueOInicialEhRecusadoNoProprioRegistro")
        void finalMenorQueInicial() {
            assertThatThrownBy(() -> servico.lancarKm(CONTRATO, km(QUARTA, 10_500, 10_100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não pode ser menor");
        }

        @Test
        @DisplayName("o percorrido é derivado, nunca digitado")
        void percorridoDerivado() {
            var lancado = servico.lancarKm(CONTRATO, km(QUARTA, 10_000, 10_240));

            assertThat(lancado.kmPercorrido()).isEqualTo(240);
        }
    }

    @Nested
    @DisplayName("RN-04 — abastecimento")
    class Combustivel {

        @Test
        @DisplayName("RN04_umAbastecimentoPorDia_eOSegundoEhRecusado")
        void tetoDiario() {
            when(abastecimentos.existeNoDia(CONTRATO, QUARTA, null)).thenReturn(true);

            // Recusado, e não aceito como não conforme: duas notas no mesmo dia quase
            // sempre são a mesma nota lançada duas vezes.
            assertThatThrownBy(() -> servico.lancarAbastecimento(CONTRATO,
                    new DadosDeAbastecimento(null, QUARTA, new BigDecimal("300"), null, null, null, "x")))
                    .isInstanceOf(NegocioException.class)
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-004-ABASTECIMENTO_DUPLICADO");
        }

        @Test
        @DisplayName("RN04_postoCredenciadoEmDiaAutorizadoEhConforme")
        void conforme() {
            var posto = postoCredenciado(Set.of(DiaDaSemana.QUA));

            var lancado = servico.lancarAbastecimento(CONTRATO,
                    new DadosDeAbastecimento(posto, QUARTA, new BigDecimal("300"), new BigDecimal("50"), 10_500, null, null));

            assertThat(lancado.isNaoConforme()).isFalse();
            assertThat(lancado.getJustificativa()).isNull();
            assertThat(lancado.precoPorLitro()).hasValueSatisfying(
                    preco -> assertThat(preco).isEqualByComparingTo("6.000"));
        }

        @Test
        @DisplayName("RN04_diaNaoAutorizadoExigeJustificativa_eEntaoEhAceito")
        void diaNaoAutorizado() {
            var posto = postoCredenciado(Set.of(DiaDaSemana.SEG, DiaDaSemana.TER));
            var semJustificativa = new DadosDeAbastecimento(
                    posto, QUARTA, new BigDecimal("300"), null, null, null, null);

            assertThatThrownBy(() -> servico.lancarAbastecimento(CONTRATO, semJustificativa))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("dias autorizados")
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-004-JUSTIFICATIVA_OBRIGATORIA");

            // Com justificativa, o fato é registrado — recusá-lo faria o gestor não lançar
            // o gasto, perdendo o custo e a não conformidade de uma vez.
            var lancado = servico.lancarAbastecimento(CONTRATO, new DadosDeAbastecimento(
                    posto, QUARTA, new BigDecimal("300"), null, null, null, "Tanque na reserva em viagem."));

            assertThat(lancado.isNaoConforme()).isTrue();
            assertThat(lancado.getJustificativa()).contains("reserva");
        }

        @Test
        @DisplayName("RN04_postoNaoCredenciadoParaAobraTambemEhNaoConformidade")
        void postoDeOutraObra() {
            var posto = new Fornecedor(TipoFornecedor.POSTO, "Posto de Fora");
            ReflectionTestUtils.setField(posto, "id", 12L);
            posto.definirDadosPosto(new DadosPosto(Set.of(DiaDaSemana.QUA), null));

            var avaliacao = servico.avaliarAbastecimento(CONTRATO, posto, QUARTA);

            assertThat(avaliacao.conforme()).isFalse();
            assertThat(avaliacao.motivos()).anyMatch(motivo -> motivo.contains("não está credenciado"));
        }

        @Test
        @DisplayName("posto sem dias cadastrados não gera não conformidade por dia")
        void postoSemDiasCadastrados() {
            // Ausência de configuração é lacuna de cadastro, não irregularidade do
            // abastecimento — tratá-la como violação puniria o lançamento pelo cadastro.
            var posto = postoCredenciado(Set.of());

            assertThat(servico.avaliarAbastecimento(CONTRATO, posto, QUARTA).conforme()).isTrue();
        }
    }

    @Nested
    @DisplayName("RN-05 — lava-jato")
    class Servicos {

        @Test
        @DisplayName("RN05_umLavaJatoPorSemanaDeCalendario")
        void umPorSemana() {
            // A semana é de segunda a domingo: 09/03 a 15/03 para uma quarta em 11/03.
            when(servicos.existeNaJanela(CONTRATO, TipoDeServico.LAVA_JATO,
                    LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15), null)).thenReturn(true);

            assertThatThrownBy(() -> servico.lancarServico(CONTRATO, new DadosDeServico(
                    TipoDeServico.LAVA_JATO, null, QUARTA, new BigDecimal("60"), null, null)))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("semana de 2026-03-09 a 2026-03-15");
        }

        @Test
        @DisplayName("com justificativa, o segundo lava-jato da semana é registrado como não conforme")
        void segundoComJustificativa() {
            when(servicos.existeNaJanela(any(), any(), any(), any(), any())).thenReturn(true);

            var lancado = servico.lancarServico(CONTRATO, new DadosDeServico(
                    TipoDeServico.LAVA_JATO, null, QUARTA, new BigDecimal("60"), null, "Veículo enlameado na obra."));

            assertThat(lancado.isNaoConforme()).isTrue();
        }

        @Test
        @DisplayName("RN05_naoSeAplicaAborrachariaNemParaBrisas")
        void semLimiteNosOutros() {
            when(servicos.existeNaJanela(any(), any(), any(), any(), any())).thenReturn(true);

            // Trocar dois pneus na mesma semana é azar, não irregularidade.
            var pneu = servico.lancarServico(CONTRATO, new DadosDeServico(
                    TipoDeServico.BORRACHARIA, null, QUARTA, new BigDecimal("250"), "Pneu dianteiro", null));

            assertThat(pneu.isNaoConforme()).isFalse();
            assertThat(TipoDeServico.BORRACHARIA.isLimitadoPorSemana()).isFalse();
            assertThat(TipoDeServico.LAVA_JATO.isLimitadoPorSemana()).isTrue();
        }
    }
}
