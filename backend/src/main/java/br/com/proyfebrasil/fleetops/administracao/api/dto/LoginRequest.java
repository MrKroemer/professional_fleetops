package br.com.proyfebrasil.fleetops.administracao.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais de acesso.
 *
 * @param email e-mail corporativo do usuário
 * @param senha senha em claro, transmitida apenas sob TLS e nunca registrada em log
 */
@Schema(name = "LoginRequest", description = "Credenciais de acesso ao sistema")
public record LoginRequest(
        @Schema(example = "gestor.frota@proyfebrasil.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres")
        String email,

        @Schema(format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe a senha")
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senha) {
}
