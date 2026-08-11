package br.com.proyfebrasil.fleetops.shared.exception;

import br.com.proyfebrasil.fleetops.shared.config.CorrelacaoRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tradução de exceções para respostas RFC 7807 (Problem Details).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para que as exceções do próprio Spring
 * MVC — corpo JSON malformado, método HTTP não suportado, parâmetro de tipo incompatível,
 * rota inexistente — também saiam no formato de erro padronizado, em vez de caírem no
 * tratamento genérico e virarem 500.
 *
 * <p>Todo corpo de erro carrega, além dos campos padrão da RFC:
 * <ul>
 *   <li>{@code codigo} — identificador estável do erro ({@link CodigoErro});</li>
 *   <li>{@code timestamp} — instante da falha;</li>
 *   <li>{@code requestId} — correlação com os logs estruturados;</li>
 *   <li>{@code contexto} — dados estruturados do erro, quando houver;</li>
 *   <li>{@code erros} — lista de violações por campo, em erros de validação.</li>
 * </ul>
 */
@RestControllerAdvice
public class ManipuladorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManipuladorGlobalDeErros.class);
    private static final String BASE_TIPO = "https://fleetops.proyfebrasil.com.br/erros/";

    /**
     * Violação de campo em uma requisição.
     *
     * @param campo    caminho do campo rejeitado
     * @param mensagem motivo da rejeição, em pt-BR
     */
    public record ViolacaoCampo(String campo, String mensagem) {
    }

    // ---------------------------------------------------------------------
    // Erros de domínio e de segurança
    // ---------------------------------------------------------------------

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail tratarNegocio(NegocioException ex, HttpServletRequest requisicao) {
        CodigoErro erro = ex.codigoErro();
        if (erro.status().is5xxServerError()) {
            LOG.error("Falha de negócio com status 5xx: {}", erro.codigo(), ex);
        } else {
            LOG.info("Regra de negócio violada: {} — {}", erro.codigo(), ex.getMessage());
        }
        ProblemDetail problema = montar(erro, ex.getMessage(), requisicao.getRequestURI());
        if (!ex.contexto().isEmpty()) {
            problema.setProperty("contexto", ex.contexto());
        }
        return problema;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail tratarConstraint(ConstraintViolationException ex, HttpServletRequest requisicao) {
        List<ViolacaoCampo> violacoes = ex.getConstraintViolations().stream()
                .map(violacao -> new ViolacaoCampo(
                        violacao.getPropertyPath().toString(), violacao.getMessage()))
                .sorted(Comparator.comparing(ViolacaoCampo::campo))
                .toList();
        ProblemDetail problema = montar(
                ErroComum.VALIDACAO, "Um ou mais campos não passaram na validação.", requisicao.getRequestURI());
        problema.setProperty("erros", violacoes);
        return problema;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail tratarAutenticacao(AuthenticationException ex, HttpServletRequest requisicao) {
        LOG.info("Falha de autenticação: {}", ex.getMessage());
        return montar(
                ErroComum.NAO_AUTENTICADO,
                "É necessário autenticar-se para acessar este recurso.",
                requisicao.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail tratarAcessoNegado(AccessDeniedException ex, HttpServletRequest requisicao) {
        LOG.info("Acesso negado em {}: {}", requisicao.getRequestURI(), ex.getMessage());
        return montar(
                ErroComum.ACESSO_NEGADO,
                "Seu perfil não permite executar esta operação.",
                requisicao.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarInesperado(Exception ex, HttpServletRequest requisicao) {
        LOG.error("Erro inesperado em {} {}", requisicao.getMethod(), requisicao.getRequestURI(), ex);
        return montar(
                ErroComum.ERRO_INTERNO,
                "Ocorreu um erro inesperado. Informe o identificador da requisição ao suporte.",
                requisicao.getRequestURI());
    }

    // ---------------------------------------------------------------------
    // Erros do Spring MVC — enriquecidos com as extensões do contrato de erro
    // ---------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        List<ViolacaoCampo> violacoes = ex.getBindingResult().getFieldErrors().stream()
                .map(this::paraViolacao)
                .sorted(Comparator.comparing(ViolacaoCampo::campo))
                .toList();
        ProblemDetail problema = montar(
                ErroComum.VALIDACAO, "Um ou mais campos não passaram na validação.", caminhoDe(requisicao));
        problema.setProperty("erros", violacoes);
        return ResponseEntity.status(ErroComum.VALIDACAO.status()).body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders cabecalhos,
            HttpStatusCode status,
            WebRequest requisicao) {
        List<ViolacaoCampo> violacoes = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(erro -> new ViolacaoCampo(
                                resultado.getMethodParameter().getParameterName(), erro.getDefaultMessage())))
                .sorted(Comparator.comparing(ViolacaoCampo::campo, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        ProblemDetail problema = montar(
                ErroComum.VALIDACAO, "Um ou mais parâmetros não passaram na validação.", caminhoDe(requisicao));
        problema.setProperty("erros", violacoes);
        return ResponseEntity.status(ErroComum.VALIDACAO.status()).body(problema);
    }

    /**
     * Ponto único por onde passam as demais exceções tratadas pelo Spring MVC: aqui o
     * corpo padrão é complementado com {@code codigo}, {@code timestamp} e {@code requestId}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object corpo,
            HttpHeaders cabecalhos,
            HttpStatusCode status,
            WebRequest requisicao) {
        ResponseEntity<Object> resposta = super.handleExceptionInternal(ex, corpo, cabecalhos, status, requisicao);
        if (resposta != null && resposta.getBody() instanceof ProblemDetail problema) {
            enriquecer(problema, codigoParaStatus(status), caminhoDe(requisicao));
        }
        return resposta;
    }

    // ---------------------------------------------------------------------

    private ViolacaoCampo paraViolacao(FieldError erro) {
        return new ViolacaoCampo(erro.getField(), erro.getDefaultMessage());
    }

    private ProblemDetail montar(CodigoErro erro, String detalhe, String caminho) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(erro.status(), detalhe);
        enriquecer(problema, erro, caminho);
        return problema;
    }

    private void enriquecer(ProblemDetail problema, CodigoErro erro, String caminho) {
        problema.setTitle(erro.titulo());
        problema.setType(URI.create(BASE_TIPO + erro.codigo()));
        if (caminho != null) {
            problema.setInstance(URI.create(caminho));
        }
        // Propriedades adicionadas uma a uma: `setProperties` substituiria o mapa interno
        // por um imutável, quebrando qualquer `setProperty` posterior.
        problema.setProperty("codigo", erro.codigo());
        problema.setProperty("timestamp", Instant.now().toString());
        problema.setProperty("requestId", MDC.get(CorrelacaoRequestFilter.CHAVE_MDC));
    }

    private CodigoErro codigoParaStatus(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErroComum.RECURSO_NAO_ENCONTRADO;
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErroComum.METODO_NAO_SUPORTADO;
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ErroComum.MIDIA_NAO_SUPORTADA;
        }
        if (status.is4xxClientError()) {
            return ErroComum.REQUISICAO_MALFORMADA;
        }
        return ErroComum.ERRO_INTERNO;
    }

    private String caminhoDe(WebRequest requisicao) {
        if (requisicao instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
