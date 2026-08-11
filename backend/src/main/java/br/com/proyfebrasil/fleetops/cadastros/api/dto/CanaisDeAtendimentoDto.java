package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Canais de atendimento de uma locadora.
 *
 * <p>São os números que o condutor precisa em campo — guincho, assistência 24h,
 * agendamento de revisão. Ficam no cadastro para que o plano de viagem e o controle de
 * KM possam imprimi-los sem redigitação.
 */
@Schema(name = "CanaisDeAtendimento", description = "Canais de atendimento da locadora")
public record CanaisDeAtendimentoDto(
        @Size(max = 200) String reservas,
        @Size(max = 200) String manutencao,
        @Size(max = 200) String guinchoSinistro,
        @Size(max = 200) String assistencia24h,
        @Size(max = 200) String financeiro,
        @Size(max = 200) String suporte,
        @Size(max = 200) String telemetria) {
}
