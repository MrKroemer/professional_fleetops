package br.com.proyfebrasil.fleetops.painel.api;

import br.com.proyfebrasil.fleetops.painel.domain.Severidade;
import br.com.proyfebrasil.fleetops.painel.domain.TipoDePendencia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** DTOs do painel inicial. */
public final class PainelDtos {

    private PainelDtos() {
    }

    /**
     * Uma fatia de distribuição.
     *
     * @param chave identificador estável da categoria; a interface o usa para escolher a
     *     cor da série, que precisa ser a mesma entre consultas — o rótulo traduzido não
     *     serve para isso
     */
    @Schema(name = "FatiaResponse", description = "Uma categoria e sua quantidade")
    public record FatiaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String chave,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rotulo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long quantidade) {
    }

    /** Custo mensal estimado de uma locadora. */
    @Schema(name = "CustoDaLocadoraResponse")
    public record CustoDaLocadoraResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String locadora,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long veiculos,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoMensalEstimado) {
    }

    /**
     * Indicadores do painel.
     *
     * @param custoMensalEstimado estimativa de referência, não fatura: usa o pacote mais
     *     barato do grupo tarifário de cada veículo na vigência do ano corrente
     */
    @Schema(name = "IndicadoresResponse", description = "Indicadores consolidados da frota")
    public record IndicadoresResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long veiculosNaFrota,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long veiculosEmUso,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long veiculosADiesel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long veiculosComRastreador,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long obras,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long obrasAtivas,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long condutores,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long condutoresAtivos,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long fornecedoresAtivos,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int anoDeReferencia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoMensalEstimado,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FatiaResponse> veiculosPorCategoria,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FatiaResponse> veiculosPorLocadora,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FatiaResponse> veiculosPorStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FatiaResponse> obrasPorUf,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FatiaResponse> fornecedoresPorTipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CustoDaLocadoraResponse> custoPorLocadora) {
    }

    /** Um item da central de pendências (RN-23). */
    @Schema(name = "PendenciaResponse", description = "Pendência apurada dos cadastros")
    public record PendenciaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TipoDePendencia tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Regra que origina a pendência")
            String regra,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Severidade severidade,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String severidadeDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titulo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String detalhe,
            @Schema(description = "Rota da interface que resolve a pendência") String recurso,
            Long referencia) {
    }

    /** Central de pendências com os contadores por severidade. */
    @Schema(name = "CentralDePendenciasResponse")
    public record CentralDePendenciasResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long criticas,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long atencao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long informativas,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PendenciaResponse> itens) {
    }
}
