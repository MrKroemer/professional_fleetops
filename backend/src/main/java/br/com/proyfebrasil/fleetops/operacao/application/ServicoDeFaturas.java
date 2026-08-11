package br.com.proyfebrasil.fleetops.operacao.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.domain.ErroOperacao;
import br.com.proyfebrasil.fleetops.operacao.domain.FaturaDaLocadora;
import br.com.proyfebrasil.fleetops.operacao.domain.StatusDeConferencia;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeUsoParticular;
import br.com.proyfebrasil.fleetops.operacao.domain.UsoParticular;
import br.com.proyfebrasil.fleetops.operacao.infra.FaturaRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.UsoParticularRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faturas da locadora (RN-13) e autorizações de uso particular (RN-10).
 *
 * <p>Os dois assuntos moram juntos porque são o que resta da operação mensal depois dos
 * lançamentos e do fechamento — e ambos são pequenos demais para um serviço cada. O que os
 * une de fato é a competência: a fatura é do mês, e o uso particular é o que explica um
 * pedaço da quilometragem daquele mês.
 */
@Service
public class ServicoDeFaturas {

    private final ContratoRepository contratos;
    private final FaturaRepository faturas;
    private final UsoParticularRepository usos;
    private final Clock relogio;

    public ServicoDeFaturas(
            ContratoRepository contratos,
            FaturaRepository faturas,
            UsoParticularRepository usos,
            Clock relogio) {
        this.contratos = contratos;
        this.faturas = faturas;
        this.usos = usos;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ faturas

    /** Dados de uma fatura mensal. */
    public record DadosDaFatura(
            YearMonth competencia,
            BigDecimal valorContratado,
            BigDecimal valorFaturado,
            BigDecimal extrasAprovados,
            String numeroDaNota,
            LocalDate vencimento,
            StatusDeConferencia status,
            String observacoes) {
    }

    /**
     * Lança a fatura de uma competência.
     *
     * <p>Uma por contrato e mês — a segunda quase sempre é a mesma nota digitada de novo,
     * e duas linhas dobrariam a divergência apurada sem que nada acusasse.
     */
    @Transactional
    public FaturaDaLocadora lancar(Long contratoId, DadosDaFatura dados) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        faturas.daCompetencia(contratoId, dados.competencia().atDay(1)).ifPresent(existente -> {
            throw new NegocioException(
                    ErroOperacao.FATURA_DUPLICADA,
                    "Já há fatura lançada para %s neste contrato. Edite a existente em vez de criar outra."
                            .formatted(dados.competencia()),
                    Map.of("faturaId", existente.getId()));
        });

        var fatura = new FaturaDaLocadora(contrato, dados.competencia());
        aplicar(fatura, dados);
        return resolver(faturas.save(fatura));
    }

    @Transactional
    public FaturaDaLocadora atualizar(Long faturaId, DadosDaFatura dados) {
        FaturaDaLocadora fatura = buscarFatura(faturaId);
        aplicar(fatura, dados);
        return resolver(fatura);
    }

    /**
     * Aplica valores e conferência, nessa ordem.
     *
     * <p>A ordem é a regra: a RN-13 avalia o status contra a divergência, e a divergência
     * depende dos valores. Conferir antes de atualizar os números validaria o status
     * contra a divergência anterior — exatamente o erro que a regra existe para impedir.
     */
    private void aplicar(FaturaDaLocadora fatura, DadosDaFatura dados) {
        fatura.alterarValores(dados.valorContratado(), dados.valorFaturado(), dados.extrasAprovados());
        fatura.alterarDadosDaNota(dados.numeroDaNota(), dados.vencimento());
        fatura.alterarConferencia(
                dados.status() == null ? StatusDeConferencia.PENDENTE : dados.status(), dados.observacoes());
    }

    @Transactional(readOnly = true)
    public List<FaturaDaLocadora> doContrato(Long contratoId) {
        return faturas.doContrato(contratoId).stream().map(ServicoDeFaturas::resolver).toList();
    }

    /** Faturas com divergência ainda sem tratativa concluída — alimenta a RN-23. */
    @Transactional(readOnly = true)
    public List<FaturaDaLocadora> comDivergenciaEmAberto() {
        return faturas.comDivergenciaEmAberto();
    }

    @Transactional
    public void excluirFatura(Long faturaId) {
        buscarFatura(faturaId).excluir(relogio.instant());
    }

    // ------------------------------------------------------------------ uso particular

    /** Dados de uma autorização de uso particular. */
    public record DadosDoUsoParticular(
            Condutor condutor,
            TipoDeUsoParticular tipo,
            LocalDate inicio,
            LocalDate fim,
            Integer kmAutorizado,
            Integer kmPercorrido,
            boolean aceitarRegras,
            String observacoes) {
    }

    @Transactional
    public UsoParticular autorizarUsoParticular(Long contratoId, DadosDoUsoParticular dados) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        var uso = new UsoParticular(
                contrato, dados.condutor(), dados.tipo(), dados.inicio(), dados.fim());
        aplicar(uso, dados);
        return usos.save(uso);
    }

    @Transactional
    public UsoParticular atualizarUsoParticular(Long usoId, DadosDoUsoParticular dados) {
        UsoParticular uso = usos.buscarPorId(usoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Uso particular", usoId));
        aplicar(uso, dados);
        return uso;
    }

    private void aplicar(UsoParticular uso, DadosDoUsoParticular dados) {
        if (dados.kmAutorizado() != null) {
            uso.autorizarKm(dados.kmAutorizado());
        }
        uso.registrarKmPercorrido(dados.kmPercorrido());
        uso.alterarObservacoes(dados.observacoes());
        if (dados.aceitarRegras() && uso.getAceiteEm() == null) {
            uso.registrarAceite(relogio.instant());
        }
    }

    @Transactional(readOnly = true)
    public List<UsoParticular> usosDoContrato(Long contratoId) {
        return usos.doContrato(contratoId);
    }

    @Transactional
    public void excluirUsoParticular(Long usoId) {
        usos.buscarPorId(usoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Uso particular", usoId))
                .excluir(relogio.instant());
    }

    // ------------------------------------------------------------------

    /**
     * Resolve o contrato e o que a resposta lê dele.
     *
     * <p>A fatura mostra obra e placa para que a lista de divergências seja legível sem
     * abrir cada contrato. Com {@code open-in-view=false} a sessão fecha antes do
     * mapeamento, e sem isto a leitura estoura em {@code LazyInitializationException} —
     * longe da causa, no meio da serialização.
     */
    private static FaturaDaLocadora resolver(FaturaDaLocadora fatura) {
        var contrato = fatura.getContrato();
        Hibernate.initialize(contrato);
        Hibernate.initialize(contrato.getObra());
        if (contrato.getVeiculoAtual() != null) {
            Hibernate.initialize(contrato.getVeiculoAtual());
        }
        return fatura;
    }

    private ContratoDeLocacao buscarContrato(Long id) {
        return contratos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", id));
    }

    private FaturaDaLocadora buscarFatura(Long id) {
        return faturas.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura", id));
    }
}
