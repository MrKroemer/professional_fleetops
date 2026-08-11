package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Condutor cadastrado, já com a situação da CNH avaliada (RN-16).
 *
 * @param cnhVencida       habilitação vencida na data de hoje
 * @param cnhEmAlerta      vencimento dentro da faixa de 60 dias
 * @param diasParaVencerCnh dias restantes; negativo se vencida, nulo se sem CNH cadastrada
 */
@Schema(name = "CondutorResponse", description = "Condutor cadastrado")
public record CondutorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cpf,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cpfFormatado,
        String cargo,
        String telefone,
        String email,
        String cnhNumero,
        String cnhCategoria,
        LocalDate cnhValidade,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cnhVencida,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cnhEmAlerta,
        Long diasParaVencerCnh,
        ObraResumoResponse obraAtual,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StatusCondutor status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusDescricao,
        String observacoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {
}
