package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emissão e verificação dos JWT assinados com HMAC-SHA256.
 *
 * <p>O {@link JwtDecoder} exposto como bean é o usado pelo <em>resource server</em> e
 * aceita <strong>apenas</strong> tokens de acesso: um refresh token apresentado no
 * cabeçalho {@code Authorization} é rejeitado, pois sua validade é muito maior.
 * A verificação de refresh tokens é feita separadamente em {@link ServicoDeTokens}.
 */
@Configuration
public class ConfiguracaoJwt {

    @Bean
    public SecretKey chaveAssinaturaJwt(FleetOpsProperties propriedades) {
        byte[] material = propriedades.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(material, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey chaveAssinaturaJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveAssinaturaJwt));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey chaveAssinaturaJwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveAssinaturaJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> apenasAcesso = new JwtClaimValidator<String>(
                ServicoDeTokens.CLAIM_TIPO, ServicoDeTokens.TIPO_ACCESS::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), apenasAcesso));
        return decoder;
    }
}
