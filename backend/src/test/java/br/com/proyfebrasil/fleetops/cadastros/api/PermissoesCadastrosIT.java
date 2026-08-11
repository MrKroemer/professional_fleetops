package br.com.proyfebrasil.fleetops.cadastros.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RN-19 — matriz de permissões dos cadastros.
 *
 * <p>Três níveis distintos: leitura para todos os perfis, escrita para administradores e
 * gestores, e credenciais de portal <em>apenas</em> para esses dois — o perfil
 * {@code CONSULTA} enxerga o cadastro, mas nunca o segredo.
 */
@DisplayName("RN-19 — permissões dos cadastros por perfil")
class PermissoesCadastrosIT extends TesteDeIntegracao {

    private static final String[] ROTAS_DE_CADASTRO = {
        "/api/v1/obras",
        "/api/v1/locadoras",
        "/api/v1/condutores",
        "/api/v1/veiculos",
        "/api/v1/fornecedores",
        "/api/v1/tabelas-preco",
    };

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

    @BeforeEach
    void prepararTokens() {
        for (Perfil perfil : Perfil.values()) {
            String email = "cadastros." + perfil.name().toLowerCase() + "@proyfebrasil.com.br";
            Usuario usuario = usuarios.buscarPorEmail(email)
                    .orElseGet(() -> usuarios.save(new Usuario(
                            "Usuário " + perfil.getDescricao(),
                            email,
                            codificadorDeSenha.encode("SenhaDeTeste@2026"),
                            perfil)));
            tokensPorPerfil.put(perfil, tokens.emitirAccessToken(new DadosDoToken(
                            usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getPerfil()))
                    .valor());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/obras",
        "/api/v1/locadoras",
        "/api/v1/condutores",
        "/api/v1/veiculos",
        "/api/v1/fornecedores",
        "/api/v1/tabelas-preco",
    })
    @DisplayName("RN19_deveLiberarLeituraDosCadastrosParaTodosOsPerfis")
    void leituraLiberadaParaTodos(String rota) throws Exception {
        for (Perfil perfil : Perfil.values()) {
            mockMvc.perform(get(rota).header("Authorization", "Bearer " + tokensPorPerfil.get(perfil)))
                    .andExpect(status().isOk());
        }
    }

    /**
     * O corpo enviado é <strong>válido</strong> de propósito.
     *
     * <p>A resolução de argumentos do Spring MVC — e portanto a validação do corpo —
     * acontece antes de {@code @PreAuthorize}. Enviar um corpo inválido produziria 400
     * e o teste passaria a medir a validação, não a autorização, deixando de detectar
     * a remoção acidental da anotação de segurança.
     */
    @ParameterizedTest
    @MethodSource("rotasComCorpoValido")
    @DisplayName("RN19_deveNegarEscritaNosCadastrosParaOPerfilDeConsulta")
    void escritaNegadaParaConsulta(String rota, String corpoValido) throws Exception {
        mockMvc.perform(post(rota)
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(Perfil.CONSULTA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValido))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("GEN-005-ACESSO_NEGADO"));

        mockMvc.perform(delete(rota + "/1")
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(Perfil.CONSULTA)))
                .andExpect(status().isForbidden());
    }

    private static Stream<Arguments> rotasComCorpoValido() {
        return Stream.of(
                Arguments.of("/api/v1/obras", """
                        {"codigo": "PERM-X", "nome": "Obra", "cidade": "Recife", "uf": "PE", "status": "ATIVA"}
                        """),
                Arguments.of("/api/v1/locadoras", """
                        {"nome": "Locadora X", "tipo": "NACIONAL", "ativa": true}
                        """),
                Arguments.of("/api/v1/condutores", """
                        {"nome": "Fulano", "cpf": "52998224725", "status": "ATIVO"}
                        """),
                Arguments.of("/api/v1/veiculos", """
                        {"placa": "ABC1D23", "modelo": "Onix", "categoria": "PASSEIO",
                         "combustivel": "FLEX", "locadoraId": 1, "possuiRastreador": false,
                         "possuiAdesivo": false, "status": "DISPONIVEL"}
                        """),
                Arguments.of("/api/v1/fornecedores", """
                        {"tipo": "OFICINA", "nome": "Oficina X", "ativo": true}
                        """),
                Arguments.of("/api/v1/tabelas-preco", """
                        {"locadoraId": 1, "anoVigencia": 2026, "grupos": []}
                        """));
    }

    @Test
    @DisplayName("RN19_deveLiberarEscritaNosCadastrosParaAdminEGestorDeFrota")
    void escritaLiberadaParaAdminEGestor() throws Exception {
        // A criação em si é validada em CadastrosIT; aqui interessa que não haja 403.
        for (Perfil perfil : new Perfil[] {Perfil.ADMIN, Perfil.GESTOR_FROTA}) {
            mockMvc.perform(post("/api/v1/obras")
                            .header("Authorization", "Bearer " + tokensPorPerfil.get(perfil))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"codigo": "PERM-%s", "nome": "Obra de permissão", "cidade": "Recife",
                                     "uf": "PE", "status": "ATIVA"}
                                    """.formatted(perfil.name())))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("RN19_e_RN20_deveNegarAcessoACredenciaisParaOPerfilDeConsulta")
    void credenciaisNegadasParaConsulta() throws Exception {
        long locadoraId = criarLocadoraComCredenciais();

        mockMvc.perform(get("/api/v1/locadoras/" + locadoraId + "/credenciais")
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(Perfil.CONSULTA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("GEN-005-ACESSO_NEGADO"));

        // O cadastro em si continua legível para CONSULTA — apenas o segredo é vedado.
        mockMvc.perform(get("/api/v1/locadoras/" + locadoraId)
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(Perfil.CONSULTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possuiCredenciais").value(true))
                .andExpect(jsonPath("$.credencialMascarada").value("••••••••"));
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"ADMIN", "GESTOR_FROTA"})
    @DisplayName("RN20_deveLiberarCredenciaisParaAdminEGestorDeFrota")
    void credenciaisLiberadasParaOperacao(Perfil perfil) throws Exception {
        long locadoraId = criarLocadoraComCredenciais();

        mockMvc.perform(get("/api/v1/locadoras/" + locadoraId + "/credenciais")
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(perfil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senha").value("123@abc"));
    }

    @Test
    @DisplayName("RN19_deveExigirAutenticacaoEmTodasAsRotasDeCadastro")
    void semTokenNaoAcessa() throws Exception {
        for (String rota : ROTAS_DE_CADASTRO) {
            mockMvc.perform(get(rota))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("GEN-004-NAO_AUTENTICADO"));
        }
    }

    private long criarLocadoraComCredenciais() throws Exception {
        String nome = "Locadora Permissões " + System.nanoTime();
        String corpo = mockMvc.perform(post("/api/v1/locadoras")
                        .header("Authorization", "Bearer " + tokensPorPerfil.get(Perfil.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "tipo": "NACIONAL", "ativa": true,
                                 "portalLogin": "proyfebrasil", "portalSenha": "123@abc"}
                                """.formatted(nome)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }
}
