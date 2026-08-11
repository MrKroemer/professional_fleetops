package br.com.proyfebrasil.fleetops.administracao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.proyfebrasil.fleetops.TesteDeIntegracao;
import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.security.CookieDeRefresh;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Autenticação ponta a ponta contra PostgreSQL real")
class AutenticacaoIT extends TesteDeIntegracao {

    private static final String EMAIL = "gestor.it@proyfebrasil.com.br";
    private static final String SENHA = "SenhaDeTeste@2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private SessaoRefreshRepository sessoes;

    @Autowired
    private PasswordEncoder codificadorDeSenha;

    @BeforeEach
    void prepararUsuario() {
        if (usuarios.buscarPorEmail(EMAIL).isEmpty()) {
            usuarios.save(new Usuario(
                    "Gestor de Integração", EMAIL, codificadorDeSenha.encode(SENHA), Perfil.GESTOR_FROTA));
        }
    }

    @Test
    @DisplayName("login devolve access token no corpo e refresh apenas em cookie httpOnly")
    void loginDevolveTokenECookie() throws Exception {
        MvcResult resultado = login();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(corpo.get("accessToken").asText()).isNotBlank();
        assertThat(corpo.get("usuario").get("email").asText()).isEqualTo(EMAIL);
        assertThat(corpo.get("usuario").get("perfil").asText()).isEqualTo("GESTOR_FROTA");
        // A senha nunca aparece na resposta, em nenhuma forma.
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("senha");

        Cookie refresh = resultado.getResponse().getCookie(CookieDeRefresh.NOME);
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(resultado.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("credenciais inválidas resultam em 401 no formato RFC 7807")
    void credenciaisInvalidas() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senha-errada"}
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.codigo").value("GEN-003-CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("corpo de login inválido detalha os campos rejeitados")
    void validacaoDeCorpo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nao-e-email", "senha": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("GEN-002-VALIDACAO"))
                .andExpect(jsonPath("$.erros[0].campo").value("email"))
                .andExpect(jsonPath("$.erros[1].campo").value("senha"));
    }

    @Test
    @DisplayName("o endpoint /eu devolve o usuário autenticado e exige token")
    void endpointEu() throws Exception {
        String accessToken = accessTokenDe(login());

        mockMvc.perform(get("/api/v1/auth/eu").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.perfilDescricao").value("Gestor de frota"));

        mockMvc.perform(get("/api/v1/auth/eu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GEN-004-NAO_AUTENTICADO"));
    }

    @Test
    @DisplayName("o refresh token não é aceito como token de acesso")
    void refreshNaoServeComoAccess() throws Exception {
        Cookie refresh = login().getResponse().getCookie(CookieDeRefresh.NOME);

        mockMvc.perform(get("/api/v1/auth/eu").header("Authorization", "Bearer " + refresh.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("renovação rotaciona o refresh token e invalida o anterior")
    void renovacaoRotacionaToken() throws Exception {
        Cookie primeiro = login().getResponse().getCookie(CookieDeRefresh.NOME);

        MvcResult renovacao = mockMvc.perform(post("/api/v1/auth/refresh").cookie(primeiro))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieDeRefresh.NOME))
                .andReturn();

        Cookie segundo = renovacao.getResponse().getCookie(CookieDeRefresh.NOME);
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());

        // Reapresentar o token já rotacionado é recusado.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(primeiro))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GEN-007-SESSAO_INVALIDA"));

        // E, por indicar vazamento, encerra também a sessão que era válida.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(segundo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("renovação sem cookie resulta em 401")
    void renovacaoSemCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GEN-007-SESSAO_INVALIDA"));
    }

    @Test
    @DisplayName("logout revoga a sessão, expira o cookie e é idempotente")
    void logoutRevogaEExpiraCookie() throws Exception {
        Cookie refresh = login().getResponse().getCookie(CookieDeRefresh.NOME);
        long abertasAntes = sessoes.findAll().stream().filter(sessao -> sessao.getRevogadoEm() == null).count();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(CookieDeRefresh.NOME, 0));

        long abertasDepois = sessoes.findAll().stream().filter(sessao -> sessao.getRevogadoEm() == null).count();
        assertThat(abertasDepois).isEqualTo(abertasAntes - 1);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rota inexistente sem autenticação responde 401, sem revelar se a rota existe")
    void rotaInexistenteNaoVazaExistencia() throws Exception {
        mockMvc.perform(get("/api/v1/inexistente"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GEN-004-NAO_AUTENTICADO"));
    }

    // -----------------------------------------------------------------

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}
                                """.formatted(EMAIL, SENHA)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String accessTokenDe(MvcResult resultado) throws Exception {
        return objectMapper
                .readTree(resultado.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }
}
