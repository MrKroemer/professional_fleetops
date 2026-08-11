package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Responde 401 no formato RFC 7807 quando falta autenticação válida. */
@Component
public class PontoDeEntradaNaoAutenticado implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public PontoDeEntradaNaoAutenticado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest requisicao, HttpServletResponse resposta, AuthenticationException excecao)
            throws IOException {
        RespostaDeErroDeSeguranca.escrever(
                objectMapper,
                requisicao,
                resposta,
                ErroComum.NAO_AUTENTICADO,
                "Sessão ausente ou expirada. Autentique-se para continuar.");
    }
}
