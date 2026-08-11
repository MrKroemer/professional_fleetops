package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeLocadoras;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos;
import br.com.proyfebrasil.fleetops.cadastros.domain.CanaisDeAtendimento;
import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.DiaDaSemana;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDeContratos;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial do acervo: cadastros e contratos vindos das planilhas legadas.
 *
 * <p>Roda <strong>uma vez</strong>, na primeira subida de uma instalação, e só quando
 * {@code fleetops.carga-inicial.habilitada} está ligada. Em desenvolvimento a chave vem
 * ligada por padrão; em produção, desligada — o operador liga na primeira subida e desliga
 * em seguida. A idempotência por contagem é a segunda trava: com obras já cadastradas,
 * nada acontece mesmo com a chave ligada.
 *
 * <p><strong>Isto não é dado simulado.</strong> O arquivo {@code db/seed/acervo.json} é
 * gerado por {@code scripts/extrair-acervo.py} a partir dos arquivos reais da empresa em
 * {@code arquivos/} — 35 obras, 367 veículos, 270 contratos que existem de fato. É por
 * isso que a carga pode rodar em produção sem contrariar a proibição da Seção 11: o que
 * ela proíbe é inventar dados, e aqui se trata de migrar os que já existiam em planilha.
 * O seed de <em>usuários</em>, esse sim com senha conhecida, continua restrito ao perfil
 * {@code dev}.
 *
 * <p>Os dados passam pelos <strong>serviços de aplicação</strong>, e não por SQL direto:
 * a carga exercita as mesmas validações da API — placa normalizada e única (RN-02), CPF
 * conferido, credenciais cifradas (RN-20), vigência única por locadora e ano (RN-14). Uma
 * linha que violasse alguma regra aparece como recusa no log, com o motivo, e não como
 * dado inconsistente no banco. É o mesmo princípio do relatório de rejeições da RN-24,
 * cujo importador — para as planilhas que continuam chegando de fora, como multas e
 * tabelas de preço das locadoras — é entrega da Fase 5.
 */
@Component
@ConditionalOnProperty(name = "fleetops.carga-inicial.habilitada", havingValue = "true")
@Order(20)
public class CargaInicialDoAcervo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CargaInicialDoAcervo.class);
    private static final String RECURSO = "db/seed/acervo.json";

    private final ServicoDeObras obras;
    private final ServicoDeLocadoras locadoras;
    private final ServicoDeCondutores condutores;
    private final ServicoDeVeiculos veiculos;
    private final ServicoDeFornecedores fornecedores;
    private final ServicoDeTabelasDePreco tabelas;
    private final ServicoDeContratos contratos;
    private final ObraRepository repositorioDeObras;
    private final CondutorRepository repositorioDeCondutores;
    private final VeiculoRepository repositorioDeVeiculos;
    private final LocadoraRepository repositorioDeLocadoras;
    private final ObjectMapper json;
    private final Clock relogio;

    public CargaInicialDoAcervo(
            ServicoDeObras obras,
            ServicoDeLocadoras locadoras,
            ServicoDeCondutores condutores,
            ServicoDeVeiculos veiculos,
            ServicoDeFornecedores fornecedores,
            ServicoDeTabelasDePreco tabelas,
            ServicoDeContratos contratos,
            ObraRepository repositorioDeObras,
            CondutorRepository repositorioDeCondutores,
            VeiculoRepository repositorioDeVeiculos,
            LocadoraRepository repositorioDeLocadoras,
            ObjectMapper json,
            Clock relogio) {
        this.obras = obras;
        this.locadoras = locadoras;
        this.condutores = condutores;
        this.veiculos = veiculos;
        this.fornecedores = fornecedores;
        this.tabelas = tabelas;
        this.contratos = contratos;
        this.repositorioDeObras = repositorioDeObras;
        this.repositorioDeCondutores = repositorioDeCondutores;
        this.repositorioDeVeiculos = repositorioDeVeiculos;
        this.repositorioDeLocadoras = repositorioDeLocadoras;
        this.json = json;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) throws IOException {
        if (repositorioDeObras.count() > 0) {
            LOG.info("Acervo já carregado; nada a fazer.");
            return;
        }

        ClassPathResource recurso = new ClassPathResource(RECURSO);
        if (!recurso.exists()) {
            LOG.warn("Acervo {} não encontrado. Gere-o com: python3 scripts/extrair-acervo.py", RECURSO);
            return;
        }

        JsonNode raiz;
        try (InputStream entrada = recurso.getInputStream()) {
            raiz = json.readTree(entrada);
        }

        Map<String, Long> idPorCodigoDeObra = carregarObras(raiz.path("obras"));
        Map<String, Long> idPorNomeDeLocadora = carregarLocadoras(raiz.path("locadoras"));
        int totalDeCondutores = carregarCondutores(raiz.path("condutores"), idPorCodigoDeObra);
        int totalDeVeiculos = carregarVeiculos(raiz.path("veiculos"), idPorNomeDeLocadora);
        int totalDeFornecedores = carregarFornecedores(raiz.path("fornecedores"), idPorCodigoDeObra);
        int totalDeTabelas = carregarTabelas(raiz.path("tabelasDePreco"), idPorNomeDeLocadora);
        int totalDeContratos = carregarContratos(raiz.path("contratosDeLocacao"), idPorCodigoDeObra);

        LOG.info(
                "Acervo carregado: {} obras, {} locadoras, {} condutores, "
                        + "{} veículos, {} fornecedores, {} vigências de preço, {} contratos.",
                idPorCodigoDeObra.size(),
                idPorNomeDeLocadora.size(),
                totalDeCondutores,
                totalDeVeiculos,
                totalDeFornecedores,
                totalDeTabelas,
                totalDeContratos);
    }

    // -----------------------------------------------------------------

    private Map<String, Long> carregarObras(JsonNode lista) {
        Map<String, Long> ids = new java.util.LinkedHashMap<>();
        for (JsonNode item : lista) {
            String codigo = texto(item, "codigo");
            var criada = obras.criar(new ServicoDeObras.DadosDaObra(
                    codigo,
                    texto(item, "nome"),
                    texto(item, "cliente"),
                    texto(item, "cidade"),
                    texto(item, "uf"),
                    StatusObra.valueOf(texto(item, "status")),
                    null,
                    null,
                    null));
            ids.put(codigo, criada.getId());
        }
        return ids;
    }

    private Map<String, Long> carregarLocadoras(JsonNode lista) {
        Map<String, Long> ids = new java.util.LinkedHashMap<>();
        for (JsonNode item : lista) {
            JsonNode canais = item.path("canais");
            var criada = locadoras.criar(new ServicoDeLocadoras.DadosDaLocadora(
                    texto(item, "nome"),
                    TipoLocadora.valueOf(texto(item, "tipo")),
                    texto(item, "consultor"),
                    texto(item, "telefone"),
                    texto(item, "email"),
                    texto(item, "portalUrl"),
                    texto(item, "portalLogin"),
                    texto(item, "portalSenha"),
                    new CanaisDeAtendimento(
                            texto(canais, "reservas"),
                            texto(canais, "manutencao"),
                            texto(canais, "guinchoSinistro"),
                            texto(canais, "assistencia24h"),
                            texto(canais, "financeiro"),
                            texto(canais, "suporte"),
                            texto(canais, "telemetria")),
                    texto(item, "observacoes"),
                    item.path("ativa").asBoolean(true)));
            ids.put(criada.getNome(), criada.getId());
        }
        return ids;
    }

    private int carregarCondutores(JsonNode lista, Map<String, Long> obrasPorCodigo) {
        LocalDate hoje = LocalDate.now(relogio);
        int total = 0;
        for (JsonNode item : lista) {
            // O deslocamento vem do extrator e distribui vencimentos ao redor de hoje,
            // para que a faixa de alerta da RN-16 tenha casos reais para exibir.
            LocalDate validade = hoje.plusDays(item.path("cnhValidadeDeslocamentoEmDias").asLong());
            condutores.criar(new ServicoDeCondutores.DadosDoCondutor(
                    texto(item, "nome"),
                    texto(item, "cpf"),
                    texto(item, "cargo"),
                    null,
                    null,
                    null,
                    texto(item, "cnhCategoria"),
                    validade,
                    obrasPorCodigo.get(texto(item, "obraCodigo")),
                    StatusCondutor.valueOf(texto(item, "status")),
                    null));
            total++;
        }
        return total;
    }

    private int carregarVeiculos(JsonNode lista, Map<String, Long> locadorasPorNome) {
        int total = 0;
        for (JsonNode item : lista) {
            Long locadoraId = locadorasPorNome.get(texto(item, "locadoraNome"));
            if (locadoraId == null) {
                continue;
            }
            veiculos.criar(new ServicoDeVeiculos.DadosDoVeiculo(
                    texto(item, "placa"),
                    texto(item, "modelo"),
                    texto(item, "fabricante"),
                    null,
                    CategoriaVeiculo.valueOf(texto(item, "categoria")),
                    Combustivel.valueOf(texto(item, "combustivel")),
                    locadoraId,
                    texto(item, "grupoTarifario"),
                    texto(item, "codigoInterno"),
                    item.path("possuiRastreador").asBoolean(false),
                    texto(item, "fornecedorRastreador"),
                    item.path("possuiAdesivo").asBoolean(false),
                    StatusVeiculo.valueOf(texto(item, "status")),
                    null));
            total++;
        }
        return total;
    }

    private int carregarFornecedores(JsonNode lista, Map<String, Long> obrasPorCodigo) {
        int total = 0;
        for (JsonNode item : lista) {
            TipoFornecedor tipo = TipoFornecedor.valueOf(texto(item, "tipo"));
            List<Long> obrasIds = new ArrayList<>();
            for (JsonNode codigo : item.path("obrasCodigos")) {
                Long id = obrasPorCodigo.get(codigo.asText());
                if (id != null && !obrasIds.contains(id)) {
                    obrasIds.add(id);
                }
            }

            fornecedores.criar(new ServicoDeFornecedores.DadosDoFornecedor(
                    tipo,
                    texto(item, "nome"),
                    texto(item, "cidade"),
                    texto(item, "uf"),
                    texto(item, "endereco"),
                    texto(item, "telefone"),
                    texto(item, "email"),
                    texto(item, "responsavel"),
                    texto(item, "funcionamento"),
                    texto(item, "formaFaturamento"),
                    texto(item, "formaPagamento"),
                    null,
                    item.path("ativo").asBoolean(true),
                    texto(item, "observacoes"),
                    obrasIds,
                    dadosDePosto(item.path("posto")),
                    dadosDeLavaJato(item.path("lavaJato")),
                    dadosDeRastreador(item.path("rastreador")),
                    dadosDeGrafica(item.path("grafica"))));
            total++;
        }
        return total;
    }

    private ServicoDeFornecedores.DadosDePosto dadosDePosto(JsonNode no) {
        if (no.isMissingNode() || no.isNull()) {
            return null;
        }
        Set<DiaDaSemana> dias = new LinkedHashSet<>();
        for (JsonNode dia : no.path("diasAutorizados")) {
            dias.add(DiaDaSemana.valueOf(dia.asText()));
        }
        return new ServicoDeFornecedores.DadosDePosto(dias, texto(no, "acessoFaturas"));
    }

    private ServicoDeFornecedores.DadosDeLavaJato dadosDeLavaJato(JsonNode no) {
        if (no.isMissingNode() || no.isNull()) {
            return null;
        }
        return new ServicoDeFornecedores.DadosDeLavaJato(
                no.path("servicosPorSemana").isNumber() ? no.path("servicosPorSemana").asInt() : null,
                decimal(no, "precoPasseio"),
                decimal(no, "precoSuv"),
                decimal(no, "precoQuatroXQuatro"));
    }

    private ServicoDeFornecedores.DadosDeRastreador dadosDeRastreador(JsonNode no) {
        if (no.isMissingNode() || no.isNull()) {
            return null;
        }
        return new ServicoDeFornecedores.DadosDeRastreador(
                decimal(no, "mensalidade"),
                decimal(no, "custoInstalacao"),
                decimal(no, "custoDesinstalacao"),
                texto(no, "equipadora"),
                texto(no, "portalUrl"),
                texto(no, "portalLogin"),
                texto(no, "portalSenha"));
    }

    private ServicoDeFornecedores.DadosDeGrafica dadosDeGrafica(JsonNode no) {
        if (no.isMissingNode() || no.isNull()) {
            return null;
        }
        return new ServicoDeFornecedores.DadosDeGrafica(
                texto(no, "tamanhoAdesivo"),
                decimal(no, "precoAdesivo"),
                texto(no, "tamanhoIma"),
                decimal(no, "precoIma"));
    }

    private int carregarTabelas(JsonNode lista, Map<String, Long> locadorasPorNome) {
        int total = 0;
        for (JsonNode item : lista) {
            Long locadoraId = locadorasPorNome.get(texto(item, "locadoraNome"));
            if (locadoraId == null) {
                continue;
            }

            List<ServicoDeTabelasDePreco.DadosDoGrupo> grupos = new ArrayList<>();
            for (JsonNode grupo : item.path("grupos")) {
                List<ServicoDeTabelasDePreco.DadosDoPacote> pacotes = new ArrayList<>();
                for (JsonNode pacote : grupo.path("pacotes")) {
                    pacotes.add(new ServicoDeTabelasDePreco.DadosDoPacote(
                            pacote.path("pacoteKm").asInt(), decimal(pacote, "valorMensal")));
                }
                grupos.add(new ServicoDeTabelasDePreco.DadosDoGrupo(
                        texto(grupo, "codigo"),
                        texto(grupo, "veiculosDoGrupo"),
                        CategoriaVeiculo.valueOf(texto(grupo, "categoria")),
                        pacotes));
            }

            List<ServicoDeTabelasDePreco.DadosDoKmExcedente> excedentes = new ArrayList<>();
            for (JsonNode excedente : item.path("kmExcedente")) {
                JsonNode pacote = excedente.path("pacoteKm");
                excedentes.add(new ServicoDeTabelasDePreco.DadosDoKmExcedente(
                        CategoriaVeiculo.valueOf(texto(excedente, "categoria")),
                        pacote.isNumber() ? pacote.asInt() : null,
                        decimal(excedente, "valorKm")));
            }

            tabelas.criar(new ServicoDeTabelasDePreco.DadosDaTabela(
                    locadoraId, item.path("anoVigencia").asInt(), null, grupos, excedentes));
            total++;
        }
        return total;
    }

    /**
     * Carrega os contratos de locação: o vínculo obra ↔ condutor ↔ veículo.
     *
     * <p>É o que faz a frota deixar de ser uma lista de placas e virar operação: sem o
     * contrato não há como dizer quem dirige o quê e em qual obra.
     */
    private int carregarContratos(JsonNode lista, Map<String, Long> obrasPorCodigo) {
        int total = 0;
        for (JsonNode item : lista) {
            Long obraId = obrasPorCodigo.get(texto(item, "obraCodigo"));
            var veiculo = repositorioDeVeiculos.buscarPorPlaca(texto(item, "placa"));
            if (obraId == null || veiculo.isEmpty()) {
                continue;
            }
            var obra = repositorioDeObras.buscarPorId(obraId);
            var locadora = repositorioDeLocadoras.buscarPorId(veiculo.get().getLocadora().getId());
            if (obra.isEmpty() || locadora.isEmpty()) {
                continue;
            }

            String nomeDoCondutor = texto(item, "condutorNome");
            Condutor condutor = nomeDoCondutor == null
                    ? null
                    : repositorioDeCondutores.buscarPorNome(nomeDoCondutor).orElse(null);

            var resultado = contratos.abrir(new ServicoDeContratos.DadosDoContrato(
                    obra.get(),
                    locadora.get(),
                    veiculo.get(),
                    condutor,
                    texto(item, "obraCodigo"),
                    texto(item, "localRetirada"),
                    data(item, "dataRetirada"),
                    data(item, "dataEncerramento"),
                    item.path("pacoteKmContratado").isNumber()
                            ? item.path("pacoteKmContratado").asInt()
                            : null,
                    null,
                    StatusContrato.valueOf(texto(item, "status")),
                    texto(item, "observacoes"),
                    trocasDoContrato(item.path("substituicoes"))));

            for (String recusada : resultado.recusadas()) {
                LOG.warn("Substituição descartada no contrato {}: {}", resultado.contrato().getId(), recusada);
            }
            total++;
        }
        return total;
    }

    /**
     * Converte as trocas da planilha para a abertura do contrato (RN-01, RN-18).
     *
     * <p>Elas vão junto da abertura, e não em chamadas seguintes, porque a ordem é parte
     * da regra: o contrato precisa receber todo o seu histórico de veículos antes de ser
     * encerrado. Encerrar primeiro fecharia o período corrente na data de devolução, e a
     * troca seguinte abriria um período por cima do intervalo já fechado.
     *
     * <p>Placas que não existem no cadastro são omitidas aqui; datas incoerentes são
     * recusadas pelo serviço, que devolve o motivo para o log — o embrião do relatório de
     * rejeições da RN-24.
     */
    private List<ServicoDeContratos.TrocaDeVeiculo> trocasDoContrato(JsonNode substituicoes) {
        List<ServicoDeContratos.TrocaDeVeiculo> trocas = new ArrayList<>();
        for (JsonNode troca : substituicoes) {
            String placa = texto(troca, "placa");
            LocalDate quando = data(troca, "data");
            if (placa == null || quando == null) {
                continue;
            }
            repositorioDeVeiculos.buscarPorPlaca(placa).ifPresent(veiculo -> trocas.add(
                    new ServicoDeContratos.TrocaDeVeiculo(
                            veiculo, quando, "Substituição registrada na planilha")));
        }
        return trocas;
    }

    private static LocalDate data(JsonNode no, String campo) {
        String valor = texto(no, campo);
        return valor == null ? null : LocalDate.parse(valor);
    }

    /** Texto do campo, tratando ausência e {@code null} do JSON como ausência de valor. */
    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() || valor.asText().isBlank()
                ? null
                : valor.asText();
    }

    private static BigDecimal decimal(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isNumber() ? valor.decimalValue() : null;
    }
}
