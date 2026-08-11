package br.com.proyfebrasil.fleetops.shared.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Violação de uma regra de domínio. Carrega um {@link CodigoErro} estável e um detalhe
 * já redigido em pt-BR, pronto para exibição ao usuário.
 *
 * <p>O campo {@code contexto} transporta dados estruturados que o frontend pode usar
 * para montar a mensagem (ex.: a data do abastecimento duplicado), sem que ele precise
 * interpretar o texto livre.
 */
public class NegocioException extends RuntimeException {

    private final transient CodigoErro codigoErro;
    private final transient Map<String, Object> contexto;

    public NegocioException(CodigoErro codigoErro, String detalhe) {
        this(codigoErro, detalhe, Map.of());
    }

    public NegocioException(CodigoErro codigoErro, String detalhe, Map<String, Object> contexto) {
        super(detalhe);
        this.codigoErro = Objects.requireNonNull(codigoErro, "codigoErro é obrigatório");
        this.contexto = Map.copyOf(Objects.requireNonNull(contexto, "contexto é obrigatório"));
    }

    public CodigoErro codigoErro() {
        return codigoErro;
    }

    public Map<String, Object> contexto() {
        return contexto;
    }
}
