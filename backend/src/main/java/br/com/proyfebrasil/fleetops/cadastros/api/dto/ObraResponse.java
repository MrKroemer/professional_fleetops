package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

/** Obra cadastrada. */
@Schema(name = "ObraResponse", description = "Obra cadastrada")
public record ObraResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
        String cliente,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cidade,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uf,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StatusObra status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusDescricao,
        LocalDate dataInicio,
        LocalDate dataFim,
        String observacoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {
}
