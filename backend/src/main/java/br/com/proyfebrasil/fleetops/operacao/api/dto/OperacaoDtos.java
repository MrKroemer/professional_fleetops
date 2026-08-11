package br.com.proyfebrasil.fleetops.operacao.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs da operação mensal (Fase 3).
 *
 * <p>Os campos que o mapeador sempre preenche são marcados como obrigatórios, para que o
 * cliente gerado os traga sem `undefined` — a mesma convenção dos cadastros.
 */
public final class OperacaoDtos {

    private OperacaoDtos() {
    }

    private static final String OBRIGATORIO = "REQUIRED";

    // ------------------------------------------------------------- quilometragem

    @Schema(description = "Registro diário de quilometragem (FOR.FRO.02)")
    public record RegistroDeKmRequest(
            Long condutorId,
            @NotNull(message = "Informe a data") LocalDate data,
            @NotNull(message = "Informe o KM inicial") @PositiveOrZero Integer kmInicial,
            @NotNull(message = "Informe o KM final") @PositiveOrZero Integer kmFinal,
            @Size(max = 180) String origem,
            @Size(max = 180) String destino,
            String observacao) {
    }

    @Schema(description = "Registro diário de quilometragem")
    public record RegistroDeKmResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate data,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmInicial,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmFinal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmPercorrido,
            String condutor,
            String origem,
            String destino,
            String observacao) {
    }

    // ------------------------------------------------------------- abastecimento

    @Schema(description = "Abastecimento (RN-04)")
    public record AbastecimentoRequest(
            @NotNull(message = "Informe o posto onde abasteceu") Long postoId,
            @NotNull(message = "Informe a data") LocalDate data,
            @NotNull(message = "Informe o valor") @PositiveOrZero BigDecimal valor,
            BigDecimal litros,
            @PositiveOrZero Integer km,
            String observacao,
            @Schema(description = "Obrigatória quando o lançamento está fora das condições autorizadas")
            String justificativa) {
    }

    @Schema(description = "Abastecimento lançado")
    public record AbastecimentoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate data,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean naoConforme,
            String posto,
            BigDecimal litros,
            BigDecimal precoPorLitro,
            Integer km,
            String justificativa,
            String observacao) {
    }

    @Schema(description = "Avaliação prévia de conformidade de um abastecimento (RN-04)")
    public record ConformidadeResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean conforme,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> motivos) {
    }

    // ------------------------------------------------------------- serviços

    @Schema(description = "Serviço de lava-jato, borracharia ou para-brisas (RN-05)")
    public record ServicoRequest(
            @NotNull(message = "Informe o tipo do serviço") String tipo,
            Long fornecedorId,
            @NotNull(message = "Informe a data") LocalDate data,
            @NotNull(message = "Informe o valor") @PositiveOrZero BigDecimal valor,
            @Size(max = 300) String descricao,
            String justificativa) {
    }

    @Schema(description = "Serviço lançado")
    public record ServicoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate data,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean naoConforme,
            String fornecedor,
            String descricao,
            String justificativa) {
    }

    // ------------------------------------------------------------- fechamento

    @Schema(description = "Fechamento mensal — todos os números derivados dos lançamentos (RN-06, RN-21)")
    public record FechamentoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long contratoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String competencia,
            String placa,
            String obra,
            Integer kmInicial,
            Integer kmFinal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmPercorrido,
            Integer pacoteContratado,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmExcedente,
            BigDecimal valorDoKmExcedente,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoDoExcedente,
            @Schema(description = "Verdadeiro quando não há tabela de preços do ano — o custo não pôde ser estimado",
                    requiredMode = Schema.RequiredMode.REQUIRED) Boolean vigenciaIndisponivel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal consumoTotal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer quantidadeDeAbastecimentos,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoDeLavaJato,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoDeBorracharia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoDeParaBrisas,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal custoTotal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lancamentosNaoConformes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean estourouOPacote,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
            String observacoes) {
    }

    @Schema(description = "Conferência de uma competência")
    public record ConferenciaRequest(String observacoes) {
    }

    // ------------------------------------------------------------- faturas

    @Schema(description = "Fatura mensal da locadora (RN-13)")
    public record FaturaRequest(
            @NotNull(message = "Informe a competência no formato AAAA-MM") String competencia,
            @PositiveOrZero BigDecimal valorContratado,
            @PositiveOrZero BigDecimal valorFaturado,
            @PositiveOrZero BigDecimal extrasAprovados,
            @Size(max = 60) String numeroDaNota,
            LocalDate vencimento,
            String status,
            String observacoes) {
    }

    @Schema(description = "Fatura lançada")
    public record FaturaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long contratoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String competencia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorContratado,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal valorFaturado,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal extrasAprovados,
            @Schema(description = "Derivada: faturado − (contratado + extras)",
                    requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal divergencia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean exigeTratativa,
            String numeroDaNota,
            LocalDate vencimento,
            String observacoes,
            String obra,
            String placa) {
    }

    // ------------------------------------------------------------- uso particular

    @Schema(description = "Autorização de uso particular (RN-10)")
    public record UsoParticularRequest(
            @NotNull(message = "Informe o condutor") Long condutorId,
            @NotNull(message = "Informe o tipo") String tipo,
            @NotNull(message = "Informe o início") LocalDate inicio,
            @NotNull(message = "Informe o fim") LocalDate fim,
            @Schema(description = "Até 1.000 km, teto da RN-10") Integer kmAutorizado,
            @PositiveOrZero Integer kmPercorrido,
            @Schema(description = "Registra o aceite das regras pelo condutor") boolean aceitarRegras,
            String observacoes) {
    }

    @Schema(description = "Autorização de uso particular")
    public record UsoParticularResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String condutor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate inicio,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate fim,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmAutorizado,
            Integer kmPercorrido,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer kmExcedido,
            @Schema(description = "Sem aceite, a autorização não vale (RN-10)",
                    requiredMode = Schema.RequiredMode.REQUIRED) Boolean valida,
            Instant aceiteEm,
            String observacoes) {
    }
}
