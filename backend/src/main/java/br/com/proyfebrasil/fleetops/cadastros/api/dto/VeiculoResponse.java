package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Veículo cadastrado.
 *
 * @param placa                    placa normalizada, sem separadores
 * @param placaFormatada           placa para exibição, ex.: ABC-1D23
 * @param exigeTesteFumacaPreta    veículo a diesel; teste obrigatório na retirada (RN-09)
 */
@Schema(name = "VeiculoResponse", description = "Veículo cadastrado")
public record VeiculoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placa,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placaFormatada,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelo,
        String fabricante,
        Integer anoFabricacao,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategoriaVeiculo categoria,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoriaDescricao,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Combustivel combustivel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String combustivelDescricao,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocadoraResumoResponse locadora,
        String grupoTarifario,
        String codigoInterno,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiRastreador,
        String fornecedorRastreador,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiAdesivo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exigeTesteFumacaPreta,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StatusVeiculo status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusDescricao,
        String observacoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {
}
