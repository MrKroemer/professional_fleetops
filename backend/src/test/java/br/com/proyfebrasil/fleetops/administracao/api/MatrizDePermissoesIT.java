package br.com.proyfebrasil.fleetops.administracao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.proyfebrasil.fleetops.TesteDeIntegracao;
import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.security.DadosDoToken;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import br.com.proyfebrasil.fleetops.shared.security.ServicoDeTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RN-19 — matriz de permissões da administração de usuários.
 *
 * <p>A administração de usuários é exclusiva do perfil {@code ADMIN}: {@code GESTOR_FROTA}
 * tem operação completa da frota, mas não administra acessos, e {@code CONSULTA} é somente
 * leitura. Cada verbo é verificado individualmente, e não por amostragem, porque uma
 * anotação esquecida em um único método é exatamente o tipo de falha que este teste existe
 * para pegar.
 */
@DisplayName("RN-19 — matriz de permissões por perfil")
class MatrizDePermissoesIT extends TesteDeIntegracao {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificadorDeSenha;

    @Autowired
    private ServicoDeTokens tokens;

    private final Map<Perfil, String> tokensPorPerfil = new EnumMap<>(Perfil.class);

    private Long idDeUmUsuarioExistente;

    @BeforeEach
    void prepararUsuariosDeCadaPerfil() {
        for (Perfil perfil : Perfil.values()) {
            String email = "perfil." + perfil.name().toLowerCase() + "@proyfebrasil.com.br";
            Usuario usuario = usuarios.buscarPorEmail(email)
                    .orElseGet(() -> usuarios.save(new Usuario(
                            "Usuário " + perfil.getDescricao(),
                            email,
                            codificadorDeSenha.encode("SenhaDeTeste@2026"),
                            perfil)));
            tokensPorPerfil.put(perfil, emitirToken(usuario));
            if (perfil == Perfil.CONSULTA) {
                idDeUmUsuarioExistente = usuario.getId();
            }
        }
    }

    @Test
    @DisplayName("RN19_deveLiberarAdministracaoDeUsuariosApenasParaAdmin")
    void adminTemAcessoTotal() throws Exception {
        String admin = tokensPorPerfil.get(Perfil.ADMIN);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isArray())
                .andExpect(jsonPath("$.totalElementos").isNumber());

        mockMvc.perform(get("/api/v1/usuarios/" + idDeUmUsuarioExistente)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"GESTOR_FROTA", "CONSULTA"})
    @DisplayName("RN19_deveNegarListagemDeUsuariosParaPerfisNaoAdministradores")
    void naoAdminNaoLista(Perfil perfil) throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tokensPorPerfil.get(perfil)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("GEN-005-ACESSO_NEGADO"));
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"GESTOR_FROTA", "CONSULTA"})
    @DisplayName("RN19_deveNegarCriacaoDeUsuarioParaPerfisNaoAdministradores")
    void naoAdminNaoCria(Perfil perfil) throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(perfil))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeUsuario("tentativa@proyfebrasil.com.br")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"GESTOR_FROTA", "CONSULTA"})
    @DisplayName("RN19_deveNegarAlteracaoDeUsuarioParaPerfisNaoAdministradores")
    void naoAdminNaoAltera(Perfil perfil) throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + idDeUmUsuarioExistente)
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(perfil))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeUsuario("alterado@proyfebrasil.com.br")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"GESTOR_FROTA", "CONSULTA"})
    @DisplayName("RN19_deveNegarExclusaoDeUsuarioParaPerfisNaoAdministradores")
    void naoAdminNaoExclui(Perfil perfil) throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/" + idDeUmUsuarioExistente)
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(perfil)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    @DisplayName("RN19_deveLiberarOProprioPerfilParaTodosOsPerfis")
    void todosConsultamOProprioPerfil(Perfil perfil) throws Exception {
        mockMvc.perform(get("/api/v1/auth/eu").header("Authorization", "Bearer " + tokensPorPerfil.get(perfil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value(perfil.name()));
    }

    @Test
    @DisplayName("RN19_deveExigirAutenticacaoEmTodaARotaDeAdministracao")
    void semTokenNaoAcessa() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GEN-004-NAO_AUTENTICADO"));
    }

    @Test
    @DisplayName("admin cria, atualiza e exclui logicamente um usuário")
    void cicloCompletoDeAdministracao() throws Exception {
        String admin = tokensPorPerfil.get(Perfil.ADMIN);

        String criado = mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeUsuario("ciclo@proyfebrasil.com.br")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ciclo@proyfebrasil.com.br"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(criado).get("id").asLong();

        // E-mail repetido é recusado com conflito, não com 500.
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeUsuario("ciclo@proyfebrasil.com.br")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GEN-006-CONFLITO_DE_ESTADO"));

        mockMvc.perform(delete("/api/v1/usuarios/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/usuarios/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GEN-001-RECURSO_NAO_ENCONTRADO"));

        // Exclusão é lógica: o registro continua no banco, apenas marcado.
        assertThat(usuarios.findById(id)).isPresent().get().satisfies(usuario ->
                assertThat(usuario.isExcluida()).isTrue());
    }

    private String emitirToken(Usuario usuario) {
        DadosDoToken dados = new DadosDoToken(
                usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getPerfil());
        return tokens.emitirAccessToken(dados).valor();
    }

    private String corpoDeUsuario(String email) {
        return """
                {"nome": "Usuário de Teste", "email": "%s", "perfil": "CONSULTA",
                 "senha": "SenhaDeTeste@2026", "ativo": true}
                """.formatted(email);
    }
}
