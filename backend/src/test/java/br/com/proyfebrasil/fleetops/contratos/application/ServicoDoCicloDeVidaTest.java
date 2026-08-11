package br.com.proyfebrasil.fleetops.contratos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.conformidade.application.ServicoDeFumacaPreta;
import br.com.proyfebrasil.fleetops.conformidade.application.ServicoDeFumacaPreta.SituacaoDaFumaca;
import br.com.proyfebrasil.fleetops.conformidade.domain.PadraoRingelmann;
import br.com.proyfebrasil.fleetops.conformidade.domain.TesteDeFumacaPreta;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.EventoDeContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.ItemDoBook;
import br.com.proyfebrasil.fleetops.contratos.domain.SituacaoDoEvento;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.TipoDeEvento;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.contratos.infra.EventoDeContratoRepository;
import br.com.proyfebrasil.fleetops.shared.arquivos.Anexo;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@DisplayName("Ciclo de vida do contrato — RN-09 e RN-17")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoDoCicloDeVidaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);
    private static final Long CONTRATO_ID = 7L;

    @Mock
    private ContratoRepository contratos;

    @Mock
    private EventoDeContratoRepository eventos;

    @Mock
    private ServicoDeFumacaPreta fumacaPreta;

    @Mock
    private br.com.proyfebrasil.fleetops.operacao.infra.FechamentoRepository fechamentos;

    private ServicoDoCicloDeVida servico;
    private ContratoDeLocacao contrato;
    private Locadora locadora;
    private Clock relogio;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDoCicloDeVida(contratos, eventos, fumacaPreta, fechamentos, relogio);
        this.relogio = relogio;

        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        contrato = new ContratoDeLocacao(
                new Obra("24.019", "SKER", "Uibaí", "BA"), locadora, LocalDate.of(2025, 1, 10));
        ReflectionTestUtils.setField(contrato, "id", CONTRATO_ID);
        when(contratos.buscarPorId(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(eventos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        // Por padrão a competência final está conferida: os testes desta classe tratam
        // das outras condições da RN-17, e a do fechamento tem teste próprio.
        var conferido = new br.com.proyfebrasil.fleetops.operacao.domain.FechamentoMensal(
                contrato, java.time.YearMonth.from(HOJE));
        conferido.conferir("gestor", relogio.instant(), null);
        when(fechamentos.daCompetencia(any(), any())).thenReturn(Optional.of(conferido));
    }

    private Veiculo veiculo(String placa, Combustivel combustivel) {
        Veiculo veiculo = new Veiculo(placa, "S10", CategoriaVeiculo.QUATRO_X_QUATRO, combustivel, locadora);
        ReflectionTestUtils.setField(veiculo, "id", 42L);
        return veiculo;
    }

    /** Evento pronto para concluir: book completo e CRLV anexado. */
    private EventoDeContrato eventoCompleto(TipoDeEvento tipo, Veiculo veiculo) {
        var evento = new EventoDeContrato(contrato, tipo, veiculo, HOJE);
        for (ItemDoBook item : ItemDoBook.obrigatorios()) {
            evento.adicionarFoto(item, new Anexo("k/" + item, item + ".jpg", "image/jpeg", 10, "h"), null);
        }
        evento.anexarCrlv(new Anexo("k/crlv", "crlv.pdf", "application/pdf", 20, "h2"));
        ReflectionTestUtils.setField(evento, "id", 99L);
        when(eventos.buscarPorId(99L)).thenReturn(Optional.of(evento));
        return evento;
    }

    @Nested
    @DisplayName("RN-09 — fumaça preta na retirada")
    class FumacaPreta {

        @Test
        @DisplayName("RN09_veiculoAGasolinaNaoPrecisaDeTeste")
        void gasolinaNaoExigeTeste() {
            Veiculo polo = veiculo("ABC1D23", Combustivel.FLEX);
            var evento = eventoCompleto(TipoDeEvento.RETIRADA, polo);
            when(fumacaPreta.situacaoDe(polo)).thenReturn(new SituacaoDaFumaca(false, Optional.empty()));

            servico.concluirEvento(99L);

            assertThat(evento.estaConcluido()).isTrue();
        }

        @Test
        @DisplayName("RN09_dieselSemTesteBloqueiaAConclusaoDaRetirada")
        void dieselSemTeste() {
            Veiculo s10 = veiculo("XYZ9K87", Combustivel.DIESEL);
            eventoCompleto(TipoDeEvento.RETIRADA, s10);
            when(fumacaPreta.situacaoDe(s10)).thenReturn(new SituacaoDaFumaca(true, Optional.empty()));

            assertThatThrownBy(() -> servico.concluirEvento(99L))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("ainda não tem teste")
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-009-FUMACA_PRETA_PENDENTE");
        }

        @Test
        @DisplayName("RN09_dieselReprovadoBloqueia_eOErroPedeOutroVeiculo")
        void dieselReprovado() {
            Veiculo s10 = veiculo("XYZ9K87", Combustivel.DIESEL);
            eventoCompleto(TipoDeEvento.RETIRADA, s10);
            var teste = new TesteDeFumacaPreta(s10, HOJE, PadraoRingelmann.PADRAO_4, 100);
            ReflectionTestUtils.setField(teste, "id", 5L);
            when(fumacaPreta.situacaoDe(s10)).thenReturn(new SituacaoDaFumaca(true, Optional.of(teste)));

            // Pendente e reprovado levam a ações diferentes — fazer o teste versus trocar
            // o carro —, então precisam de códigos diferentes.
            assertThatThrownBy(() -> servico.concluirEvento(99L))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("só pode ser concluída com outro veículo")
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-009-FUMACA_PRETA_REPROVADA");
        }

        @Test
        @DisplayName("RN09_aRegraNaoSeAplicaAdevolucao")
        void devolucaoNaoExigeTeste() {
            Veiculo s10 = veiculo("XYZ9K87", Combustivel.DIESEL);
            var evento = eventoCompleto(TipoDeEvento.DEVOLUCAO, s10);
            when(fumacaPreta.situacaoDe(s10)).thenReturn(new SituacaoDaFumaca(true, Optional.empty()));

            // A RN-09 fala da retirada. Exigir o teste na devolução impediria devolver
            // justamente o carro que reprovou — o oposto do que a regra quer.
            assertThatCode(() -> servico.concluirEvento(99L)).doesNotThrowAnyException();
            assertThat(evento.estaConcluido()).isTrue();
        }

        @Test
        @DisplayName("RN09_precedeARN12_porqueOveiculoReprovadoInvalidaOBookInteiro")
        void fumacaPretaVemAntes() {
            Veiculo s10 = veiculo("XYZ9K87", Combustivel.DIESEL);
            var evento = new EventoDeContrato(contrato, TipoDeEvento.RETIRADA, s10, HOJE);
            ReflectionTestUtils.setField(evento, "id", 98L);
            when(eventos.buscarPorId(98L)).thenReturn(Optional.of(evento));
            when(fumacaPreta.situacaoDe(s10)).thenReturn(new SituacaoDaFumaca(true, Optional.empty()));

            // Com os dois problemas presentes, o veículo a diesel é a notícia mais
            // acionável: adianta pouco pedir oito fotos de um carro que vai ser trocado.
            assertThatThrownBy(() -> servico.concluirEvento(98L))
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-009-FUMACA_PRETA_PENDENTE");
        }
    }

    @Nested
    @DisplayName("RN-17 — desmobilização e devolução")
    class Encerramento {

        @Test
        @DisplayName("RN17_devolucaoSemEventoConcluidoEBloqueada")
        void devolucaoSemEvento() {
            when(eventos.ultimoDoTipo(CONTRATO_ID, TipoDeEvento.DEVOLUCAO, SituacaoDoEvento.CONCLUIDO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.encerrar(CONTRATO_ID, StatusContrato.DEVOLVIDO, HOJE))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("evento de devolução")
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-017-DEVOLUCAO_SEM_EVENTO");

            assertThat(contrato.estaAtivo()).isTrue();
        }

        @Test
        @DisplayName("RN17_desmobilizarNaoExigeEvento_porqueEDecisaoDaObra")
        void desmobilizacaoNaoExigeEvento() {
            when(eventos.ultimoDoTipo(any(), any(), any())).thenReturn(Optional.empty());

            servico.encerrar(CONTRATO_ID, StatusContrato.DESMOBILIZADO, HOJE);

            // DESMOBILIZADO é "devolvido pela obra": o carro pode seguir em tratativa com
            // a locadora, e é exatamente o estado que a RN-17 oferece quando há pendência.
            assertThat(contrato.getStatus()).isEqualTo(StatusContrato.DESMOBILIZADO);
            assertThat(contrato.getDataEncerramento()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("RN17_comEventoConcluidoAdevolucaoPassa")
        void devolucaoLiberada() {
            var evento = eventoCompleto(TipoDeEvento.DEVOLUCAO, veiculo("ABC1D23", Combustivel.FLEX));
            evento.concluir(Instant.now());
            when(eventos.ultimoDoTipo(CONTRATO_ID, TipoDeEvento.DEVOLUCAO, SituacaoDoEvento.CONCLUIDO))
                    .thenReturn(Optional.of(evento));

            servico.encerrar(CONTRATO_ID, StatusContrato.DEVOLVIDO, HOJE);

            assertThat(contrato.getStatus()).isEqualTo(StatusContrato.DEVOLVIDO);
        }

        @Test
        @DisplayName("a verificação lista as pendências, em vez de devolver só sim ou não")
        void verificacaoDetalhada() {
            when(eventos.ultimoDoTipo(any(), any(), any())).thenReturn(Optional.empty());

            var verificacao = servico.verificarDevolucao(CONTRATO_ID);

            assertThat(verificacao.liberaDevolucao()).isFalse();
            assertThat(verificacao.eventoDeDevolucaoConcluido()).isFalse();
            assertThat(verificacao.pendencias()).hasSize(1);
            // Fechamento e avarias são Fase 3 e 4: hoje não bloqueiam, e a tela precisa
            // saber disso para não prometer uma verificação que ainda não existe.
            assertThat(verificacao.fechamentoDoPeriodoFinal()).isTrue();
            // Avarias continuam sendo Fase 4: a tela precisa saber que essa verificação
            // ainda não existe, em vez de a regra parecer completa.
            assertThat(verificacao.avariasAbertas()).isZero();
        }

        @Test
        @DisplayName("RN17_semConferenciaDaCompetenciaFinal_adevolucaoEBloqueada")
        void semFechamentoConferido() {
            var evento = eventoCompleto(TipoDeEvento.DEVOLUCAO, veiculo("ABC1D23", Combustivel.FLEX));
            evento.concluir(Instant.now());
            when(eventos.ultimoDoTipo(CONTRATO_ID, TipoDeEvento.DEVOLUCAO, SituacaoDoEvento.CONCLUIDO))
                    .thenReturn(Optional.of(evento));
            // Sem registro de conferência: o fechamento é calculado sempre, então
            // "existir" não diz nada — o que a RN-17 quer é que alguém tenha olhado.
            when(fechamentos.daCompetencia(any(), any())).thenReturn(Optional.empty());

            var verificacao = servico.verificarDevolucao(CONTRATO_ID);

            assertThat(verificacao.fechamentoDoPeriodoFinal()).isFalse();
            assertThat(verificacao.liberaDevolucao()).isFalse();
            assertThat(verificacao.pendencias()).anyMatch(p -> p.contains("não foi conferido"));
        }

        @Test
        @DisplayName("um contrato já encerrado não é encerrado de novo")
        void contratoJaEncerrado() {
            contrato.encerrar(StatusContrato.DEVOLVIDO, LocalDate.of(2025, 12, 1));

            assertThatThrownBy(() -> servico.encerrar(CONTRATO_ID, StatusContrato.INATIVO, HOJE))
                    .isInstanceOf(NegocioException.class)
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-001-CONTRATO_ENCERRADO");
        }
    }
}
