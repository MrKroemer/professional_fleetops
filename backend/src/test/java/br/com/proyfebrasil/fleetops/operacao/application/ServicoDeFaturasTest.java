package br.com.proyfebrasil.fleetops.operacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas.DadosDaFatura;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas.DadosDoUsoParticular;
import br.com.proyfebrasil.fleetops.operacao.domain.FaturaDaLocadora;
import br.com.proyfebrasil.fleetops.operacao.domain.StatusDeConferencia;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeUsoParticular;
import br.com.proyfebrasil.fleetops.operacao.domain.UsoParticular;
import br.com.proyfebrasil.fleetops.operacao.infra.FaturaRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.UsoParticularRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

@DisplayName("Faturas (RN-13) e uso particular (RN-10) — serviço")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoDeFaturasTest {

    private static final Long CONTRATO = 44L;
    private static final YearMonth MARCO = YearMonth.of(2026, 3);

    @Mock private ContratoRepository contratos;
    @Mock private FaturaRepository faturas;
    @Mock private UsoParticularRepository usos;

    private ServicoDeFaturas servico;
    private ContratoDeLocacao contrato;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(
                LocalDate.of(2026, 4, 5).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDeFaturas(contratos, faturas, usos, relogio);

        contrato = new ContratoDeLocacao(
                new Obra("25.001", "Auren", "Ponta Negra", "RN"),
                new Locadora("Unidas", TipoLocadora.NACIONAL),
                LocalDate.of(2025, 6, 1));
        ReflectionTestUtils.setField(contrato, "id", CONTRATO);

        when(contratos.buscarPorId(CONTRATO)).thenReturn(Optional.of(contrato));
        when(faturas.daCompetencia(anyLong(), any())).thenReturn(Optional.empty());
        when(faturas.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(usos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    private static DadosDaFatura fatura(String faturado, StatusDeConferencia status, String obs) {
        return new DadosDaFatura(
                MARCO, new BigDecimal("2606.08"), new BigDecimal(faturado), BigDecimal.ZERO,
                "NF-99213", LocalDate.of(2026, 4, 10), status, obs);
    }

    private static Condutor condutor() {
        var condutor = new Condutor("Ana Souza", "52998224725");
        condutor.alterarCnh("123", "AD", LocalDate.of(2030, 1, 1));
        return condutor;
    }

    @Nested
    @DisplayName("faturas")
    class Faturas {

        @Test
        @DisplayName("RN13_umaFaturaPorCompetencia")
        void naoDuplica() {
            var existente = new FaturaDaLocadora(contrato, MARCO);
            ReflectionTestUtils.setField(existente, "id", 9L);
            when(faturas.daCompetencia(CONTRATO, MARCO.atDay(1))).thenReturn(Optional.of(existente));

            // A segunda quase sempre é a mesma nota digitada de novo; duas linhas
            // dobrariam a divergência apurada sem que nada acusasse.
            assertThatThrownBy(() -> servico.lancar(CONTRATO, fatura("3006.08", null, null)))
                    .isInstanceOf(NegocioException.class)
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-013-FATURA_DUPLICADA");
            verify(faturas, never()).save(any());
        }

        @Test
        @DisplayName("lançada sem status, nasce pendente — e a divergência não bloqueia o registro")
        void nascePendente() {
            var lancada = servico.lancar(CONTRATO, fatura("3006.08", null, null));

            assertThat(lancada.getStatus()).isEqualTo(StatusDeConferencia.PENDENTE);
            assertThat(lancada.getDivergencia()).isEqualByComparingTo("400.00");
            assertThat(lancada.exigeTratativa()).isTrue();
        }

        @Test
        @DisplayName("RN13_osValoresSaoAplicadosAntesDaConferencia")
        void ordemDeAplicacao() {
            var lancada = servico.lancar(CONTRATO, fatura("2606.08", StatusDeConferencia.OK, null));

            // Conferir antes de atualizar os números validaria o status contra a
            // divergência anterior — exatamente o erro que a RN-13 existe para impedir.
            assertThat(lancada.getDivergencia()).isEqualByComparingTo("0.00");
            assertThat(lancada.getStatus()).isEqualTo(StatusDeConferencia.OK);
            assertThat(lancada.getNumeroDaNota()).isEqualTo("NF-99213");
        }

        @Test
        @DisplayName("a atualização respeita a RN-13")
        void atualizacaoRespeitaARegra() {
            var existente = new FaturaDaLocadora(contrato, MARCO);
            ReflectionTestUtils.setField(existente, "id", 9L);
            when(faturas.buscarPorId(9L)).thenReturn(Optional.of(existente));

            assertThatThrownBy(() ->
                    servico.atualizar(9L, fatura("3006.08", StatusDeConferencia.OK, "ok")))
                    .isInstanceOf(NegocioException.class);

            var ajustada = servico.atualizar(
                    9L, fatura("3006.08", StatusDeConferencia.AJUSTADA, "Nota de crédito emitida."));
            assertThat(ajustada.exigeTratativa()).isFalse();
        }

        @Test
        @DisplayName("excluir marca a exclusão lógica, sem apagar o histórico")
        void exclusaoLogica() {
            var existente = new FaturaDaLocadora(contrato, MARCO);
            when(faturas.buscarPorId(9L)).thenReturn(Optional.of(existente));

            servico.excluirFatura(9L);

            assertThat(existente.isExcluida()).isTrue();
        }

        @Test
        @DisplayName("fatura inexistente resulta em 404")
        void faturaInexistente() {
            when(faturas.buscarPorId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.excluirFatura(99L))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        @DisplayName("a lista de divergentes é a fila de tratativa")
        void divergentes() {
            when(faturas.comDivergenciaEmAberto()).thenReturn(List.of());

            assertThat(servico.comDivergenciaEmAberto()).isEmpty();
            verify(faturas).comDivergenciaEmAberto();
        }
    }

    @Nested
    @DisplayName("uso particular")
    class Uso {

        private DadosDoUsoParticular dados(Integer km, boolean aceitar) {
            return new DadosDoUsoParticular(
                    condutor(), TipoDeUsoParticular.USO_PONTUAL,
                    LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 15),
                    km, null, aceitar, "Fim de semana");
        }

        @Test
        @DisplayName("RN10_autorizarComAceiteTornaAAutorizacaoValida")
        void autorizaComAceite() {
            UsoParticular uso = servico.autorizarUsoParticular(CONTRATO, dados(600, true));

            assertThat(uso.getKmAutorizado()).isEqualTo(600);
            assertThat(uso.estaValida()).isTrue();
            assertThat(uso.getAceiteEm()).isNotNull();
        }

        @Test
        @DisplayName("RN10_semAceiteAAutorizacaoFicaInvalida")
        void semAceite() {
            UsoParticular uso = servico.autorizarUsoParticular(CONTRATO, dados(600, false));

            assertThat(uso.estaValida()).isFalse();
        }

        @Test
        @DisplayName("RN10_acimaDoTetoEhRecusado")
        void acimaDoTeto() {
            assertThatThrownBy(() -> servico.autorizarUsoParticular(CONTRATO, dados(1500, true)))
                    .isInstanceOf(NegocioException.class)
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-010-USO_PARTICULAR_ACIMA_DO_LIMITE");
        }

        @Test
        @DisplayName("o aceite não é reemitido em uma atualização posterior")
        void aceiteNaoSeRepete() {
            UsoParticular uso = servico.autorizarUsoParticular(CONTRATO, dados(600, true));
            var primeiroAceite = uso.getAceiteEm();
            ReflectionTestUtils.setField(uso, "id", 5L);
            when(usos.buscarPorId(5L)).thenReturn(Optional.of(uso));

            servico.atualizarUsoParticular(5L, dados(700, true));

            // Reescrever o carimbo apagaria quando o condutor de fato aceitou.
            assertThat(uso.getAceiteEm()).isEqualTo(primeiroAceite);
            assertThat(uso.getKmAutorizado()).isEqualTo(700);
        }

        @Test
        @DisplayName("uso particular inexistente resulta em 404")
        void inexistente() {
            when(usos.buscarPorId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.excluirUsoParticular(99L))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> servico.atualizarUsoParticular(99L, dados(600, true)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        @DisplayName("a listagem devolve os usos do contrato")
        void listagem() {
            when(usos.doContrato(CONTRATO)).thenReturn(List.of());

            assertThat(servico.usosDoContrato(CONTRATO)).isEmpty();
        }
    }
}
