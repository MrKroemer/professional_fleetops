package br.com.proyfebrasil.fleetops.contratos.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contratos de locação.
 *
 * <p>Cobre o esqueleto do agregado: abertura do contrato, colocação e substituição do
 * veículo, definição do condutor e encerramento. Retirada e devolução com book
 * fotográfico, CRLV, teste de fumaça preta e trocas de condutor com histórico próprio
 * completam a Fase 2 nas próximas entregas.
 */
@Service
public class ServicoDeContratos {

    private final ContratoRepository contratos;
    private final Clock relogio;

    public ServicoDeContratos(ContratoRepository contratos, Clock relogio) {
        this.contratos = contratos;
        this.relogio = relogio;
    }

    /** Uma troca de veículo a registrar junto com a abertura. */
    public record TrocaDeVeiculo(Veiculo veiculo, LocalDate quando, String motivo) {
    }

    /**
     * Resultado da abertura.
     *
     * @param recusadas trocas que não puderam ser registradas, com o motivo — a carga de
     *     histórico precisa saber o que ficou de fora, em vez de perder a informação
     */
    public record ResultadoDaAbertura(ContratoDeLocacao contrato, List<String> recusadas) {
    }

    /** Dados de abertura de um contrato. */
    public record DadosDoContrato(
            Obra obra,
            Locadora locadora,
            Veiculo veiculo,
            Condutor condutor,
            String codigoInterno,
            String localRetirada,
            LocalDate dataRetirada,
            LocalDate dataEncerramento,
            Integer pacoteKmContratado,
            BigDecimal valorMensalContratado,
            StatusContrato status,
            String observacoes,
            List<TrocaDeVeiculo> substituicoes) {
    }

    @Transactional(readOnly = true)
    public ContratoDeLocacao buscar(Long id) {
        return contratos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", id));
    }

    /**
     * Contrato ativo de um veículo — o mais recente, quando há mais de um.
     *
     * <p>Três placas do acervo aparecem em dois contratos ativos ao mesmo tempo, e a V4
     * deixou de proibir isso porque a RN-01 fala do contrato, não do veículo. Escolher o de
     * retirada mais recente é a leitura útil: é o contrato em que o carro está agora, e o
     * conflito segue visível na central de pendências.
     */
    @Transactional(readOnly = true)
    public Optional<ContratoDeLocacao> contratoAtivoDoVeiculo(Long veiculoId) {
        return contratos.ativosDoVeiculo(veiculoId).stream().findFirst();
    }

    /** Contratos ativos que já têm veículo, para as telas de operação. */
    @Transactional(readOnly = true)
    public List<ContratoDeLocacao> ativosComVeiculo() {
        return contratos.ativosComVeiculo();
    }

    @Transactional(readOnly = true)
    public long contarAtivos() {
        return contratos.contarAtivos();
    }

    /** Filtros da listagem de contratos. */
    public record FiltroDeContratos(String termo, StatusContrato status, Long obraId, Long locadoraId) {
    }

    /**
     * Página de contratos.
     *
     * <p>O termo é normalizado aqui, e não na consulta: transformá-lo em curinga dentro do
     * JPQL exigiria concatenação, que impede o banco de reaproveitar o plano da consulta.
     *
     * <p>Os históricos são resolvidos antes de a transação fechar. A listagem mostra
     * quantas trocas cada contrato teve, e com {@code open-in-view=false} o mapeamento
     * acontece com a sessão já encerrada — sem isto, a contagem estoura em
     * {@code LazyInitializationException}. Duas consultas a mais por página, e não duas
     * por linha, graças ao {@code @BatchSize} declarado nas coleções.
     */
    @Transactional(readOnly = true)
    public Page<ContratoDeLocacao> listar(FiltroDeContratos filtro, Pageable paginacao) {
        String termo = filtro.termo() == null || filtro.termo().isBlank()
                ? null
                : "%" + filtro.termo().trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<ContratoDeLocacao> pagina =
                contratos.listar(termo, filtro.status(), filtro.obraId(), filtro.locadoraId(), paginacao);
        pagina.forEach(contrato -> {
            Hibernate.initialize(contrato.getSubstituicoes());
            Hibernate.initialize(contrato.getTrocasDeCondutor());
        });
        return pagina;
    }

    /**
     * Abre um contrato com todo o seu histórico de veículos.
     *
     * <p>A ordem importa e é a razão de as substituições virem junto da abertura, e não
     * em chamadas separadas: colocar o veículo original, aplicar as trocas e só então
     * encerrar. Encerrar antes fecharia o período corrente na data de devolução, e as
     * trocas seguintes abririam períodos sobrepostos ao intervalo já fechado — algo que
     * a restrição de exclusão do banco rejeita, e com razão.
     *
     * <p>O período começa na data de retirada, não na data de hoje: o histórico precisa
     * reproduzir o que de fato aconteceu, e toda a carga inicial registra o passado.
     *
     * <p>Trocas que não cabem na linha do tempo são recusadas e devolvidas ao chamador
     * com o motivo, em vez de interromper a abertura. É a forma da RN-24: registrar o
     * que dá, relatar o que não deu.
     */
    @Transactional
    public ResultadoDaAbertura abrir(DadosDoContrato dados) {
        ContratoDeLocacao contrato =
                new ContratoDeLocacao(dados.obra(), dados.locadora(), dados.dataRetirada());
        contrato.alterarDadosDaLocacao(
                dados.codigoInterno(),
                dados.localRetirada(),
                dados.pacoteKmContratado(),
                dados.valorMensalContratado());
        contrato.alterarObservacoes(dados.observacoes());

        LocalDate inicio = inicioDoPeriodo(dados);
        if (dados.veiculo() != null) {
            contrato.colocarVeiculo(dados.veiculo(), inicio, "Retirada inicial");
        }

        List<String> recusadas = new ArrayList<>();

        // O condutor abre período na mesma data do veículo (RN-18). A RN-16 pode recusar
        // o vínculo — CNH já vencida na data — e essa recusa não pode derrubar a carga de
        // um contrato que existiu: entra no relatório, como as trocas de veículo.
        if (dados.condutor() != null) {
            try {
                contrato.colocarCondutor(dados.condutor(), inicio, "Condutor da retirada");
            } catch (NegocioException erro) {
                recusadas.add("condutor %s em %s: %s"
                        .formatted(dados.condutor().getNome(), inicio, erro.getMessage()));
            }
        }
        for (TrocaDeVeiculo troca : dados.substituicoes() == null ? List.<TrocaDeVeiculo>of() : dados.substituicoes()) {
            String motivoDaRecusa = motivoParaRecusar(contrato, troca, dados.dataEncerramento());
            if (motivoDaRecusa != null) {
                recusadas.add("%s em %s: %s".formatted(troca.veiculo().getPlaca(), troca.quando(), motivoDaRecusa));
                continue;
            }
            contrato.colocarVeiculo(troca.veiculo(), troca.quando(), troca.motivo());
        }

        StatusContrato status = dados.status() == null ? StatusContrato.ATIVO : dados.status();
        if (status != StatusContrato.ATIVO) {
            contrato.encerrar(status, dados.dataEncerramento());
        }

        return new ResultadoDaAbertura(contratos.save(contrato), recusadas);
    }

    /**
     * Verifica se a troca cabe na linha do tempo do contrato.
     *
     * @return o motivo da recusa, ou {@code null} quando a troca é válida
     */
    private static String motivoParaRecusar(
            ContratoDeLocacao contrato, TrocaDeVeiculo troca, LocalDate encerramento) {
        if (troca.veiculo() == null || troca.quando() == null) {
            return "veículo ou data ausentes";
        }
        if (encerramento != null && troca.quando().isAfter(encerramento)) {
            return "a data é posterior ao encerramento do contrato em %s".formatted(encerramento);
        }
        return contrato.periodoEmCurso()
                .filter(periodo -> !troca.quando().isAfter(periodo.getInicio()))
                .map(periodo -> "a data não é posterior a %s, início do período em curso".formatted(periodo.getInicio()))
                .orElse(null);
    }

    /**
     * Data em que o veículo passa a valer no contrato.
     *
     * <p>Preferência pela retirada; na falta dela, pelo encerramento. Usar a data de hoje
     * como início faria um contrato antigo sem retirada registrada nascer com o período
     * começando no futuro em relação ao seu próprio encerramento — foi exatamente o que
     * apareceu na carga do acervo, em um contrato de 2024 sem data de retirada.
     */
    private LocalDate inicioDoPeriodo(DadosDoContrato dados) {
        if (dados.dataRetirada() != null) {
            return dados.dataRetirada();
        }
        return dados.dataEncerramento() != null ? dados.dataEncerramento() : LocalDate.now(relogio);
    }

    @Transactional
    public ContratoDeLocacao substituirVeiculo(
            Long contratoId, Veiculo novoVeiculo, LocalDate aPartirDe, String motivo) {
        ContratoDeLocacao contrato = buscar(contratoId);
        contrato.colocarVeiculo(novoVeiculo, aPartirDe, motivo);
        return contrato;
    }

    @Transactional
    public ContratoDeLocacao encerrar(Long contratoId, StatusContrato status, LocalDate em) {
        ContratoDeLocacao contrato = buscar(contratoId);
        contrato.encerrar(status, em);
        return contrato;
    }
}
