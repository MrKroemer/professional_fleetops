package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Responde 403 no formato RFC 7807 quando o perfil do usuário não autoriza a operação (RN-19). */
@Component
public class ManipuladorDeAcessoNegado implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ManipuladorDeAcessoNegado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest requisicao, HttpServletResponse resposta, AccessDeniedException excecao)
            throws IOException {
        RespostaDeErroDeSeguranca.escrever(
                objectMapper,
                requisicao,
                resposta,
                ErroComum.ACESSO_NEGADO,
                "Seu perfil não permite executar esta operação.");
    }
}
