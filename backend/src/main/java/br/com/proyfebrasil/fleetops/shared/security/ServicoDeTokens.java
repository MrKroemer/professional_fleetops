package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Emissão e verificação dos tokens de sessão.
 *
 * <p>Dois tipos de token, com papéis distintos:
 * <ul>
 *   <li><strong>access</strong> — curta duração, enviado no cabeçalho {@code Authorization}
 *       e mantido apenas em memória no browser;</li>
 *   <li><strong>refresh</strong> — longa duração, transportado apenas em cookie
 *       {@code httpOnly} e sempre correlacionado a um registro em banco pelo {@code jti},
 *       o que permite revogação imediata no logout.</li>
 * </ul>
 *
 * <p>O decodificador usado aqui verifica assinatura e expiração, mas não o tipo do token —
 * o tipo é conferido explicitamente em {@link #validarRefreshToken(String)}, de modo que
 * um access token nunca sirva como refresh e vice-versa.
 */
@Service
public class ServicoDeTokens {

    /** Claim que distingue o tipo do token. */
    public static final String CLAIM_TIPO = "typ";
    /** Claim com o perfil de acesso do usuário. */
    public static final String CLAIM_PERFIL = "perfil";
    /** Claim com o e-mail, usado como nome do principal. */
    public static final String CLAIM_EMAIL = "email";
    /** Claim com o nome de exibição. */
    public static final String CLAIM_NOME = "nome";

    public static final String TIPO_ACCESS = "access";
    public static final String TIPO_REFRESH = "refresh";

    private static final String EMISSOR = "fleetops";

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoderSemFiltroDeTipo;
    private final Duration ttlAccess;
    private final Duration ttlRefresh;

    public ServicoDeTokens(JwtEncoder encoder, SecretKey chaveAssinaturaJwt, FleetOpsProperties propriedades) {
        this.encoder = encoder;
        this.decoderSemFiltroDeTipo = NimbusJwtDecoder.withSecretKey(chaveAssinaturaJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.ttlAccess = propriedades.jwt().accessTtl();
        this.ttlRefresh = propriedades.jwt().refreshTtl();
    }

    /**
     * Token emitido, com o instante de expiração para que o cliente possa renovar
     * proativamente em vez de esperar o primeiro 401.
     *
     * @param valor    token codificado
     * @param expiraEm instante de expiração
     */
    public record TokenEmitido(String valor, Instant expiraEm) {
    }

    /**
     * Refresh token emitido junto de seu identificador único.
     *
     * @param valor    token codificado
     * @param jti      identificador persistido para permitir revogação
     * @param expiraEm instante de expiração
     */
    public record RefreshEmitido(String valor, UUID jti, Instant expiraEm) {
    }

    /** Emite um access token de curta duração. */
    public TokenEmitido emitirAccessToken(DadosDoToken dados) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(ttlAccess);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .subject(String.valueOf(dados.usuarioId()))
                .claim(CLAIM_TIPO, TIPO_ACCESS)
                .claim(CLAIM_EMAIL, dados.email())
                .claim(CLAIM_NOME, dados.nome())
                .claim(CLAIM_PERFIL, dados.perfil().name())
                .build();
        return new TokenEmitido(codificar(claims), expiraEm);
    }

    /** Emite um refresh token e o {@code jti} que deve ser persistido para permitir revogação. */
    public RefreshEmitido emitirRefreshToken(DadosDoToken dados) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(ttlRefresh);
        UUID jti = UUID.randomUUID();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .subject(String.valueOf(dados.usuarioId()))
                .id(jti.toString())
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .build();
        return new RefreshEmitido(codificar(claims), jti, expiraEm);
    }

    /**
     * Verifica assinatura, expiração e tipo de um refresh token.
     *
     * @throws NegocioException com {@link ErroComum#SESSAO_INVALIDA} quando o token é
     *     inválido, expirado ou não é do tipo refresh
     */
    public RefreshValidado validarRefreshToken(String token) {
        Jwt jwt;
        try {
            jwt = decoderSemFiltroDeTipo.decode(token);
        } catch (JwtException ex) {
            throw new NegocioException(ErroComum.SESSAO_INVALIDA, "Sessão expirada. Faça login novamente.");
        }
        if (!TIPO_REFRESH.equals(jwt.getClaimAsString(CLAIM_TIPO)) || jwt.getId() == null) {
            throw new NegocioException(ErroComum.SESSAO_INVALIDA, "Sessão inválida. Faça login novamente.");
        }
        try {
            return new RefreshValidado(Long.parseLong(jwt.getSubject()), UUID.fromString(jwt.getId()));
        } catch (IllegalArgumentException ex) {
            throw new NegocioException(ErroComum.SESSAO_INVALIDA, "Sessão inválida. Faça login novamente.");
        }
    }

    /**
     * Conteúdo útil de um refresh token verificado.
     *
     * @param usuarioId identificador do dono da sessão
     * @param jti       identificador da sessão, conferido contra o banco
     */
    public record RefreshValidado(Long usuarioId, UUID jti) {
    }

    /** Validade configurada do refresh token, usada para dimensionar o cookie. */
    public Duration ttlRefresh() {
        return ttlRefresh;
    }

    private String codificar(JwtClaimsSet claims) {
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }
}
