package br.com.proyfebrasil.fleetops.shared.arquivos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.shared.arquivos.ArmazenamentoDeAnexos.ArquivoGravado;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("Recebimento de anexos (Seção 5, item 4)")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoDeAnexosTest {

    @Mock
    private AnexoRepository anexos;

    @Mock
    private ArmazenamentoDeAnexos armazenamento;

    private ServicoDeAnexos servico;

    @BeforeEach
    void preparar() throws IOException {
        servico = new ServicoDeAnexos(anexos, armazenamento);
        when(armazenamento.lerLimitado(any(InputStream.class)))
                .thenAnswer(chamada -> ((InputStream) chamada.getArgument(0)).readAllBytes());
        when(anexos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    private static MultipartFile foto(String nome) {
        return new MockMultipartFile("arquivo", nome, "image/jpeg", "conteúdo".getBytes());
    }

    @Test
    @DisplayName("grava no bucket e registra os metadados, nessa ordem")
    void gravaERegistra() {
        when(armazenamento.gravar(anyString(), anyString(), any()))
                .thenReturn(new ArquivoGravado("2026/02/uuid-frente.jpg", "abc123", 42L));

        Anexo anexo = servico.receber(foto("frente.jpg"));

        assertThat(anexo.getChave()).isEqualTo("2026/02/uuid-frente.jpg");
        assertThat(anexo.getNomeOriginal()).isEqualTo("frente.jpg");
        assertThat(anexo.getSha256()).isEqualTo("abc123");
        assertThat(anexo.ehImagem()).isTrue();
    }

    @Test
    @DisplayName("falha ao registrar os metadados remove o objeto do bucket")
    void limpaOObjetoOrfao() {
        when(armazenamento.gravar(anyString(), anyString(), any()))
                .thenReturn(new ArquivoGravado("2026/02/uuid-frente.jpg", "abc123", 42L));
        when(anexos.save(any())).thenThrow(new IllegalStateException("banco fora"));

        assertThatThrownBy(() -> servico.receber(foto("frente.jpg")))
                .isInstanceOf(IllegalStateException.class);

        // Sem isto, o objeto ficaria no bucket para sempre: invisível ao sistema e
        // cobrado pelo armazenamento.
        verify(armazenamento).remover("2026/02/uuid-frente.jpg");
    }

    @Test
    @DisplayName("arquivo ausente ou vazio é recusado antes de tocar no bucket")
    void recusaArquivoVazio() {
        assertThatThrownBy(() -> servico.receber(null))
                .isInstanceOf(ArquivoRecusadoException.class)
                .hasMessageContaining("Nenhum arquivo");

        var vazio = new MockMultipartFile("arquivo", "vazio.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> servico.receber(vazio))
                .isInstanceOf(ArquivoRecusadoException.class);

        verify(armazenamento, never()).gravar(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("um arquivo sem nome recebe um nome, em vez de quebrar o registro")
    void arquivoSemNome() {
        when(armazenamento.gravar(any(), anyString(), any()))
                .thenReturn(new ArquivoGravado("2026/02/uuid-arquivo", "abc", 10L));
        var semNome = new MockMultipartFile("arquivo", "", "image/jpeg", "x".getBytes());

        assertThat(servico.receber(semNome).getNomeOriginal()).isEqualTo("arquivo");
    }

    @Test
    @DisplayName("falha de leitura vira erro de negócio, e não 500")
    void falhaDeLeitura() throws IOException {
        when(armazenamento.lerLimitado(any(InputStream.class))).thenThrow(new IOException("conexão caiu"));

        assertThatThrownBy(() -> servico.receber(foto("frente.jpg")))
                .isInstanceOf(ArquivoRecusadoException.class)
                .hasMessageContaining("conexão caiu");
    }

    @Test
    @DisplayName("o link de leitura é gerado a partir do anexo gravado")
    void linkDeLeitura() {
        var anexo = new Anexo("chave", "crlv.pdf", "application/pdf", 100, "hash");
        when(anexos.findById(5L)).thenReturn(Optional.of(anexo));
        when(armazenamento.linkDeLeitura(anexo)).thenReturn(URI.create("https://minio/assinado"));

        assertThat(servico.linkDeLeitura(5L)).hasToString("https://minio/assinado");
        assertThat(servico.buscar(5L)).isSameAs(anexo);
        assertThat(anexo.ehImagem()).isFalse();
    }

    @Test
    @DisplayName("anexo inexistente resulta em 404")
    void anexoInexistente() {
        when(anexos.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> servico.linkDeLeitura(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("o Anexo recusa tamanho não positivo na própria construção")
    void anexoExigeTamanho() {
        assertThatThrownBy(() -> new Anexo("c", "n", "image/jpeg", 0, "h"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    @DisplayName("os limites de tipo saem da configuração, com padrão seguro quando ausentes")
    void limitesDeTipo() {
        // Lista vazia aceitaria qualquer coisa, inclusive um executável renomeado.
        var padrao = new PropriedadesDeArmazenamento(
                null, "us-east-1", "b", "k", "s", 1024, null, null);
        assertThat(padrao.aceitaTipo("image/jpeg")).isTrue();
        assertThat(padrao.aceitaTipo("application/pdf")).isTrue();
        assertThat(padrao.aceitaTipo("application/x-msdownload")).isFalse();
        assertThat(padrao.aceitaTipo(null)).isFalse();
        assertThat(padrao.validadeDoLink()).isNotNull();
    }

    @Test
    @DisplayName("o conteúdo lido é o mesmo que sobe — a impressão digital depende disso")
    void conteudoIntegro() {
        var capturado = new byte[][] {null};
        when(armazenamento.gravar(anyString(), anyString(), any())).thenAnswer(chamada -> {
            capturado[0] = chamada.getArgument(2);
            return new ArquivoGravado("c", "h", capturado[0].length);
        });

        servico.receber(new MockMultipartFile("arquivo", "f.jpg", "image/jpeg", "abc".getBytes()));

        assertThat(new ByteArrayInputStream(capturado[0]).readAllBytes()).isEqualTo("abc".getBytes());
    }
}
