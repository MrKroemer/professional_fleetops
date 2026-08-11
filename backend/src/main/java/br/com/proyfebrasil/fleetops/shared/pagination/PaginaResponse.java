package br.com.proyfebrasil.fleetops.shared.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginação único da API. Existe para não vazar o {@code Page} do Spring Data
 * (cuja serialização não é contrato estável) e para dar ao frontend um formato previsível
 * em todas as listagens.
 *
 * @param <T>              tipo do item já convertido em DTO
 * @param conteudo         itens da página atual
 * @param pagina           índice da página, começando em zero
 * @param tamanho          quantidade de itens solicitada por página
 * @param totalElementos   total de registros que atendem ao filtro
 * @param totalPaginas     total de páginas disponíveis
 * @param primeira         indica se é a primeira página
 * @param ultima           indica se é a última página
 */
@Schema(description = "Envelope padrão de listagens paginadas")
public record PaginaResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> conteudo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pagina,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int tamanho,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElementos,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPaginas,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean primeira,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ultima) {

    /** Converte um {@link Page} de entidades em uma página de DTOs. */
    public static <E, D> PaginaResponse<D> de(Page<E> page, Function<E, D> conversor) {
        return new PaginaResponse<>(
                page.getContent().stream().map(conversor).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
