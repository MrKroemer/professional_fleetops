package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.DiaDaSemana;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Fornecedor credenciado.
 *
 * <p>Apenas o bloco correspondente ao tipo vem preenchido; os demais chegam nulos.
 */
@Schema(name = "FornecedorResponse", description = "Fornecedor credenciado")
public record FornecedorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TipoFornecedor tipo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
        String cidade,
        String uf,
        String endereco,
        String telefone,
        String email,
        String responsavel,
        String funcionamento,
        String formaFaturamento,
        String formaPagamento,
        LocalDate credenciadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativo,
        String observacoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ObraResumoResponse> obras,
        DadosDePostoResponse posto,
        DadosDeLavaJatoResponse lavaJato,
        DadosDeRastreadorResponse rastreador,
        DadosDeGraficaResponse grafica,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {

    /** Dados de posto. */
    @Schema(name = "DadosDePostoResponse")
    public record DadosDePostoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<DiaDaSemana> diasAutorizados,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Verdadeiro quando não há restrição de dia")
            boolean semRestricaoDeDia,
            String acessoFaturas) {
    }

    /** Dados de lava-jato. */
    @Schema(name = "DadosDeLavaJatoResponse")
    public record DadosDeLavaJatoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int servicosPorSemana,
            BigDecimal precoPasseio,
            BigDecimal precoSuv,
            BigDecimal precoQuatroXQuatro) {
    }

    /** Dados de rastreador. As credenciais só aparecem mascaradas (RN-20). */
    @Schema(name = "DadosDeRastreadorResponse")
    public record DadosDeRastreadorResponse(
            BigDecimal mensalidade,
            BigDecimal custoInstalacao,
            BigDecimal custoDesinstalacao,
            String equipadora,
            String portalUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiCredenciais,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String credencialMascarada) {
    }

    /** Dados de gráfica. */
    @Schema(name = "DadosDeGraficaResponse")
    public record DadosDeGraficaResponse(
            String tamanhoAdesivo, BigDecimal precoAdesivo, String tamanhoIma, BigDecimal precoIma) {
    }
}
