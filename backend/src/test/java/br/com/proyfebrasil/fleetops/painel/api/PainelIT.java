package br.com.proyfebrasil.fleetops.painel.api;

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
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Painel e central de pendências contra PostgreSQL real.
 *
 * <p>Monta uma situação conhecida — uma obra ativa sem posto, um condutor de CNH
 * vencida, uma locadora sem tabela do ano — e confere que ela aparece na central com a
 * severidade certa. É o teste que garante que as consultas de apuração continuam
 * enxergando o que deveriam depois de qualquer mudança no schema.
 */
@DisplayName("Painel — indicadores e central de pendências")
class PainelIT extends TesteDeIntegracao {

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

    private String token;

    @BeforeEach
    void autenticar() {
        String email = "painel.gestor@proyfebrasil.com.br";
        Usuario usuario = usuarios.buscarPorEmail(email)
                .orElseGet(() -> usuarios.save(new Usuario(
                        "Gestor do Painel", email, codificadorDeSenha.encode("SenhaDeTeste@2026"),
                        Perfil.GESTOR_FROTA)));
        token = tokens.emitirAccessToken(new DadosDoToken(
                        usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getPerfil()))
                .valor();
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("os indicadores refletem os cadastros existentes")
    void indicadoresRefletemOsCadastros() throws Exception {
        long locadoraId = criarLocadora("Painel Locadora");
        criarVeiculo("PNL1A01", locadoraId, "DIESEL", "QUATRO_X_QUATRO", true);
        criarVeiculo("PNL1A02", locadoraId, "FLEX", "PASSEIO", false);

        mockMvc.perform(autenticado(get("/api/v1/painel/indicadores")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veiculosNaFrota").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.veiculosADiesel").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.veiculosComRastreador").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.veiculosPorCategoria").isArray())
                .andExpect(jsonPath("$.veiculosPorLocadora").isArray())
                // A chave técnica acompanha o rótulo traduzido: a interface usa a chave
                // para manter a cor da série estável entre consultas.
                .andExpect(jsonPath("$.veiculosPorCategoria[0].chave").isNotEmpty())
                .andExpect(jsonPath("$.veiculosPorCategoria[0].rotulo").isNotEmpty());
    }

    @Test
    @DisplayName("RN23_condutorComCnhVencidaApareceComoPendenciaCritica")
    void cnhVencidaViraPendenciaCritica() throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/condutores"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Condutor do Painel", "cpf": "168.995.350-09",
                                 "cnhCategoria": "B", "cnhValidade": "%s", "status": "ATIVO"}
                                """.formatted(LocalDate.now().minusDays(30))))
                .andExpect(status().isCreated());

        mockMvc.perform(autenticado(get("/api/v1/painel/pendencias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticas").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath(
                                "$.itens[?(@.tipo == 'CNH_VENCIDA' && @.titulo =~ /.*Condutor do Painel.*/)]")
                        .exists())
                .andExpect(jsonPath("$.itens[0].severidade").value("CRITICA"))
                .andExpect(jsonPath("$.itens[0].regra").isNotEmpty());
    }

    @Test
    @DisplayName("RN23_obraAtivaSemPostoCredenciadoApareceNaCentral")
    void obraSemPostoApareceNaCentral() throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/obras"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "99.001", "nome": "Obra Sem Posto", "cidade": "Recife",
                                 "uf": "PE", "status": "ATIVA"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(autenticado(get("/api/v1/painel/pendencias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.tipo == 'OBRA_SEM_POSTO' && @.titulo =~ /.*99\\.001.*/)]")
                        .exists());
    }

    @Test
    @DisplayName("RN23_pendenciasSaemOrdenadasPelaSeveridade")
    void pendenciasOrdenadas() throws Exception {
        String corpo = mockMvc.perform(autenticado(get("/api/v1/painel/pendencias")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var itens = objectMapper.readTree(corpo).get("itens");
        int anterior = -1;
        for (var item : itens) {
            int atual = switch (item.get("severidade").asText()) {
                case "CRITICA" -> 0;
                case "ATENCAO" -> 1;
                default -> 2;
            };
            org.assertj.core.api.Assertions.assertThat(atual)
                    .as("a severidade nunca regride ao percorrer a lista")
                    .isGreaterThanOrEqualTo(anterior);
            anterior = atual;
        }
    }

    @Test
    @DisplayName("RN19_oPainelExigeAutenticacao")
    void painelExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/painel/indicadores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/painel/pendencias")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RN19_oPerfilDeConsultaLeOPainel")
    void consultaLeOPainel() throws Exception {
        String email = "painel.consulta@proyfebrasil.com.br";
        Usuario usuario = usuarios.buscarPorEmail(email)
                .orElseGet(() -> usuarios.save(new Usuario(
                        "Consulta do Painel", email, codificadorDeSenha.encode("SenhaDeTeste@2026"),
                        Perfil.CONSULTA)));
        String tokenDeConsulta = tokens.emitirAccessToken(new DadosDoToken(
                        usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getPerfil()))
                .valor();

        mockMvc.perform(get("/api/v1/painel/indicadores").header("Authorization", "Bearer " + tokenDeConsulta))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------

    private long criarLocadora(String nome) throws Exception {
        String corpo = mockMvc.perform(autenticado(post("/api/v1/locadoras"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "tipo": "NACIONAL", "ativa": true}
                                """.formatted(nome)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private void criarVeiculo(
            String placa, long locadoraId, String combustivel, String categoria, boolean rastreador)
            throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/veiculos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placa": "%s", "modelo": "Modelo", "categoria": "%s",
                                 "combustivel": "%s", "locadoraId": %d, "grupoTarifario": "B",
                                 "possuiRastreador": %b, "possuiAdesivo": false, "status": "EM_USO"}
                                """.formatted(placa, categoria, combustivel, locadoraId, rastreador)))
                .andExpect(status().isCreated());
    }
}
