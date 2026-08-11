package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação ou atualização de uma locadora.
 *
 * <p>Sobre as credenciais (RN-20): omitir {@code portalLogin} ou {@code portalSenha}
 * preserva o valor já cadastrado — é o que permite editar o restante do cadastro sem
 * redigitar a senha. Enviar string vazia remove a credencial.
 */
@Schema(name = "LocadoraRequest", description = "Dados de uma locadora")
public record LocadoraRequest(
        @Schema(example = "Unidas", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o nome da locadora")
        @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres")
        String nome,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe o tipo da locadora")
        TipoLocadora tipo,

        @Size(max = 160) String consultor,
        @Size(max = 60) String telefone,

        @Email(message = "Informe um e-mail válido")
        @Size(max = 180)
        String email,

        @Size(max = 400) String portalUrl,

        @Schema(description = "Login do portal em claro. Omitir preserva o atual; vazio remove.")
        @Size(max = 200)
        String portalLogin,

        @Schema(format = "password", description = "Senha do portal em claro. Omitir preserva a atual; vazio remove.")
        @Size(max = 200)
        String portalSenha,

        @Valid CanaisDeAtendimentoDto canais,

        @Size(max = 2000) String observacoes,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativa) {
}
