package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Vigência de tabela de preços de uma locadora (RN-14). */
@Schema(name = "TabelaPrecoResponse", description = "Vigência de tabela de preços")
public record TabelaPrecoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocadoraResumoResponse locadora,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int anoVigencia,
        String observacoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GrupoResponse> grupos,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KmExcedenteResponse> kmExcedente,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {

    /** Grupo tarifário e seus pacotes. */
    @Schema(name = "GrupoTarifarioResponse")
    public record GrupoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String veiculosDoGrupo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategoriaVeiculo categoria,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoriaDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PacoteResponse> pacotes) {
    }

    /** Pacote de quilometragem e seu valor mensal. */
    @Schema(name = "PacoteKmResponse")
    public record PacoteResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pacoteKm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorMensal) {
    }

    /** Valor do KM excedente. {@code pacoteKm} nulo indica que vale para todos os pacotes. */
    @Schema(name = "KmExcedenteResponse")
    public record KmExcedenteResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategoriaVeiculo categoria,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoriaDescricao,
            Integer pacoteKm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorKm) {
    }
}
