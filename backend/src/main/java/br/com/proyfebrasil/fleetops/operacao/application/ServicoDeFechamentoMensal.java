package br.com.proyfebrasil.fleetops.operacao.application;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.domain.Abastecimento;
import br.com.proyfebrasil.fleetops.operacao.domain.FechamentoMensal;
import br.com.proyfebrasil.fleetops.operacao.domain.RegistroDeKm;
import br.com.proyfebrasil.fleetops.operacao.domain.ServicoOperacional;
import br.com.proyfebrasil.fleetops.operacao.domain.StatusDoFechamento;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import br.com.proyfebrasil.fleetops.operacao.infra.AbastecimentoRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.FechamentoRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.RegistroDeKmRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.ServicoOperacionalRepository;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fechamento mensal por veículo (RN-06, RN-21).
 *
 * <p>Este serviço <strong>calcula</strong>; não guarda. Todos os números do fechamento —
 * km inicial, km final, km percorrida, excedente, consumo, contagens — saem dos
 * lançamentos a cada leitura. A tabela {@code fechamento_mensal} guarda apenas se um
 * humano conferiu a competência.
 *
 * <p>A escolha é da RN-21, e o motivo é prático: a nota de um abastecimento chega dias
 * depois do mês virar, e o lançamento retroativo é rotina. Com os totais gravados, o
 * número no banco passaria a divergir da soma dos lançamentos sem que nada acusasse.
 * Calculando, o fechamento é sempre a verdade de agora — e uma conferência anterior a um
 * lançamento novo aparece como desatualizada, que é informação, não erro.
 */
@Service
public class ServicoDeFechamentoMensal {

    private final ContratoRepository contratos;
    private final RegistroDeKmRepository registros;
    private final AbastecimentoRepository abastecimentos;
    private final ServicoOperacionalRepository servicos;
    private final FechamentoRepository fechamentos;
    private final ServicoDeTabelasDePreco tabelas;
    private final Clock relogio;

    public ServicoDeFechamentoMensal(
            ContratoRepository contratos,
            RegistroDeKmRepository registros,
            AbastecimentoRepository abastecimentos,
            ServicoOperacionalRepository servicos,
            FechamentoRepository fechamentos,
            ServicoDeTabelasDePreco tabelas,
            Clock relogio) {
        this.contratos = contratos;
        this.registros = registros;
        this.abastecimentos = abastecimentos;
        this.servicos = servicos;
        this.fechamentos = fechamentos;
        this.tabelas = tabelas;
        this.relogio = relogio;
    }

    /**
     * O fechamento de uma competência, inteiramente derivado.
     *
     * @param kmExcedente quanto passou do pacote contratado; zero quando dentro da franquia
     * @param valorDoKmExcedente preço unitário da vigência da competência, quando disponível
     * @param custoDoExcedente estimativa — excedente × valor unitário
     * @param vigenciaIndisponivel verdadeiro quando não há tabela de preços do ano, caso em
     *     que o excedente é conhecido mas seu custo não pode ser calculado
     */
    public record Fechamento(
            Long contratoId,
            YearMonth competencia,
            String placa,
            String obra,
            Integer kmInicial,
            Integer kmFinal,
            int kmPercorrido,
            Integer pacoteContratado,
            int kmExcedente,
            BigDecimal valorDoKmExcedente,
            BigDecimal custoDoExcedente,
            boolean vigenciaIndisponivel,
            BigDecimal consumoTotal,
            int quantidadeDeAbastecimentos,
            BigDecimal custoDeLavaJato,
            BigDecimal custoDeBorracharia,
            BigDecimal custoDeParaBrisas,
            BigDecimal custoTotal,
            int lancamentosNaoConformes,
            StatusDoFechamento status,
            String observacoes) {

        /** Indica se o contrato estourou a franquia — o que o dashboard sinaliza (RN-06). */
        public boolean estourouOPacote() {
            return kmExcedente > 0;
        }
    }

    /**
     * Apura a competência.
     *
     * <p>O km inicial é o do <em>primeiro</em> registro do mês e o final é o do último, em
     * vez da soma dos trechos diários. A diferença aparece quando falta um dia: somar os
     * trechos ignoraria a quilometragem do dia não lançado, e o fechamento fecharia bonito
     * escondendo a lacuna. Pelos extremos do hodômetro, o buraco entra na conta — que é o
     * que a locadora vai cobrar de qualquer jeito.
     */
    @Transactional(readOnly = true)
    public Fechamento apurar(Long contratoId, YearMonth competencia) {
        ContratoDeLocacao contrato = contratos.buscarPorId(contratoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", contratoId));

        LocalDate inicio = competencia.atDay(1);
        LocalDate fim = competencia.atEndOfMonth();

        List<RegistroDeKm> doMes = registros.doPeriodo(contratoId, inicio, fim);
        Integer kmInicial = doMes.isEmpty() ? null : doMes.get(0).getKmInicial();
        Integer kmFinal = doMes.isEmpty() ? null : doMes.get(doMes.size() - 1).getKmFinal();
        int kmPercorrido = kmInicial == null ? 0 : kmFinal - kmInicial;

        Integer pacote = contrato.getPacoteKmContratado();
        int excedente = pacote == null || pacote <= 0 ? 0 : Math.max(0, kmPercorrido - pacote);

        BigDecimal valorUnitario = null;
        boolean vigenciaIndisponivel = false;
        if (excedente > 0) {
            var preco = valorDoKmExcedente(contrato, competencia, pacote);
            valorUnitario = preco.orElse(null);
            vigenciaIndisponivel = preco.isEmpty();
        }
        BigDecimal custoDoExcedente = valorUnitario == null
                ? BigDecimal.ZERO
                : valorUnitario.multiply(BigDecimal.valueOf(excedente));

        List<Abastecimento> combustivel = abastecimentos.doPeriodo(contratoId, inicio, fim);
        BigDecimal consumo = somar(combustivel.stream().map(Abastecimento::getValor).toList());

        List<ServicoOperacional> prestados = servicos.doPeriodo(contratoId, inicio, fim);
        BigDecimal lavaJato = somarPorTipo(prestados, TipoDeServico.LAVA_JATO);
        BigDecimal borracharia = somarPorTipo(prestados, TipoDeServico.BORRACHARIA);
        BigDecimal paraBrisas = somarPorTipo(prestados, TipoDeServico.PARA_BRISAS);

        long naoConformes = combustivel.stream().filter(Abastecimento::isNaoConforme).count()
                + prestados.stream().filter(ServicoOperacional::isNaoConforme).count();

        var conferencia = fechamentos.daCompetencia(contratoId, inicio);

        return new Fechamento(
                contratoId,
                competencia,
                contrato.getVeiculoAtual() == null ? null : contrato.getVeiculoAtual().getPlaca(),
                contrato.getObra().getNome(),
                kmInicial,
                kmFinal,
                kmPercorrido,
                pacote,
                excedente,
                valorUnitario,
                custoDoExcedente,
                vigenciaIndisponivel,
                consumo,
                combustivel.size(),
                lavaJato,
                borracharia,
                paraBrisas,
                consumo.add(lavaJato).add(borracharia).add(paraBrisas).add(custoDoExcedente),
                (int) naoConformes,
                conferencia.map(FechamentoMensal::getStatus).orElse(StatusDoFechamento.ABERTO),
                conferencia.map(FechamentoMensal::getObservacoes).orElse(null));
    }

    /**
     * Preço do KM excedente na vigência da competência (RN-06 + RN-14).
     *
     * <p>Usa o ano da competência, não o corrente: reprocessar março de 2025 em 2026 tem
     * de reproduzir o preço de 2025. Devolve vazio quando a locadora não tem tabela do ano
     * — e o chamador expõe isso como "vigência indisponível" em vez de estimar zero, que
     * seria afirmar que o excedente não custa nada.
     */
    private Optional<BigDecimal> valorDoKmExcedente(
            ContratoDeLocacao contrato, YearMonth competencia, Integer pacote) {
        Veiculo veiculo = contrato.getVeiculoAtual();
        if (veiculo == null || pacote == null) {
            return Optional.empty();
        }
        try {
            return tabelas.valorKmExcedente(
                    contrato.getLocadora().getId(), competencia, veiculo.getCategoria(), pacote);
        } catch (RuntimeException semVigencia) {
            // `vigenciaPara` lança quando não há tabela do ano. Aqui isso não é erro: o
            // fechamento continua válido, só não consegue precificar o excedente.
            return Optional.empty();
        }
    }

    /**
     * Contratos que estouraram a franquia na competência (RN-06).
     *
     * <p>Apura contrato a contrato, e não por uma consulta agregada, porque o cálculo do
     * excedente precisa do pacote de cada contrato e do preço da vigência da locadora
     * dele — dados que uma soma em SQL não alcançaria sem replicar a regra no banco.
     * Com 49 contratos ativos o custo é irrelevante; se um dia deixar de ser, a medição
     * dirá, e aí valerá a pena a consulta especializada. Otimizar antes disso é proibido
     * pela Seção 7.
     */
    @Transactional(readOnly = true)
    public List<Fechamento> excedentesDaCompetencia(YearMonth competencia) {
        return contratos.ativosComVeiculo().stream()
                .map(contrato -> apurar(contrato.getId(), competencia))
                .filter(Fechamento::estourouOPacote)
                .sorted(java.util.Comparator.comparingInt(Fechamento::kmExcedente).reversed())
                .toList();
    }

    /**
     * A competência mais recente que tem lançamento.
     *
     * <p>É o mês sobre o qual faz sentido alertar. Sem lançamento nenhum, devolve o mês
     * anterior ao corrente — um sistema recém-instalado não deve apontar o mês em curso,
     * que ainda está recebendo dados.
     */
    @Transactional(readOnly = true)
    public YearMonth ultimaCompetenciaApurada() {
        return registros.dataDoUltimoLancamento()
                .map(YearMonth::from)
                .orElseGet(() -> YearMonth.now(relogio).minusMonths(1));
    }

    /** Marca a competência como conferida — decisão humana, não cálculo. */
    @Transactional
    public FechamentoMensal conferir(Long contratoId, YearMonth competencia, String usuario, String observacoes) {
        FechamentoMensal fechamento = obterOuCriar(contratoId, competencia);
        fechamento.conferir(usuario, relogio.instant(), observacoes);
        return fechamentos.save(fechamento);
    }

    @Transactional
    public FechamentoMensal reabrir(Long contratoId, YearMonth competencia) {
        FechamentoMensal fechamento = obterOuCriar(contratoId, competencia);
        fechamento.reabrir();
        return fechamentos.save(fechamento);
    }

    private FechamentoMensal obterOuCriar(Long contratoId, YearMonth competencia) {
        return fechamentos.daCompetencia(contratoId, competencia.atDay(1))
                .orElseGet(() -> {
                    ContratoDeLocacao contrato = contratos.buscarPorId(contratoId)
                            .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", contratoId));
                    return new FechamentoMensal(contrato, competencia);
                });
    }

    private static BigDecimal somarPorTipo(List<ServicoOperacional> servicos, TipoDeServico tipo) {
        return somar(servicos.stream()
                .filter(servico -> servico.getTipo() == tipo)
                .map(ServicoOperacional::getValor)
                .toList());
    }

    private static BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
