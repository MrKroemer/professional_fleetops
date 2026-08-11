package br.com.proyfebrasil.fleetops.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlaciona logs e respostas de erro a uma mesma requisição.
 *
 * <p>Reaproveita o cabeçalho {@code X-Request-Id} quando o cliente o envia (permitindo
 * rastrear a chamada de ponta a ponta a partir do frontend) e o devolve na resposta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacaoRequestFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String CHAVE_MDC = "requestId";

    private static final int TAMANHO_MAXIMO = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = normalizar(request.getHeader(CABECALHO));
        MDC.put(CHAVE_MDC, requestId);
        response.setHeader(CABECALHO, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }

    private String normalizar(String recebido) {
        if (recebido == null || recebido.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String limpo = recebido.replaceAll("[^A-Za-z0-9._-]", "");
        if (limpo.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return limpo.length() > TAMANHO_MAXIMO ? limpo.substring(0, TAMANHO_MAXIMO) : limpo;
    }
}
