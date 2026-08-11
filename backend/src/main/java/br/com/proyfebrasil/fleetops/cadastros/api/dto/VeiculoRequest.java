package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados de criação ou atualização de um veículo. */
@Schema(name = "VeiculoRequest", description = "Dados de um veículo")
public record VeiculoRequest(
        @Schema(example = "ABC1D23", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Formato Mercosul ou antigo; normalizada para caixa alta sem separadores (RN-02)")
        @NotBlank(message = "Informe a placa")
        String placa,

        @Schema(example = "S10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o modelo")
        @Size(max = 120)
        String modelo,

        @Size(max = 120) String fabricante,

        @Min(value = 1980, message = "Ano de fabricação inválido")
        @Max(value = 2100, message = "Ano de fabricação inválido")
        Integer anoFabricacao,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe a categoria")
        CategoriaVeiculo categoria,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe o combustível")
        Combustivel combustivel,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe a locadora")
        Long locadoraId,

        @Schema(example = "AM", description = "Código do grupo tarifário na tabela da locadora")
        @Size(max = 20)
        String grupoTarifario,

        @Size(max = 40) String codigoInterno,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiRastreador,
        @Size(max = 160) String fornecedorRastreador,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiAdesivo,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe a situação do veículo")
        StatusVeiculo status,

        @Size(max = 2000) String observacoes) {
}
