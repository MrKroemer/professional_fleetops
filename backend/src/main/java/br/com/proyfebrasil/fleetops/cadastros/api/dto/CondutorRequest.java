package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Dados de criação ou atualização de um condutor. */
@Schema(name = "CondutorRequest", description = "Dados de um condutor")
public record CondutorRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o nome do condutor")
        @Size(max = 180, message = "O nome deve ter no máximo 180 caracteres")
        String nome,

        @Schema(example = "12345678909", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Com ou sem formatação; é normalizado para apenas dígitos")
        @NotBlank(message = "Informe o CPF")
        String cpf,

        @Size(max = 120) String cargo,
        @Size(max = 60) String telefone,

        @Email(message = "Informe um e-mail válido")
        @Size(max = 180)
        String email,

        @Size(max = 20) String cnhNumero,

        @Schema(example = "AB")
        @Pattern(regexp = "^$|^[A-Za-z]{1,4}$", message = "Categoria de CNH inválida")
        String cnhCategoria,

        @Schema(description = "Base dos alertas de 60 e 30 dias e do bloqueio de vínculo (RN-16)")
        LocalDate cnhValidade,

        Long obraAtualId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe a situação do condutor")
        StatusCondutor status,

        @Size(max = 2000) String observacoes) {
}
