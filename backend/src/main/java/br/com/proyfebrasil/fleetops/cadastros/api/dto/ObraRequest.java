package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Dados de criação ou atualização de uma obra. */
@Schema(name = "ObraRequest", description = "Dados de uma obra")
public record ObraRequest(
        @Schema(example = "24.019", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o código da obra")
        @Size(max = 20, message = "O código deve ter no máximo 20 caracteres")
        String codigo,

        @Schema(example = "SKER Ventos de Santa Eugênia", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o nome da obra")
        @Size(max = 180, message = "O nome deve ter no máximo 180 caracteres")
        String nome,

        @Size(max = 180, message = "O cliente deve ter no máximo 180 caracteres")
        String cliente,

        @Schema(example = "Uibaí", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe a cidade")
        @Size(max = 120, message = "A cidade deve ter no máximo 120 caracteres")
        String cidade,

        @Schema(example = "BA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe a UF")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "A UF deve ter exatamente duas letras")
        String uf,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe a situação da obra")
        StatusObra status,

        LocalDate dataInicio,
        LocalDate dataFim,

        @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres")
        String observacoes) {
}
