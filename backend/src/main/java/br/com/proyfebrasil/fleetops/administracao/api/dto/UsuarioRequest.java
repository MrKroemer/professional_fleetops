package br.com.proyfebrasil.fleetops.administracao.api.dto;

import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados para criar ou atualizar um usuário.
 *
 * @param nome   nome de exibição
 * @param email  e-mail de acesso; normalizado para caixa baixa e único entre não excluídos
 * @param perfil perfil de acesso (RN-19)
 * @param senha  senha inicial; obrigatória na criação, opcional na atualização
 * @param ativo  indica se o usuário pode autenticar
 */
@Schema(name = "UsuarioRequest", description = "Dados de criação ou atualização de usuário")
public record UsuarioRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o nome")
        @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres")
        String nome,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres")
        String email,

        @NotNull(message = "Informe o perfil de acesso")
        Perfil perfil,

        @Schema(format = "password", description = "Obrigatória na criação; se omitida na atualização, a senha atual é mantida")
        @Size(min = 10, max = 200, message = "A senha deve ter entre 10 e 200 caracteres")
        String senha,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativo) {
}
