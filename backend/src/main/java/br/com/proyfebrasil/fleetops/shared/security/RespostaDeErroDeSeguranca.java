package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.config.CorrelacaoRequestFilter;
import br.com.proyfebrasil.fleetops.shared.exception.CodigoErro;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Escreve respostas RFC 7807 a partir da cadeia de filtros do Spring Security.
 *
 * <p>Necessário porque falhas de autenticação e de autorização ocorrem <em>antes</em> do
 * {@code @RestControllerAdvice}: sem isto, 401 e 403 sairiam no formato padrão do
 * contêiner, quebrando o contrato de erro que o frontend consome.
 */
final class RespostaDeErroDeSeguranca {

    private static final String BASE_TIPO = "https://fleetops.proyfebrasil.com.br/erros/";

    private RespostaDeErroDeSeguranca() {
    }

    static void escrever(
            ObjectMapper objectMapper,
            HttpServletRequest requisicao,
            HttpServletResponse resposta,
            CodigoErro erro,
            String detalhe)
            throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(erro.status(), detalhe);
        problema.setTitle(erro.titulo());
        problema.setType(URI.create(BASE_TIPO + erro.codigo()));
        problema.setInstance(URI.create(requisicao.getRequestURI()));
        problema.setProperty("codigo", erro.codigo());
        problema.setProperty("timestamp", Instant.now().toString());
        problema.setProperty("requestId", MDC.get(CorrelacaoRequestFilter.CHAVE_MDC));

        resposta.setStatus(erro.status().value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(resposta.getOutputStream(), problema);
    }
}
