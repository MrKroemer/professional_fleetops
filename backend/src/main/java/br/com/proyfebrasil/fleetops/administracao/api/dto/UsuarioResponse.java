package br.com.proyfebrasil.fleetops.administracao.api.dto;

import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Usuário do sistema. Nunca inclui o hash da senha.
 *
 * @param id              identificador
 * @param nome            nome de exibição
 * @param email           e-mail de acesso
 * @param perfil          perfil de acesso (RN-19)
 * @param perfilDescricao rótulo do perfil em pt-BR, para exibição direta
 * @param ativo           indica se o usuário pode autenticar
 * @param ultimoAcessoEm  instante do último login bem-sucedido
 * @param criadoEm        instante de criação
 */
@Schema(name = "UsuarioResponse", description = "Usuário do sistema")
public record UsuarioResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Perfil perfil,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String perfilDescricao,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativo,
        @Schema(description = "Nulo enquanto o usuário nunca tiver autenticado") Instant ultimoAcessoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm) {
}
