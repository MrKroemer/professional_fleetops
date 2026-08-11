package br.com.proyfebrasil.fleetops.administracao.api;

import br.com.proyfebrasil.fleetops.administracao.api.dto.LoginRequest;
import br.com.proyfebrasil.fleetops.administracao.api.dto.SessaoResponse;
import br.com.proyfebrasil.fleetops.administracao.api.dto.UsuarioResponse;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeAutenticacao;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeAutenticacao.OrigemDaSessao;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeAutenticacao.SessaoCriada;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeUsuarios;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.security.CookieDeRefresh;
import br.com.proyfebrasil.fleetops.shared.security.ServicoDeTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ciclo de vida da sessão.
 *
 * <p>O refresh token nunca aparece no corpo das respostas: ele é gravado e lido apenas
 * pelo cookie {@code httpOnly} {@code fleetops_refresh}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, renovação e encerramento de sessão")
public class AutenticacaoController {

    private final ServicoDeAutenticacao autenticacao;
    private final ServicoDeUsuarios usuarios;
    private final UsuarioMapper mapper;
    private final CookieDeRefresh cookieDeRefresh;
    private final Duration ttlRefresh;

    public AutenticacaoController(
            ServicoDeAutenticacao autenticacao,
            ServicoDeUsuarios usuarios,
            UsuarioMapper mapper,
            CookieDeRefresh cookieDeRefresh,
            ServicoDeTokens tokens) {
        this.autenticacao = autenticacao;
        this.usuarios = usuarios;
        this.mapper = mapper;
        this.cookieDeRefresh = cookieDeRefresh;
        this.ttlRefresh = tokens.ttlRefresh();
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Autentica um usuário",
            description = "Devolve o access token no corpo e grava o refresh token em cookie httpOnly.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão criada"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos"),
        @ApiResponse(responseCode = "403", description = "Usuário inativo"),
    })
    public ResponseEntity<SessaoResponse> login(
            @Valid @RequestBody LoginRequest requisicao, HttpServletRequest http) {
        SessaoCriada sessao = autenticacao.autenticar(requisicao.email(), requisicao.senha(), origemDe(http));
        return respostaComSessao(sessao);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Renova a sessão",
            description = "Usa o cookie httpOnly de refresh, rotacionando-o. Nenhum corpo de requisição é necessário.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão renovada"),
        @ApiResponse(responseCode = "401", description = "Sessão ausente, expirada ou revogada"),
    })
    public ResponseEntity<SessaoResponse> renovar(HttpServletRequest http) {
        String refreshToken = cookieDeRefresh.extrair(http)
                .orElseThrow(() -> new NegocioException(
                        ErroComum.SESSAO_INVALIDA, "Sessão expirada. Faça login novamente."));
        SessaoCriada sessao = autenticacao.renovar(refreshToken, origemDe(http));
        return respostaComSessao(sessao);
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Encerra a sessão", description = "Revoga o refresh token e remove o cookie. Idempotente.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        cookieDeRefresh.extrair(http).ifPresent(autenticacao::encerrar);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieDeRefresh.expirar().toString())
                .build();
    }

    @GetMapping("/eu")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Dados do usuário autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário autenticado"),
        @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada"),
    })
    public UsuarioResponse eu(@AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        return mapper.paraResponse(usuarios.buscar(id));
    }

    private ResponseEntity<SessaoResponse> respostaComSessao(SessaoCriada sessao) {
        ResponseCookie cookie = cookieDeRefresh.criar(sessao.refreshToken().valor(), ttlRefresh);
        SessaoResponse corpo = new SessaoResponse(
                sessao.accessToken().valor(),
                sessao.accessToken().expiraEm(),
                mapper.paraResponse(sessao.usuario()));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(corpo);
    }

    private OrigemDaSessao origemDe(HttpServletRequest http) {
        return new OrigemDaSessao(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
