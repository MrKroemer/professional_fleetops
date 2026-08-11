package br.com.proyfebrasil.fleetops.cadastros.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.GrupoTarifario;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.TabelaPreco;
import br.com.proyfebrasil.fleetops.cadastros.infra.LocadoraRepository;
import br.com.proyfebrasil.fleetops.cadastros.infra.TabelaPrecoRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabelas de preço de locação, uma por locadora e ano de vigência (RN-14).
 *
 * <p>A grade de grupos e pacotes é substituída por inteiro a cada atualização. Editar
 * item a item seria mais econômico, mas as tabelas chegam da locadora como uma planilha
 * completa; reconstruir a partir dela evita deixar resíduos de uma vigência anterior
 * misturados com a nova.
 */
@Service
public class ServicoDeTabelasDePreco {

    private final TabelaPrecoRepository tabelas;
    private final LocadoraRepository locadoras;

    public ServicoDeTabelasDePreco(TabelaPrecoRepository tabelas, LocadoraRepository locadoras) {
        this.tabelas = tabelas;
        this.locadoras = locadoras;
    }

    /**
     * Um pacote de quilometragem e seu valor mensal.
     *
     * @param pacoteKm    franquia mensal contratada, ex.: 3000
     * @param valorMensal valor da locação para esse pacote
     */
    public record DadosDoPacote(int pacoteKm, BigDecimal valorMensal) {
    }

    /** Um grupo tarifário e seus pacotes. */
    public record DadosDoGrupo(
            String codigo, String veiculosDoGrupo, CategoriaVeiculo categoria, List<DadosDoPacote> pacotes) {
    }

    /**
     * Preço de KM excedente.
     *
     * @param pacoteKm pacote específico; {@code null} aplica o valor a todos os pacotes
     */
    public record DadosDoKmExcedente(CategoriaVeiculo categoria, Integer pacoteKm, BigDecimal valorKm) {
    }

    /** Dados de criação ou atualização de uma vigência. */
    public record DadosDaTabela(
            Long locadoraId,
            int anoVigencia,
            String observacoes,
            List<DadosDoGrupo> grupos,
            List<DadosDoKmExcedente> kmExcedente) {
    }

    /** Filtro de listagem de vigências. */
    public record FiltroTabela(Long locadoraId, Integer ano) {
    }

    @Transactional(readOnly = true)
    public Page<TabelaPreco> listar(FiltroTabela filtro, Pageable paginacao) {
        Page<TabelaPreco> pagina = tabelas.pesquisar(filtro.locadoraId(), filtro.ano(), paginacao);
        pagina.getContent().forEach(ServicoDeTabelasDePreco::inicializarGrade);
        return pagina;
    }

    @Transactional(readOnly = true)
    public TabelaPreco buscar(Long id) {
        TabelaPreco tabela = tabelas.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tabela de preços", id));
        inicializarGrade(tabela);
        return tabela;
    }

    /**
     * Localiza a vigência aplicável a uma competência (RN-14).
     *
     * <p>Usa o ano da competência do lançamento, não o ano corrente: reprocessar o
     * fechamento de março de 2025 em 2026 tem de reproduzir os preços de 2025.
     *
     * @throws NegocioException com {@link ErroCadastro#VIGENCIA_INEXISTENTE} quando a
     *     locadora não tem tabela cadastrada para aquele ano
     */
    @Transactional(readOnly = true)
    public TabelaPreco vigenciaPara(Long locadoraId, YearMonth competencia) {
        TabelaPreco vigencia = tabelas.buscarVigencia(locadoraId, competencia.getYear())
                .orElseThrow(() -> new NegocioException(
                        ErroCadastro.VIGENCIA_INEXISTENTE,
                        "Não há tabela de preços da locadora para a vigência de %d.".formatted(competencia.getYear()),
                        Map.of("locadoraId", locadoraId, "ano", competencia.getYear())));
        inicializarGrade(vigencia);
        return vigencia;
    }

    /** Valor mensal contratado para um grupo e pacote, na vigência da competência. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> valorMensal(
            Long locadoraId, YearMonth competencia, String codigoDoGrupo, int pacoteKm) {
        return vigenciaPara(locadoraId, competencia).valorMensal(codigoDoGrupo, pacoteKm);
    }

    /** Valor do KM excedente aplicável na vigência da competência (RN-06). */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> valorKmExcedente(
            Long locadoraId, YearMonth competencia, CategoriaVeiculo categoria, int pacoteKm) {
        return vigenciaPara(locadoraId, competencia).valorKmExcedente(categoria, pacoteKm);
    }

    @Transactional
    public TabelaPreco criar(DadosDaTabela dados) {
        Locadora locadora = resolverLocadora(dados.locadoraId());
        garantirVigenciaDisponivel(locadora.getId(), dados.anoVigencia(), null);
        TabelaPreco tabela = new TabelaPreco(locadora, dados.anoVigencia());
        aplicar(tabela, dados);
        return tabelas.save(tabela);
    }

    @Transactional
    public TabelaPreco atualizar(Long id, DadosDaTabela dados) {
        TabelaPreco tabela = buscar(id);
        garantirVigenciaDisponivel(tabela.getLocadora().getId(), dados.anoVigencia(), id);
        tabela.alterarVigencia(dados.anoVigencia());
        aplicar(tabela, dados);
        return tabela;
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).excluir(Instant.now());
    }

    /**
     * Carrega a grade dentro da transação.
     *
     * <p>A aplicação roda com {@code open-in-view=false}: a sessão fecha ao sair do
     * serviço, e o mapeamento para DTO acontece depois disso. Sem esta inicialização,
     * o acesso preguiçoso a grupos e pacotes estouraria fora da sessão. Um
     * {@code join fetch} não serviria aqui porque coleções somadas à paginação
     * obrigariam o Hibernate a paginar em memória.
     */
    private static void inicializarGrade(TabelaPreco tabela) {
        tabela.getPrecosKmExcedente().size();
        tabela.getGrupos().forEach(grupo -> grupo.getPacotes().size());
    }

    private void aplicar(TabelaPreco tabela, DadosDaTabela dados) {
        tabela.alterarObservacoes(dados.observacoes());

        tabela.limparGrupos();
        Set<String> codigosVistos = new HashSet<>();
        for (DadosDoGrupo dadosDoGrupo : dados.grupos() == null ? List.<DadosDoGrupo>of() : dados.grupos()) {
            String codigo = dadosDoGrupo.codigo() == null
                    ? null
                    : dadosDoGrupo.codigo().trim().toUpperCase(Locale.ROOT);
            if (!codigosVistos.add(codigo)) {
                throw new NegocioException(
                        ErroCadastro.GRUPO_TARIFARIO_DUPLICADO,
                        "O grupo \"%s\" aparece mais de uma vez na vigência.".formatted(codigo),
                        Map.of("codigo", String.valueOf(codigo)));
            }
            GrupoTarifario grupo = tabela.adicionarGrupo(
                    dadosDoGrupo.codigo(), dadosDoGrupo.veiculosDoGrupo(), dadosDoGrupo.categoria());
            List<DadosDoPacote> pacotes =
                    dadosDoGrupo.pacotes() == null ? List.of() : dadosDoGrupo.pacotes();
            for (DadosDoPacote pacote : pacotes) {
                grupo.definirPacote(pacote.pacoteKm(), pacote.valorMensal());
            }
        }

        tabela.limparKmExcedente();
        List<DadosDoKmExcedente> excedentes =
                dados.kmExcedente() == null ? List.of() : dados.kmExcedente();
        for (DadosDoKmExcedente excedente : excedentes) {
            tabela.definirKmExcedente(excedente.categoria(), excedente.pacoteKm(), excedente.valorKm());
        }
    }

    private Locadora resolverLocadora(Long locadoraId) {
        if (locadoraId == null) {
            throw new RecursoNaoEncontradoException("Locadora", null);
        }
        return locadoras.buscarPorId(locadoraId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Locadora", locadoraId));
    }

    private void garantirVigenciaDisponivel(Long locadoraId, int ano, Long idAtual) {
        if (tabelas.existeOutraVigencia(locadoraId, ano, idAtual)) {
            throw new NegocioException(
                    ErroCadastro.VIGENCIA_DUPLICADA,
                    "Esta locadora já tem uma tabela de preços para %d.".formatted(ano),
                    Map.of("locadoraId", locadoraId, "ano", ano));
        }
    }
}
