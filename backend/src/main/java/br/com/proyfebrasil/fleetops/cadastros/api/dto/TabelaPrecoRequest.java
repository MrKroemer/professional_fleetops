package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dados de uma vigência de tabela de preços (RN-14).
 *
 * <p>A grade é substituída por inteiro a cada envio: grupos e pacotes ausentes na
 * requisição deixam de existir na vigência.
 */
@Schema(name = "TabelaPrecoRequest", description = "Vigência de tabela de preços de uma locadora")
public record TabelaPrecoRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe a locadora")
        Long locadoraId,

        @Schema(example = "2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 2000, message = "Ano de vigência inválido")
        @Max(value = 2100, message = "Ano de vigência inválido")
        int anoVigencia,

        @Size(max = 2000) String observacoes,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe os grupos tarifários")
        List<@Valid GrupoRequest> grupos,

        @Schema(description = "Valores de KM excedente por categoria (RN-06)")
        List<@Valid KmExcedenteRequest> kmExcedente) {

    /** Grupo tarifário e seus pacotes de quilometragem. */
    @Schema(name = "GrupoTarifarioRequest")
    public record GrupoRequest(
            @Schema(example = "AM", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe o código do grupo")
            @Size(max = 20)
            String codigo,

            @Schema(example = "KWID/Mobi", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe os veículos do grupo")
            @Size(max = 300)
            String veiculosDoGrupo,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe a categoria do grupo")
            CategoriaVeiculo categoria,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe os pacotes de KM")
            List<@Valid PacoteRequest> pacotes) {
    }

    /** Pacote de quilometragem e seu valor mensal. */
    @Schema(name = "PacoteKmRequest")
    public record PacoteRequest(
            @Schema(example = "3000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Min(value = 1, message = "O pacote de KM deve ser maior que zero")
            int pacoteKm,

            @Schema(example = "2606.08", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe o valor mensal")
            @DecimalMin(value = "0.00", message = "O valor mensal não pode ser negativo")
            BigDecimal valorMensal) {
    }

    /**
     * Valor do KM excedente.
     *
     * @param pacoteKm pacote específico; omitir aplica o valor a todos os pacotes da categoria
     */
    @Schema(name = "KmExcedenteRequest")
    public record KmExcedenteRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe a categoria")
            CategoriaVeiculo categoria,

            @Min(value = 1, message = "O pacote de KM deve ser maior que zero")
            Integer pacoteKm,

            @Schema(example = "0.60", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe o valor por KM")
            @DecimalMin(value = "0.00", message = "O valor por KM não pode ser negativo")
            BigDecimal valorKm) {
    }
}
