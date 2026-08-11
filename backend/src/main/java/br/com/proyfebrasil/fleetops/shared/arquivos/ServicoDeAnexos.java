package br.com.proyfebrasil.fleetops.shared.arquivos;

import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recebe arquivos e devolve o anexo registrado.
 *
 * <p>A ordem das duas escritas — objeto no bucket, depois linha no banco — é a que produz
 * o erro mais barato. Invertida, uma falha no upload deixaria no banco a referência a um
 * arquivo que não existe, e toda leitura futura quebraria. Nesta ordem, uma falha depois
 * do upload deixa no bucket um objeto sem dono: desperdício de espaço, não corrupção.
 * Ainda assim o objeto é removido no caminho de erro, para que nem esse desperdício fique.
 */
@Service
public class ServicoDeAnexos {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeAnexos.class);

    private final AnexoRepository anexos;
    private final ArmazenamentoDeAnexos armazenamento;

    public ServicoDeAnexos(AnexoRepository anexos, ArmazenamentoDeAnexos armazenamento) {
        this.anexos = anexos;
        this.armazenamento = armazenamento;
    }

    /**
     * Grava o arquivo e registra o anexo.
     *
     * @throws ArquivoRecusadoException se o tipo ou o tamanho não forem aceitos
     */
    @Transactional
    public Anexo receber(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoRecusadoException("Nenhum arquivo foi enviado.");
        }

        byte[] conteudo;
        try (var entrada = arquivo.getInputStream()) {
            conteudo = armazenamento.lerLimitado(entrada);
        } catch (IOException erro) {
            throw new ArquivoRecusadoException("Não foi possível ler o arquivo enviado: " + erro.getMessage());
        }

        var gravado = armazenamento.gravar(arquivo.getOriginalFilename(), arquivo.getContentType(), conteudo);
        try {
            return anexos.save(new Anexo(
                    gravado.chave(),
                    nomeDe(arquivo),
                    arquivo.getContentType(),
                    gravado.tamanhoEmBytes(),
                    gravado.sha256()));
        } catch (RuntimeException erro) {
            // Sem isto, o objeto ficaria órfão no bucket para sempre — invisível ao
            // sistema e cobrado pelo armazenamento.
            LOG.warn("Falha ao registrar o anexo {}; removendo o objeto do bucket.", gravado.chave(), erro);
            armazenamento.remover(gravado.chave());
            throw erro;
        }
    }

    /** URL temporária de leitura. */
    @Transactional(readOnly = true)
    public URI linkDeLeitura(Long anexoId) {
        Anexo anexo = anexos.findById(anexoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo", anexoId));
        return armazenamento.linkDeLeitura(anexo);
    }

    @Transactional(readOnly = true)
    public Anexo buscar(Long anexoId) {
        return anexos.findById(anexoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo", anexoId));
    }

    private static String nomeDe(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();
        return nome == null || nome.isBlank() ? "arquivo" : nome;
    }
}
