package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Credencial de portal em claro (RN-20).
 *
 * <p>Devolvida apenas pelo endpoint explícito de revelação, restrito aos perfis com
 * permissão e registrado em log com o solicitante. Nunca aparece em listagens nem no
 * detalhe do cadastro.
 */
@Schema(name = "CredencialReveladaResponse", description = "Credencial de portal em claro")
public record CredencialReveladaResponse(String login, String senha) {
}
