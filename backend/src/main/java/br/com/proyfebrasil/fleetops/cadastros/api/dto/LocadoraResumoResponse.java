package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Identificação enxuta de uma locadora, para referências em outros cadastros. */
@Schema(name = "LocadoraResumoResponse", description = "Identificação resumida de uma locadora")
public record LocadoraResumoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome) {
}
