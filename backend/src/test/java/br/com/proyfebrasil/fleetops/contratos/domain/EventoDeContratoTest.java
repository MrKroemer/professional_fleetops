package br.com.proyfebrasil.fleetops.contratos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.shared.arquivos.Anexo;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RN-12 — retirada e devolução exigem book completo e CRLV")
class EventoDeContratoTest {

    private static final LocalDate RETIRADA = LocalDate.of(2026, 2, 10);
    private static final Instant AGORA = Instant.parse("2026-02-10T14:00:00Z");

    private Locadora locadora;
    private Veiculo veiculo;
    private ContratoDeLocacao contrato;
    private EventoDeContrato evento;

    @BeforeEach
    void preparar() {
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        veiculo = new Veiculo("ABC1D23", "Polo", CategoriaVeiculo.PASSEIO, Combustivel.FLEX, locadora);
        contrato = new ContratoDeLocacao(new Obra("24.019", "SKER", "Uibaí", "BA"), locadora, RETIRADA);
        evento = new EventoDeContrato(contrato, TipoDeEvento.RETIRADA, veiculo, RETIRADA);
    }

    private static Anexo foto(String nome) {
        return new Anexo("2026/02/" + nome, nome, "image/jpeg", 1024, "abc");
    }

    private static Anexo pdf(String nome) {
        return new Anexo("2026/02/" + nome, nome, "application/pdf", 2048, "def");
    }

    private void completarBook() {
        for (ItemDoBook item : ItemDoBook.obrigatorios()) {
            evento.adicionarFoto(item, foto(item.name().toLowerCase(java.util.Locale.ROOT) + ".jpg"), null);
        }
    }

    @Nested
    @DisplayName("book fotográfico")
    class Book {

        @Test
        @DisplayName("RN12_oBookExigeOitoAngulos_eAvariasNaoEntraNaConta")
        void avariasNaoEObrigatorio() {
            // Exigir AVARIAS impediria concluir a retirada de um carro sem avaria — o caso
            // comum e o desejável.
            assertThat(ItemDoBook.obrigatorios()).hasSize(8).doesNotContain(ItemDoBook.AVARIAS);
            assertThat(ItemDoBook.AVARIAS.isObrigatorio()).isFalse();
            assertThat(ItemDoBook.AVARIAS.aceitaVarias()).isTrue();
        }

        @Test
        @DisplayName("RN12_oQueFaltaEDevolvidoItemAItem_naoComoUmErroGenerico")
        void listaOsFaltantes() {
            evento.adicionarFoto(ItemDoBook.FRENTE, foto("frente.jpg"), null);
            evento.adicionarFoto(ItemDoBook.TRASEIRA, foto("traseira.jpg"), null);

            // Quem está no pátio precisa saber que falta o porta-malas, não que "algo falta".
            assertThat(evento.itensFaltantesDoBook())
                    .containsExactly(
                            ItemDoBook.LATERAL_DIREITA, ItemDoBook.LATERAL_ESQUERDA, ItemDoBook.PNEUS,
                            ItemDoBook.MOTOR, ItemDoBook.PORTA_MALAS, ItemDoBook.HODOMETRO);
        }

        @Test
        @DisplayName("reenviar um ângulo substitui a foto anterior em vez de acumular")
        void reenvioSubstitui() {
            evento.adicionarFoto(ItemDoBook.FRENTE, foto("tremida.jpg"), null);
            evento.adicionarFoto(ItemDoBook.FRENTE, foto("nitida.jpg"), null);

            // Guardar as duas deixaria o book ambíguo sobre qual delas vale.
            assertThat(evento.getFotos()).hasSize(1);
            assertThat(evento.getFotos().get(0).getAnexo().getNomeOriginal()).isEqualTo("nitida.jpg");
        }

        @Test
        @DisplayName("avarias aceitam várias fotos — uma por avaria")
        void avariasAcumulam() {
            evento.adicionarFoto(ItemDoBook.AVARIAS, foto("risco-porta.jpg"), "Risco na porta");
            evento.adicionarFoto(ItemDoBook.AVARIAS, foto("para-choque.jpg"), "Para-choque amassado");

            assertThat(evento.getFotos()).hasSize(2);
        }

        @Test
        @DisplayName("o book aceita apenas imagens")
        void bookSoAceitaImagem() {
            assertThatThrownBy(() -> evento.adicionarFoto(ItemDoBook.FRENTE, pdf("frente.pdf"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apenas imagens");
        }
    }

    @Nested
    @DisplayName("conclusão")
    class Conclusao {

        @Test
        @DisplayName("RN12_naoConcluiComBookIncompleto_eDizQualFotoFalta")
        void recusaBookIncompleto() {
            evento.anexarCrlv(pdf("crlv.pdf"));
            evento.adicionarFoto(ItemDoBook.FRENTE, foto("frente.jpg"), null);

            assertThatThrownBy(() -> evento.concluir(AGORA))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("Traseira")
                    .hasMessageContaining("Hodômetro")
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-012-BOOK_INCOMPLETO");
        }

        @Test
        @DisplayName("RN12_naoConcluiSemCrlv_mesmoComOBookCompleto")
        void recusaSemCrlv() {
            completarBook();

            assertThatThrownBy(() -> evento.concluir(AGORA))
                    .isInstanceOf(NegocioException.class)
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-012-CRLV_AUSENTE");
        }

        @Test
        @DisplayName("RN12_comBookCompletoECrlv_oEventoConclui")
        void conclui() {
            completarBook();
            evento.anexarCrlv(pdf("crlv.pdf"));
            evento.registrarAceiteDasRegras(AGORA);

            assertThat(evento.estaCompleto()).isTrue();
            evento.concluir(AGORA);

            assertThat(evento.estaConcluido()).isTrue();
            assertThat(evento.getConcluidoEm()).isEqualTo(AGORA);
            assertThat(evento.getRegrasAceitasEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("um evento concluído não aceita alteração silenciosa")
        void concluidoNaoAceitaAlteracao() {
            completarBook();
            evento.anexarCrlv(pdf("crlv.pdf"));
            evento.concluir(AGORA);

            // A RN-12 proíbe conclusão parcial; permitir edição depois seria a mesma
            // brecha pela porta dos fundos — o evento concluído deixaria de descrever o
            // que foi conferido.
            assertThatThrownBy(() -> evento.adicionarFoto(ItemDoBook.FRENTE, foto("outra.jpg"), null))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("Reabra");
            assertThatThrownBy(() -> evento.concluir(AGORA))
                    .isInstanceOf(NegocioException.class)
                    .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                    .isEqualTo("RN-012-EVENTO_JA_CONCLUIDO");
        }

        @Test
        @DisplayName("reabrir devolve o evento à edição sem perder o que já foi enviado")
        void reabre() {
            completarBook();
            evento.anexarCrlv(pdf("crlv.pdf"));
            evento.concluir(AGORA);

            evento.reabrir();

            assertThat(evento.estaConcluido()).isFalse();
            assertThat(evento.getConcluidoEm()).isNull();
            assertThat(evento.getFotos()).hasSize(8);
            assertThat(evento.getCrlv()).isPresent();
        }
    }
}
