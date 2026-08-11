package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identificação enxuta de uma obra, para referências dentro de outros cadastros.
 * Evita arrastar o registro inteiro sempre que só o rótulo é necessário.
 */
@Schema(name = "ObraResumoResponse", description = "Identificação resumida de uma obra")
public record ObraResumoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome) {
}
