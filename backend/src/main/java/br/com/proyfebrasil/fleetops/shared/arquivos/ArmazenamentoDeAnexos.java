package br.com.proyfebrasil.fleetops.shared.arquivos;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Gravação e leitura de anexos em storage S3-compatível (Seção 5, item 4).
 *
 * <p>O binário nunca é servido pelo backend. A leitura sai por <strong>URL pré-assinada</strong>
 * de vida curta, gerada localmente com a credencial: o navegador busca o arquivo direto do
 * bucket, e o backend deixa de ser o gargalo por onde passariam todas as fotos de todos os
 * books. O link expira sozinho, então um endereço vazado não vira acesso permanente.
 *
 * <p>O caminho da chave é {@code <ano>/<mês>/<uuid>-<nome>}. A pasta por competência não é
 * enfeite: é o que permite aplicar política de ciclo de vida no bucket — expirar ou mover
 * para armazenamento frio o que passou do prazo de guarda — sem precisar consultar o banco.
 */
@Component
public class ArmazenamentoDeAnexos {

    private final PropriedadesDeArmazenamento propriedades;
    private final S3Client cliente;
    private final S3Presigner assinador;

    public ArmazenamentoDeAnexos(PropriedadesDeArmazenamento propriedades) {
        this.propriedades = propriedades;

        var credenciais = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(propriedades.chaveDeAcesso(), propriedades.chaveSecreta()));
        var regiao = Region.of(propriedades.regiao());

        // `pathStyleAccessEnabled` é obrigatório com MinIO: o estilo virtual-host exige um
        // subdomínio por bucket, que não existe quando o serviço responde em um host fixo.
        var configuracao = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        var construtorDoCliente = S3Client.builder()
                .region(regiao)
                .credentialsProvider(credenciais)
                .serviceConfiguration(configuracao);
        var construtorDoAssinador = S3Presigner.builder()
                .region(regiao)
                .credentialsProvider(credenciais)
                .serviceConfiguration(configuracao);

        if (propriedades.endpoint() != null && !propriedades.endpoint().isBlank()) {
            URI endereco = URI.create(propriedades.endpoint());
            construtorDoCliente.endpointOverride(endereco);
            construtorDoAssinador.endpointOverride(endereco);
        }

        this.cliente = construtorDoCliente.build();
        this.assinador = construtorDoAssinador.build();
    }

    /** Resultado da gravação: o que o chamador precisa para montar o {@link Anexo}. */
    public record ArquivoGravado(String chave, String sha256, long tamanhoEmBytes) {
    }

    /**
     * Grava o conteúdo no bucket.
     *
     * <p>O conteúdo é lido inteiro para memória antes de subir, e isso é deliberado: o
     * SHA-256 precisa ser calculado sobre exatamente os mesmos bytes que foram enviados, e
     * o S3 exige saber o tamanho de antemão. O teto configurado mantém a leitura limitada —
     * é o mesmo motivo pelo qual o limite é verificado <em>antes</em> de qualquer byte sair
     * do lugar.
     *
     * @throws ArquivoRecusadoException se o tipo não for aceito ou o tamanho ultrapassar o teto
     */
    public ArquivoGravado gravar(String nomeOriginal, String tipoDeConteudo, byte[] conteudo) {
        if (!propriedades.aceitaTipo(tipoDeConteudo)) {
            throw new ArquivoRecusadoException(
                    "Tipo de arquivo não aceito: %s. Aceitos: %s."
                            .formatted(tipoDeConteudo, String.join(", ", propriedades.tiposPermitidos())));
        }
        if (conteudo == null || conteudo.length == 0) {
            throw new ArquivoRecusadoException("O arquivo enviado está vazio.");
        }
        if (conteudo.length > propriedades.tamanhoMaximoBytes()) {
            throw new ArquivoRecusadoException(
                    "Arquivo de %s; o limite é %s."
                            .formatted(emMegabytes(conteudo.length), emMegabytes(propriedades.tamanhoMaximoBytes())));
        }

        String chave = montarChave(nomeOriginal);
        cliente.putObject(
                PutObjectRequest.builder()
                        .bucket(propriedades.bucket())
                        .key(chave)
                        .contentType(tipoDeConteudo)
                        .contentLength((long) conteudo.length)
                        .build(),
                RequestBody.fromBytes(conteudo));

        return new ArquivoGravado(chave, impressaoDigital(conteudo), conteudo.length);
    }

    /** URL temporária de leitura direta do bucket. */
    public URI linkDeLeitura(Anexo anexo) {
        var requisicao = GetObjectPresignRequest.builder()
                .signatureDuration(propriedades.validadeDoLink())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(propriedades.bucket())
                        .key(anexo.getChave())
                        // Faz o navegador baixar com o nome que o usuário reconhece, em vez
                        // do UUID da chave.
                        .responseContentDisposition("inline; filename=\"%s\"".formatted(anexo.getNomeOriginal()))
                        .build())
                .build();
        return URI.create(assinador.presignGetObject(requisicao).url().toString());
    }

    /** Remove o objeto do bucket. Usado quando a gravação de metadados falha depois do upload. */
    public void remover(String chave) {
        cliente.deleteObject(DeleteObjectRequest.builder().bucket(propriedades.bucket()).key(chave).build());
    }

    /** Lê o conteúdo de um envio, respeitando o teto configurado. */
    public byte[] lerLimitado(InputStream entrada) throws IOException {
        // `readNBytes` com limite + 1 permite distinguir "cabe exatamente no teto" de
        // "estourou": lendo só o teto, um arquivo grande demais passaria truncado.
        byte[] lido = entrada.readNBytes((int) Math.min(propriedades.tamanhoMaximoBytes() + 1, Integer.MAX_VALUE));
        if (lido.length > propriedades.tamanhoMaximoBytes()) {
            throw new ArquivoRecusadoException(
                    "Arquivo maior que o limite de %s.".formatted(emMegabytes(propriedades.tamanhoMaximoBytes())));
        }
        return lido;
    }

    private String montarChave(String nomeOriginal) {
        LocalDate hoje = LocalDate.now();
        String seguro = nomeOriginal == null
                ? "arquivo"
                : nomeOriginal.replaceAll("[^A-Za-z0-9._-]", "_");
        return "%04d/%02d/%s-%s".formatted(hoje.getYear(), hoje.getMonthValue(), UUID.randomUUID(), seguro);
    }

    private static String impressaoDigital(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException erro) {
            // SHA-256 é exigido de toda JVM pela especificação da plataforma; se faltar,
            // o ambiente está quebrado de um jeito que não cabe tratar aqui.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", erro);
        }
    }

    private static String emMegabytes(long bytes) {
        return String.format(Locale.of("pt", "BR"), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
