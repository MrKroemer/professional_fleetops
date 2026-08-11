package br.com.proyfebrasil.fleetops.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da aplicação, sempre proveniente de variáveis de ambiente.
 *
 * <p>Nenhum valor sensível tem padrão embutido: a ausência de {@code FLEETOPS_JWT_SECRET}
 * ou {@code FLEETOPS_CRYPTO_KEY} impede a subida da aplicação, por desenho.
 *
 * @param jwt      parâmetros de emissão e validação dos tokens
 * @param cripto   chave simétrica usada para cifrar credenciais de portais (RN-20)
 * @param cors     origens autorizadas a consumir a API a partir do browser
 * @param alertas  parâmetros configuráveis da central de pendências (RN-23)
 */
@Validated
@ConfigurationProperties(prefix = "fleetops")
public record FleetOpsProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid Cripto cripto,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Alertas alertas) {

    /**
     * @param secret     segredo HMAC-SHA256; mínimo de 32 caracteres
     * @param accessTtl  validade do access token, mantido apenas em memória no cliente
     * @param refreshTtl validade do refresh token, transportado em cookie {@code httpOnly}
     * @param cookieSecure marca o cookie de refresh como {@code Secure}; obrigatório sob HTTPS
     */
    public record Jwt(
            @NotBlank @Size(min = 32, message = "O segredo JWT deve ter no mínimo 32 caracteres") String secret,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl,
            boolean cookieSecure) {
    }

    /**
     * @param chave chave AES-GCM de 256 bits codificada em Base64
     */
    public record Cripto(@NotBlank String chave) {
    }

    /**
     * @param origens lista de origens permitidas; vazia quando o frontend é servido pelo mesmo host
     */
    public record Cors(@NotNull List<String> origens) {
    }

    /**
     * @param multaAntecedenciaDias antecedência do alerta de prazo de indicação de condutor (RN-08)
     */
    public record Alertas(@Positive int multaAntecedenciaDias) {
    }
}
