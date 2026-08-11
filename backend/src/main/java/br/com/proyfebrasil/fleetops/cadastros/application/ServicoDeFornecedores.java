package br.com.proyfebrasil.fleetops.cadastros.application;

import static br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras.curinga;

import br.com.proyfebrasil.fleetops.cadastros.domain.DadosGrafica;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosLavaJato;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosPosto;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosRastreador;
import br.com.proyfebrasil.fleetops.cadastros.domain.DiaDaSemana;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.cadastros.infra.FornecedorRepository;
import br.com.proyfebrasil.fleetops.cadastros.infra.ObraRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import br.com.proyfebrasil.fleetops.shared.security.CriptografiaCredenciais;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de fornecedores credenciados.
 *
 * <p>Cada tipo aceita um conjunto próprio de dados complementares. O serviço recusa
 * combinações incoerentes — enviar preços de lava-jato para uma borracharia, por
 * exemplo — em vez de ignorá-las silenciosamente, porque um dado descartado sem aviso
 * reaparece depois como "o sistema perdeu minha informação".
 */
@Service
public class ServicoDeFornecedores {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeFornecedores.class);

    private final FornecedorRepository fornecedores;
    private final ObraRepository obras;
    private final CriptografiaCredenciais criptografia;

    public ServicoDeFornecedores(
            FornecedorRepository fornecedores, ObraRepository obras, CriptografiaCredenciais criptografia) {
        this.fornecedores = fornecedores;
        this.obras = obras;
        this.criptografia = criptografia;
    }

    /** Dados de posto: dias em que o abastecimento é autorizado (RN-04). */
    public record DadosDePosto(Set<DiaDaSemana> diasAutorizados, String acessoFaturas) {
    }

    /** Dados de lava-jato: frequência permitida (RN-05) e preços por categoria. */
    public record DadosDeLavaJato(
            Integer servicosPorSemana,
            BigDecimal precoPasseio,
            BigDecimal precoSuv,
            BigDecimal precoQuatroXQuatro) {
    }

    /**
     * Dados de rastreador.
     *
     * @param portalLogin login em claro; {@code null} preserva o atual
     * @param portalSenha senha em claro; {@code null} preserva a atual
     */
    public record DadosDeRastreador(
            BigDecimal mensalidade,
            BigDecimal custoInstalacao,
            BigDecimal custoDesinstalacao,
            String equipadora,
            String portalUrl,
            String portalLogin,
            String portalSenha) {
    }

    /** Dados de gráfica: tamanhos e preços de adesivo e imã. */
    public record DadosDeGrafica(
            String tamanhoAdesivo, BigDecimal precoAdesivo, String tamanhoIma, BigDecimal precoIma) {
    }

    /** Dados de criação ou atualização de um fornecedor. */
    public record DadosDoFornecedor(
            TipoFornecedor tipo,
            String nome,
            String cidade,
            String uf,
            String endereco,
            String telefone,
            String email,
            String responsavel,
            String funcionamento,
            String formaFaturamento,
            String formaPagamento,
            LocalDate credenciadoEm,
            boolean ativo,
            String observacoes,
            List<Long> obrasIds,
            DadosDePosto posto,
            DadosDeLavaJato lavaJato,
            DadosDeRastreador rastreador,
            DadosDeGrafica grafica) {
    }

    /** Filtro de listagem de fornecedores. */
    public record FiltroFornecedor(String termo, TipoFornecedor tipo, Boolean ativo, Long obraId) {
    }

    /** Credencial revelada sob solicitação explícita e auditada (RN-20). */
    public record CredencialRevelada(String login, String senha) {
    }

    @Transactional(readOnly = true)
    public Page<Fornecedor> listar(FiltroFornecedor filtro, Pageable paginacao) {
        Page<Fornecedor> pagina = fornecedores.pesquisar(
                curinga(filtro.termo()), filtro.tipo(), filtro.ativo(), filtro.obraId(), paginacao);
        pagina.getContent().forEach(ServicoDeFornecedores::inicializarAssociacoes);
        return pagina;
    }

    @Transactional(readOnly = true)
    public Fornecedor buscar(Long id) {
        return fornecedores.buscarDetalhado(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", id));
    }

    /**
     * Carrega obras e dados específicos dentro da transação.
     *
     * <p>Com {@code open-in-view=false}, o mapeamento para DTO ocorre com a sessão já
     * fechada; sem esta inicialização o acesso preguiçoso falharia. O {@code @BatchSize}
     * nas coleções mantém o custo em poucas consultas, em vez de uma por linha.
     */
    private static void inicializarAssociacoes(Fornecedor fornecedor) {
        Hibernate.initialize(fornecedor.getObras());
        Hibernate.initialize(fornecedor.getDadosPosto());
        Hibernate.initialize(fornecedor.getDadosLavaJato());
        Hibernate.initialize(fornecedor.getDadosRastreador());
        Hibernate.initialize(fornecedor.getDadosGrafica());
    }

    @Transactional
    public Fornecedor criar(DadosDoFornecedor dados) {
        garantirNomeDisponivel(dados.nome(), dados.tipo(), null);
        recusarDadosDeOutroTipo(dados);
        Fornecedor fornecedor = new Fornecedor(dados.tipo(), dados.nome());
        aplicarDadosComuns(fornecedor, dados);
        aplicarDadosEspecificos(fornecedor, dados);
        return fornecedores.save(fornecedor);
    }

    @Transactional
    public Fornecedor atualizar(Long id, DadosDoFornecedor dados) {
        Fornecedor fornecedor = buscar(id);
        if (dados.tipo() != null && dados.tipo() != fornecedor.getTipo()) {
            throw new NegocioException(
                    ErroCadastro.DADOS_INCOMPATIVEIS_COM_O_TIPO,
                    "O tipo de um fornecedor não pode ser alterado. Cadastre um novo registro.",
                    Map.of("tipoAtual", fornecedor.getTipo().name(), "tipoInformado", dados.tipo().name()));
        }
        garantirNomeDisponivel(dados.nome(), fornecedor.getTipo(), id);
        recusarDadosDeOutroTipo(dados);
        aplicarDadosComuns(fornecedor, dados);
        aplicarDadosEspecificos(fornecedor, dados);
        return fornecedor;
    }

    @Transactional
    public void excluir(Long id) {
        Fornecedor fornecedor = buscar(id);
        if (fornecedor.getDadosRastreador() != null) {
            fornecedor.getDadosRastreador().limparCredenciais();
        }
        fornecedor.excluir(Instant.now());
    }

    /**
     * Revela as credenciais do portal de telemetria em claro (RN-20).
     *
     * @throws NegocioException se o fornecedor não for rastreador ou não tiver credenciais
     */
    @Transactional(readOnly = true)
    public CredencialRevelada revelarCredenciais(Long id, String solicitante) {
        Fornecedor fornecedor = buscar(id);
        DadosRastreador dados = fornecedor.getDadosRastreador();
        if (dados == null || !dados.possuiCredenciais()) {
            throw new NegocioException(
                    ErroCadastro.CREDENCIAL_INDISPONIVEL,
                    "Este fornecedor não tem credenciais de portal cadastradas.");
        }
        LOG.warn("Credenciais do fornecedor {} ({}) reveladas para {}",
                fornecedor.getId(), fornecedor.getNome(), solicitante);
        return new CredencialRevelada(
                criptografia.decifrar(dados.getPortalLoginCifrado()),
                criptografia.decifrar(dados.getPortalSenhaCifrada()));
    }

    // -----------------------------------------------------------------

    private void aplicarDadosComuns(Fornecedor fornecedor, DadosDoFornecedor dados) {
        fornecedor.alterarDadosBasicos(
                dados.nome(),
                dados.cidade(),
                dados.uf(),
                dados.endereco(),
                dados.telefone(),
                dados.email(),
                dados.responsavel());
        fornecedor.alterarCondicoesComerciais(
                dados.funcionamento(), dados.formaFaturamento(), dados.formaPagamento(), dados.credenciadoEm());
        fornecedor.definirObras(resolverObras(dados.obrasIds()));
        fornecedor.alterarObservacoes(dados.observacoes());
        if (dados.ativo()) {
            fornecedor.ativar();
        } else {
            fornecedor.desativar();
        }
    }

    private void aplicarDadosEspecificos(Fornecedor fornecedor, DadosDoFornecedor dados) {
        switch (fornecedor.getTipo()) {
            case POSTO -> aplicarPosto(fornecedor, dados.posto());
            case LAVA_JATO -> aplicarLavaJato(fornecedor, dados.lavaJato());
            case RASTREADOR -> aplicarRastreador(fornecedor, dados.rastreador());
            case GRAFICA -> aplicarGrafica(fornecedor, dados.grafica());
            case BORRACHARIA, PARA_BRISAS, OFICINA -> {
                // Tipos sem dados complementares: nada a fazer.
            }
            default -> throw new IllegalStateException("Tipo de fornecedor não tratado: " + fornecedor.getTipo());
        }
    }

    private void aplicarPosto(Fornecedor fornecedor, DadosDePosto dados) {
        DadosDePosto valores = dados == null ? new DadosDePosto(Set.of(), null) : dados;
        DadosPosto existente = fornecedor.getDadosPosto();
        if (existente == null) {
            fornecedor.definirDadosPosto(new DadosPosto(valores.diasAutorizados(), valores.acessoFaturas()));
        } else {
            existente.definirDiasAutorizados(valores.diasAutorizados());
            existente.definirAcessoFaturas(valores.acessoFaturas());
        }
    }

    private void aplicarLavaJato(Fornecedor fornecedor, DadosDeLavaJato dados) {
        DadosDeLavaJato valores = dados == null
                ? new DadosDeLavaJato(DadosLavaJato.SERVICOS_POR_SEMANA_PADRAO, null, null, null)
                : dados;
        int frequencia = valores.servicosPorSemana() == null
                ? DadosLavaJato.SERVICOS_POR_SEMANA_PADRAO
                : valores.servicosPorSemana();
        DadosLavaJato existente = fornecedor.getDadosLavaJato();
        if (existente == null) {
            fornecedor.definirDadosLavaJato(new DadosLavaJato(
                    frequencia, valores.precoPasseio(), valores.precoSuv(), valores.precoQuatroXQuatro()));
        } else {
            existente.definirFrequencia(frequencia);
            existente.definirPrecos(valores.precoPasseio(), valores.precoSuv(), valores.precoQuatroXQuatro());
        }
    }

    private void aplicarRastreador(Fornecedor fornecedor, DadosDeRastreador dados) {
        DadosDeRastreador valores =
                dados == null ? new DadosDeRastreador(null, null, null, null, null, null, null) : dados;
        DadosRastreador existente = fornecedor.getDadosRastreador();
        if (existente == null) {
            existente = new DadosRastreador(
                    valores.mensalidade(),
                    valores.custoInstalacao(),
                    valores.custoDesinstalacao(),
                    valores.equipadora(),
                    valores.portalUrl());
            fornecedor.definirDadosRastreador(existente);
        } else {
            existente.definirCustos(
                    valores.mensalidade(), valores.custoInstalacao(), valores.custoDesinstalacao());
            existente.definirEquipadora(valores.equipadora(), valores.portalUrl());
        }
        existente.definirCredenciaisCifradas(
                cifrarSePresente(valores.portalLogin()), cifrarSePresente(valores.portalSenha()));
    }

    private void aplicarGrafica(Fornecedor fornecedor, DadosDeGrafica dados) {
        DadosDeGrafica valores = dados == null ? new DadosDeGrafica(null, null, null, null) : dados;
        DadosGrafica existente = fornecedor.getDadosGrafica();
        if (existente == null) {
            fornecedor.definirDadosGrafica(new DadosGrafica(
                    valores.tamanhoAdesivo(), valores.precoAdesivo(), valores.tamanhoIma(), valores.precoIma()));
        } else {
            existente.definirTamanhos(valores.tamanhoAdesivo(), valores.tamanhoIma());
            existente.definirPrecos(valores.precoAdesivo(), valores.precoIma());
        }
    }

    /**
     * Recusa dados complementares que não pertencem ao tipo informado.
     *
     * <p>Aceitar e descartar em silêncio faria o usuário acreditar que gravou preços de
     * lava-jato em uma borracharia — e só descobrir a perda meses depois.
     */
    private void recusarDadosDeOutroTipo(DadosDoFornecedor dados) {
        TipoFornecedor tipo = dados.tipo();
        if (tipo == null) {
            return;
        }
        rejeitarSeIncompativel(tipo, TipoFornecedor.POSTO, dados.posto() != null, "de posto");
        rejeitarSeIncompativel(tipo, TipoFornecedor.LAVA_JATO, dados.lavaJato() != null, "de lava-jato");
        rejeitarSeIncompativel(tipo, TipoFornecedor.RASTREADOR, dados.rastreador() != null, "de rastreador");
        rejeitarSeIncompativel(tipo, TipoFornecedor.GRAFICA, dados.grafica() != null, "de gráfica");
    }

    private void rejeitarSeIncompativel(
            TipoFornecedor tipoInformado, TipoFornecedor tipoEsperado, boolean presente, String rotulo) {
        if (presente && tipoInformado != tipoEsperado) {
            throw new NegocioException(
                    ErroCadastro.DADOS_INCOMPATIVEIS_COM_O_TIPO,
                    "Dados %s não se aplicam a um fornecedor do tipo %s."
                            .formatted(rotulo, tipoInformado.getDescricao()),
                    Map.of("tipo", tipoInformado.name(), "dadosRecebidos", rotulo));
        }
    }

    private Set<Obra> resolverObras(List<Long> ids) {
        Set<Obra> resolvidas = new LinkedHashSet<>();
        if (ids == null) {
            return resolvidas;
        }
        for (Long obraId : ids) {
            resolvidas.add(obras.buscarPorId(obraId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Obra", obraId)));
        }
        return resolvidas;
    }

    private String cifrarSePresente(String valorEmClaro) {
        if (valorEmClaro == null) {
            return null;
        }
        return valorEmClaro.isBlank() ? "" : criptografia.cifrar(valorEmClaro);
    }

    private void garantirNomeDisponivel(String nome, TipoFornecedor tipo, Long idAtual) {
        if (fornecedores.existeOutroComNomeETipo(nome == null ? null : nome.trim(), tipo, idAtual)) {
            throw new NegocioException(
                    ErroCadastro.FORNECEDOR_DUPLICADO,
                    "Já existe um fornecedor deste tipo com o mesmo nome.",
                    Map.of("nome", String.valueOf(nome), "tipo", tipo.name()));
        }
    }
}
