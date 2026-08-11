package br.com.proyfebrasil.fleetops.shared.arquivos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do armazenamento de anexos (Seção 5, item 4).
 *
 * <p>Os limites de tamanho e tipo vivem aqui, e não espalhados pelos serviços, porque são
 * política de plataforma: mudam por decisão de infraestrutura, não por regra de negócio.
 *
 * @param endpoint URL do serviço S3-compatível; em produção com AWS, fica vazio e o SDK
 *     resolve o endereço a partir da região
 * @param regiao região informada ao SDK — o MinIO ignora, mas o protocolo exige
 * @param bucket balde onde os anexos são gravados
 * @param chaveDeAcesso identificador da credencial
 * @param chaveSecreta segredo da credencial; nunca versionado, sempre por variável de ambiente
 * @param tamanhoMaximoBytes teto por arquivo
 * @param tiposPermitidos tipos MIME aceitos
 * @param validadeDoLink por quanto tempo uma URL pré-assinada continua valendo
 */
@Validated
@ConfigurationProperties(prefix = "fleetops.armazenamento")
public record PropriedadesDeArmazenamento(
        String endpoint,
        @NotBlank String regiao,
        @NotBlank String bucket,
        @NotBlank String chaveDeAcesso,
        @NotBlank String chaveSecreta,
        @Positive long tamanhoMaximoBytes,
        List<String> tiposPermitidos,
        Duration validadeDoLink) {

    public PropriedadesDeArmazenamento {
        // Uma lista vazia de tipos permitidos aceitaria qualquer coisa — inclusive um
        // executável renomeado. Sem configuração explícita, vale o conjunto mínimo que o
        // domínio precisa: fotos do book e o CRLV em PDF.
        if (tiposPermitidos == null || tiposPermitidos.isEmpty()) {
            tiposPermitidos = List.of("image/jpeg", "image/png", "image/webp", "application/pdf");
        }
        if (validadeDoLink == null) {
            validadeDoLink = Duration.ofMinutes(10);
        }
    }

    /** Indica se o tipo informado pode ser gravado. */
    public boolean aceitaTipo(String tipoDeConteudo) {
        return tipoDeConteudo != null && tiposPermitidos.contains(tipoDeConteudo.toLowerCase(java.util.Locale.ROOT));
    }
}
