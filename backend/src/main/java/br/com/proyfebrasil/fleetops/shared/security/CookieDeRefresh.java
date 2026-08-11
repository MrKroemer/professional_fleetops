package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie que transporta o refresh token.
 *
 * <p>Decisão de segurança (Seção 11 da especificação, que exige discussão explícita do
 * trade-off): <strong>nenhum token é gravado em {@code localStorage}</strong>. O access
 * token vive apenas na memória da aba (estado do React) e o refresh token trafega em
 * cookie {@code HttpOnly}, inacessível a JavaScript — o que remove o vetor clássico de
 * roubo de sessão por XSS.
 *
 * <p>Contrapartidas assumidas:
 * <ul>
 *   <li>recarregar a página descarta o access token; a sessão é reconstruída chamando
 *       {@code /auth/refresh} na inicialização do app;</li>
 *   <li>cookie exige mesma origem entre frontend e API — garantido pelo proxy
 *       {@code /api} do Nginx em produção e pelo proxy do Vite em desenvolvimento;</li>
 *   <li>{@code SameSite=Strict} combinado com o {@code Path} restrito aos endpoints de
 *       autenticação neutraliza CSRF, já que nenhuma outra rota aceita o cookie e
 *       requisições originadas de outros sites não o carregam.</li>
 * </ul>
 */
@Component
public class CookieDeRefresh {

    public static final String NOME = "fleetops_refresh";

    /** O cookie só é enviado nos endpoints de autenticação — nunca no resto da API. */
    private static final String CAMINHO = "/api/v1/auth";

    private final boolean seguro;

    public CookieDeRefresh(FleetOpsProperties propriedades) {
        this.seguro = propriedades.jwt().cookieSecure();
    }

    /** Monta o cookie de sessão com a validade do refresh token. */
    public ResponseCookie criar(String token, Duration validade) {
        return base(token).maxAge(validade).build();
    }

    /** Monta o cookie de remoção, usado no logout. */
    public ResponseCookie expirar() {
        return base("").maxAge(0).build();
    }

    /** Lê o refresh token da requisição, se presente. */
    public Optional<String> extrair(HttpServletRequest requisicao) {
        Cookie[] cookies = requisicao.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NOME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite("Strict")
                .path(CAMINHO);
    }
}
