package br.com.proyfebrasil.fleetops.painel.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.VeiculoResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DTOs das análises cruzadas e do painel de um veículo. */
public final class AnaliseDtos {

    private AnaliseDtos() {
    }

    /** Uma linha do cruzamento categoria × locadora. */
    @Schema(name = "LinhaDaMatrizResponse")
    public record LinhaDaMatrizResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String chave,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rotulo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Quantidade por locadora")
            Map<String, Long> porLocadora) {
    }

    /** Cruzamento da frota entre categoria e locadora. */
    @Schema(name = "MatrizDaFrotaResponse", description = "Frota cruzada por categoria e locadora")
    public record MatrizDaFrotaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Colunas, na ordem de exibição")
            List<String> locadoras,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LinhaDaMatrizResponse> linhas) {
    }

    /** Um ponto da curva de preço. */
    @Schema(name = "PontoDaCurvaResponse")
    public record PontoDaCurvaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pacoteKm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorMedio) {
    }

    /** A curva de uma locadora ao longo dos pacotes de KM. */
    @Schema(name = "CurvaDeLocadoraResponse")
    public record CurvaDeLocadoraResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String locadora,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PontoDaCurvaResponse> pontos) {
    }

    /** Variação de preço de um grupo entre duas vigências. */
    @Schema(name = "ReajusteDoGrupoResponse")
    public record ReajusteDoGrupoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String locadora,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String grupo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoria,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoriaDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pacoteKm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorAnterior,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorAtual,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal variacaoPercentual) {
    }

    /** Comparação entre as duas vigências mais recentes. */
    @Schema(name = "ComparacaoDeVigenciasResponse")
    public record ComparacaoDeVigenciasResponse(
            Integer anoAnterior,
            Integer anoAtual,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReajusteDoGrupoResponse> reajustes,
            BigDecimal variacaoMedia) {
    }

    /** Conjunto de análises do painel. */
    @Schema(name = "AnalisesResponse", description = "Cruzamentos analíticos da frota e dos preços")
    public record AnalisesResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int anoDeReferencia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MatrizDaFrotaResponse matrizDaFrota,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CurvaDeLocadoraResponse> curvasDePreco,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ComparacaoDeVigenciasResponse vigencias) {
    }

    /**
     * Um veículo em operação, com quem o dirige e onde.
     *
     * <p>Reúne veículo, obra e condutor porque é assim que o card é lido: a pergunta do
     * gestor não é "que placas existem", e sim "quem está com este carro e em qual obra".
     */
    @Schema(name = "VeiculoEmOperacaoResponse", description = "Veículo com contrato ativo")
    public record VeiculoEmOperacaoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long veiculoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placaFormatada,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoria,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoriaDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exigeTesteFumacaPreta,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiRastreador,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean possuiAdesivo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String locadora,
            String grupoTarifario,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long contratoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String obraCodigo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String obraNome,
            String localRetirada,
            Integer pacoteKmContratado,
            LocalDate dataRetirada,
            Long condutorId,
            String condutorNome,
            String condutorCargo,
            LocalDate cnhValidade,
            Long diasParaVencerCnh,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cnhVencida,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cnhEmAlerta) {
    }

    /** Um resultado da busca global, com a rota que abre o registro. */
    @Schema(name = "ResultadoDaBuscaResponse")
    public record ResultadoDaBuscaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "VEICULO, CONDUTOR ou OBRA")
            String tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rotulo,
            String detalhe,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rota) {
    }

    /** Resultados da busca global, agrupados por tipo. */
    @Schema(name = "BuscaGlobalResponse", description = "Busca por placa, condutor e obra")
    public record BuscaGlobalResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResultadoDaBuscaResponse> veiculos,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResultadoDaBuscaResponse> condutores,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResultadoDaBuscaResponse> obras,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int total) {
    }

    /** Uma opção de pacote de quilometragem para um veículo. */
    @Schema(name = "OpcaoDePacoteResponse")
    public record OpcaoDePacoteResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pacoteKm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorMensal,
            @Schema(description = "Custo de cada KM que passar da franquia (RN-06)")
            BigDecimal valorKmExcedente) {
    }

    /** Grade tarifária aplicável ao veículo. */
    @Schema(name = "GradeDoVeiculoResponse")
    public record GradeDoVeiculoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int anoVigencia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String grupo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String veiculosDoGrupo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OpcaoDePacoteResponse> pacotes,
            BigDecimal menorValorMensal) {
    }

    /**
     * Painel de um veículo.
     *
     * @param motivoSemGrade explicação em português de por que não há grade, quando for
     *     o caso — nulo quando a grade existe
     */
    @Schema(name = "PainelDoVeiculoResponse", description = "Visão completa de um veículo")
    public record PainelDoVeiculoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VeiculoResponse veiculo,
            GradeDoVeiculoResponse grade,
            String motivoSemGrade) {
    }
}
