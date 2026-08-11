package br.com.proyfebrasil.fleetops.administracao.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Sessão aberta. O refresh token <strong>não</strong> aparece aqui: ele trafega
 * exclusivamente no cookie {@code httpOnly} definido na mesma resposta.
 *
 * @param accessToken token a ser enviado em {@code Authorization: Bearer}
 * @param expiraEm    expiração do access token, para renovação proativa pelo cliente
 * @param usuario     dados do usuário autenticado
 */
@Schema(name = "SessaoResponse", description = "Sessão autenticada")
public record SessaoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiraEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UsuarioResponse usuario) {
}
