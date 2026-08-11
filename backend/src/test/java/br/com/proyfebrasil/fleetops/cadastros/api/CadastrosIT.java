package br.com.proyfebrasil.fleetops.cadastros.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cadastros da Fase 1 contra PostgreSQL real.
 *
 * <p>Além dos fluxos, este teste é o que garante que o mapeamento do Hibernate e as
 * migrações V1/V2 continuam coerentes: com {@code ddl-auto=validate}, qualquer divergência
 * entre entidade e schema impede a subida do contexto e derruba a suíte inteira.
 */
@DisplayName("Cadastros — fluxos completos contra PostgreSQL real")
class CadastrosIT extends TesteDeIntegracao {

    private static final String CPF_VALIDO = "52998224725";

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

    private String tokenDeGestor;

    @BeforeEach
    void autenticarComoGestor() {
        String email = "cadastros.gestor@proyfebrasil.com.br";
        Usuario usuario = usuarios.buscarPorEmail(email)
                .orElseGet(() -> usuarios.save(new Usuario(
                        "Gestor de Cadastros", email, codificadorDeSenha.encode("SenhaDeTeste@2026"),
                        Perfil.GESTOR_FROTA)));
        tokenDeGestor = tokens.emitirAccessToken(new DadosDoToken(
                        usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getPerfil()))
                .valor();
    }

    // -----------------------------------------------------------------
    // Obra
    // -----------------------------------------------------------------

    @Test
    @DisplayName("cria, detalha e atualiza uma obra")
    void cicloDeObra() throws Exception {
        long id = criarObra("24.019", "SKER Ventos de Santa Eugênia");

        mockMvc.perform(autenticado(get("/api/v1/obras/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("24.019"))
                .andExpect(jsonPath("$.uf").value("BA"))
                .andExpect(jsonPath("$.statusDescricao").value("Ativa"));

        mockMvc.perform(autenticado(put("/api/v1/obras/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "24.019", "nome": "SKER Ventos de Santa Eugênia",
                                 "cliente": "SKER", "cidade": "Uibaí", "uf": "ba",
                                 "status": "ENCERRADA", "dataInicio": "2024-01-15", "dataFim": "2026-06-30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADA"))
                .andExpect(jsonPath("$.uf").value("BA"))
                .andExpect(jsonPath("$.cliente").value("SKER"));
    }

    @Test
    @DisplayName("recusa código de obra repetido com conflito, e não com erro interno")
    void codigoDeObraDuplicado() throws Exception {
        criarObra("24.777", "Obra Original");

        mockMvc.perform(autenticado(post("/api/v1/obras"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeObra("24.777", "Outra Obra")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAD-001-CODIGO_OBRA_DUPLICADO"));
    }

    // -----------------------------------------------------------------
    // Veículo — RN-02
    // -----------------------------------------------------------------

    @Test
    @DisplayName("RN02_deveNormalizarEGarantirUnicidadeDePlacaAtravesDaApi")
    void placaNormalizadaEUnica() throws Exception {
        long locadoraId = criarLocadora("Unidas RN02", null, null);

        String criado = mockMvc.perform(autenticado(post("/api/v1/veiculos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeVeiculo("rml-8i33", locadoraId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("RML8I33"))
                .andExpect(jsonPath("$.placaFormatada").value("RML-8I33"))
                .andExpect(jsonPath("$.exigeTesteFumacaPreta").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long veiculoId = objectMapper.readTree(criado).get("id").asLong();

        // A mesma placa em outra grafia é recusada como duplicata.
        mockMvc.perform(autenticado(post("/api/v1/veiculos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeVeiculo("RML 8I33", locadoraId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-002-PLACA_DUPLICADA"));

        // E a busca por placa encontra o veículo em qualquer grafia.
        mockMvc.perform(autenticado(get("/api/v1/veiculos/por-placa/rml8i33")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(veiculoId));

        mockMvc.perform(autenticado(get("/api/v1/veiculos").param("termo", "rml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("RN02_deveRecusarPlacaEmFormatoInvalidoComErroDeValidacao")
    void placaInvalida() throws Exception {
        long locadoraId = criarLocadora("Unidas Placa Inválida", null, null);

        mockMvc.perform(autenticado(post("/api/v1/veiculos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeVeiculo("XPTO123", locadoraId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RN-002-PLACA_INVALIDA"));
    }

    // -----------------------------------------------------------------
    // Locadora — RN-20
    // -----------------------------------------------------------------

    @Test
    @DisplayName("RN20_credenciaisNaoAparecemNoCadastroEPrecisamDeEndpointDedicado")
    void credenciaisMascaradasNoCadastro() throws Exception {
        long id = criarLocadora("Localiza RN20", "proyfebrasil", "123@abc");

        mockMvc.perform(autenticado(get("/api/v1/locadoras/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possuiCredenciais").value(true))
                .andExpect(jsonPath("$.credencialMascarada").value("••••••••"))
                .andExpect(jsonPath("$.portalLogin").doesNotExist())
                .andExpect(jsonPath("$.portalSenha").doesNotExist());

        mockMvc.perform(autenticado(get("/api/v1/locadoras/" + id + "/credenciais")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("proyfebrasil"))
                .andExpect(jsonPath("$.senha").value("123@abc"));
    }

    @Test
    @DisplayName("RN20_atualizarSemInformarSenhaPreservaACredencialGravada")
    void atualizacaoPreservaCredencial() throws Exception {
        long id = criarLocadora("Unidas Preserva", "proyfebrasil", "123@abc");

        mockMvc.perform(autenticado(put("/api/v1/locadoras/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Unidas Preserva", "tipo": "NACIONAL",
                                 "consultor": "Novo consultor", "ativa": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultor").value("Novo consultor"))
                .andExpect(jsonPath("$.possuiCredenciais").value(true));

        mockMvc.perform(autenticado(get("/api/v1/locadoras/" + id + "/credenciais")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senha").value("123@abc"));
    }

    @Test
    @DisplayName("RN20_deveResponder404AoRevelarCredencialInexistente")
    void revelacaoSemCredencial() throws Exception {
        long id = criarLocadora("Avulsa Sem Portal", null, null);

        mockMvc.perform(autenticado(get("/api/v1/locadoras/" + id + "/credenciais")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RN-020-CREDENCIAL_INDISPONIVEL"));
    }

    // -----------------------------------------------------------------
    // Condutor — RN-16
    // -----------------------------------------------------------------

    @Test
    @DisplayName("RN16_aRespostaDoCondutorTrazASituacaoDaCnhAvaliada")
    void situacaoDaCnh() throws Exception {
        String criado = mockMvc.perform(autenticado(post("/api/v1/condutores"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Ana Souza", "cpf": "529.982.247-25", "cargo": "Engenheira",
                                 "cnhNumero": "12345678900", "cnhCategoria": "ab",
                                 "cnhValidade": "2020-01-01", "status": "ATIVO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value(CPF_VALIDO))
                .andExpect(jsonPath("$.cpfFormatado").value("529.982.247-25"))
                .andExpect(jsonPath("$.cnhCategoria").value("AB"))
                .andExpect(jsonPath("$.cnhVencida").value(true))
                .andExpect(jsonPath("$.cnhEmAlerta").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(criado).get("diasParaVencerCnh").asLong()).isNegative();

        mockMvc.perform(autenticado(get("/api/v1/condutores/cnh-em-alerta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cnhVencida").value(true));
    }

    @Test
    @DisplayName("recusa CPF inválido com erro de validação de domínio")
    void cpfInvalido() throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/condutores"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Fulano", "cpf": "111.111.111-11", "status": "ATIVO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CAD-004-CPF_INVALIDO"));
    }

    // -----------------------------------------------------------------
    // Fornecedor
    // -----------------------------------------------------------------

    @Test
    @DisplayName("cadastra um posto com dias autorizados e o vincula a obras")
    void postoComDiasAutorizados() throws Exception {
        long obraId = criarObra("24.111", "Obra do Posto");

        String criado = mockMvc.perform(autenticado(post("/api/v1/fornecedores"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "POSTO", "nome": "Midas Auto Posto", "cidade": "Uibaí", "uf": "BA",
                                 "funcionamento": "24h", "ativo": true, "obrasIds": [%d],
                                 "posto": {"diasAutorizados": ["TER", "QUI", "SAB"], "acessoFaturas": "E-mail"}}
                                """.formatted(obraId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDescricao").value("Posto de combustível"))
                .andExpect(jsonPath("$.posto.semRestricaoDeDia").value(false))
                .andExpect(jsonPath("$.obras[0].codigo").value("24.111"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(criado).get("posto").get("diasAutorizados"))
                .hasSize(3);

        mockMvc.perform(autenticado(get("/api/v1/fornecedores").param("obraId", String.valueOf(obraId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("recusa dados de um tipo em fornecedor de outro tipo, em vez de descartá-los")
    void dadosIncompativeisComOTipo() throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/fornecedores"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "BORRACHARIA", "nome": "Borracharia Pai e Filho", "ativo": true,
                                 "lavaJato": {"servicosPorSemana": 1, "precoPasseio": 50.00}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CAD-006-DADOS_INCOMPATIVEIS_COM_O_TIPO"));
    }

    @Test
    @DisplayName("RN20_credenciaisDeRastreadorTambemSaoCifradasEMascaradas")
    void credenciaisDeRastreador() throws Exception {
        String criado = mockMvc.perform(autenticado(post("/api/v1/fornecedores"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "RASTREADOR", "nome": "Recife GPS", "cidade": "Recife", "uf": "PE",
                                 "ativo": true,
                                 "rastreador": {"mensalidade": 45.00, "custoInstalacao": 70.00,
                                                "custoDesinstalacao": 60.00, "equipadora": "Buda",
                                                "portalUrl": "https://sistema.getrak.com",
                                                "portalLogin": "proyfebrasil", "portalSenha": "123@abc"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rastreador.possuiCredenciais").value(true))
                .andExpect(jsonPath("$.rastreador.credencialMascarada").value("••••••••"))
                .andExpect(jsonPath("$.rastreador.portalSenha").doesNotExist())
                .andExpect(jsonPath("$.rastreador.mensalidade").value(45.00))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(criado).get("id").asLong();

        mockMvc.perform(autenticado(get("/api/v1/fornecedores/" + id + "/credenciais")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senha").value("123@abc"));
    }

    // -----------------------------------------------------------------
    // Tabela de preços — RN-14
    // -----------------------------------------------------------------

    @Test
    @DisplayName("RN14_deveResolverAVigenciaPelaCompetenciaEBloquearAnoRepetido")
    void vigenciaPorCompetencia() throws Exception {
        long locadoraId = criarLocadora("Unidas RN14", null, null);

        mockMvc.perform(autenticado(post("/api/v1/tabelas-preco"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTabela(locadoraId, 2025, "2484.00", "0.60")))
                .andExpect(status().isCreated());
        mockMvc.perform(autenticado(post("/api/v1/tabelas-preco"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTabela(locadoraId, 2026, "2606.08", "0.65")))
                .andExpect(status().isCreated());

        // A competência de 2025 continua resolvendo a tabela de 2025, não a mais recente.
        mockMvc.perform(autenticado(get("/api/v1/tabelas-preco/vigencia")
                        .param("locadoraId", String.valueOf(locadoraId))
                        .param("competencia", "2025-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anoVigencia").value(2025))
                .andExpect(jsonPath("$.grupos[0].pacotes[0].valorMensal").value(2484.00))
                .andExpect(jsonPath("$.kmExcedente[0].valorKm").value(0.60));

        mockMvc.perform(autenticado(get("/api/v1/tabelas-preco/vigencia")
                        .param("locadoraId", String.valueOf(locadoraId))
                        .param("competencia", "2026-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anoVigencia").value(2026));

        // Repetir o ano da mesma locadora é conflito.
        mockMvc.perform(autenticado(post("/api/v1/tabelas-preco"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTabela(locadoraId, 2026, "9999.00", "1.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-014-VIGENCIA_DUPLICADA"));

        // Competência sem tabela cadastrada é 404 com código próprio.
        mockMvc.perform(autenticado(get("/api/v1/tabelas-preco/vigencia")
                        .param("locadoraId", String.valueOf(locadoraId))
                        .param("competencia", "2019-01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RN-014-VIGENCIA_INEXISTENTE"));
    }

    // -----------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder autenticado(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao) {
        return requisicao.header("Authorization", "Bearer " + tokenDeGestor);
    }

    private long criarObra(String codigo, String nome) throws Exception {
        String corpo = mockMvc.perform(autenticado(post("/api/v1/obras"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeObra(codigo, nome)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private String corpoDeObra(String codigo, String nome) {
        return """
                {"codigo": "%s", "nome": "%s", "cliente": "SKER", "cidade": "Uibaí",
                 "uf": "BA", "status": "ATIVA"}
                """.formatted(codigo, nome);
    }

    private long criarLocadora(String nome, String login, String senha) throws Exception {
        String credenciais = login == null
                ? ""
                : ", \"portalLogin\": \"%s\", \"portalSenha\": \"%s\"".formatted(login, senha);
        String corpo = mockMvc.perform(autenticado(post("/api/v1/locadoras"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "tipo": "NACIONAL", "ativa": true%s}
                                """.formatted(nome, credenciais)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private String corpoDeVeiculo(String placa, long locadoraId) {
        return """
                {"placa": "%s", "modelo": "S10", "fabricante": "Chevrolet", "anoFabricacao": 2024,
                 "categoria": "QUATRO_X_QUATRO", "combustivel": "DIESEL", "locadoraId": %d,
                 "grupoTarifario": "J", "possuiRastreador": true, "fornecedorRastreador": "Recife GPS",
                 "possuiAdesivo": true, "status": "DISPONIVEL"}
                """.formatted(placa, locadoraId);
    }

    private String corpoDeTabela(long locadoraId, int ano, String valorMensal, String valorKm) {
        return """
                {"locadoraId": %d, "anoVigencia": %d,
                 "grupos": [{"codigo": "B", "veiculosDoGrupo": "Polo, Argo, Onix",
                             "categoria": "PASSEIO",
                             "pacotes": [{"pacoteKm": 3000, "valorMensal": %s}]}],
                 "kmExcedente": [{"categoria": "PASSEIO", "valorKm": %s}]}
                """.formatted(locadoraId, ano, valorMensal, valorKm);
    }
}
