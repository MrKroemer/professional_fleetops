package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Locadora cadastrada.
 *
 * <p>As credenciais nunca aparecem aqui: a resposta informa apenas <em>se</em> existem
 * e uma máscara para exibição. O valor em claro só é obtido pelo endpoint dedicado de
 * revelação, que registra quem o solicitou (RN-20).
 */
@Schema(name = "LocadoraResponse", description = "Locadora cadastrada")
public record LocadoraResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TipoLocadora tipo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
        String consultor,
        String telefone,
        String email,
        String portalUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Indica se há credenciais armazenadas")
        boolean possuiCredenciais,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Máscara exibida no lugar da senha")
        String credencialMascarada,
        CanaisDeAtendimentoDto canais,
        String observacoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativa,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {
}
