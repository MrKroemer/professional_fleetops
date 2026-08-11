package br.com.proyfebrasil.fleetops.contratos.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.conformidade.application.ServicoDeFumacaPreta;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.ErroContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.EventoDeContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.ItemDoBook;
import br.com.proyfebrasil.fleetops.contratos.domain.SituacaoDoEvento;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.TipoDeEvento;
import br.com.proyfebrasil.fleetops.contratos.domain.TrocaCondutor;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.contratos.infra.EventoDeContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.domain.FechamentoMensal;
import br.com.proyfebrasil.fleetops.operacao.infra.FechamentoRepository;
import br.com.proyfebrasil.fleetops.shared.arquivos.Anexo;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import org.hibernate.Hibernate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida do contrato: retirada, troca de condutor e devolução (Fase 2).
 *
 * <p>Separado de {@link ServicoDeContratos}, que cuida do cadastro do contrato em si. A
 * divisão não é estética: os eventos daqui atravessam três módulos — contrato, anexos e
 * conformidade —, e misturá-los com a abertura de contrato tornaria impossível carregar
 * um histórico sem arrastar junto o armazenamento de arquivos.
 *
 * <p>As três regras da fase moram aqui, cada uma no ponto em que pode ser aplicada:
 * a RN-09 na conclusão da retirada, a RN-12 na conclusão do evento (delegada ao domínio,
 * que é quem conhece o book), e a RN-17 na devolução à locadora.
 */
@Service
public class ServicoDoCicloDeVida {

    private final ContratoRepository contratos;
    private final EventoDeContratoRepository eventos;
    private final ServicoDeFumacaPreta fumacaPreta;
    private final FechamentoRepository fechamentos;
    private final Clock relogio;

    public ServicoDoCicloDeVida(
            ContratoRepository contratos,
            EventoDeContratoRepository eventos,
            ServicoDeFumacaPreta fumacaPreta,
            FechamentoRepository fechamentos,
            Clock relogio) {
        this.contratos = contratos;
        this.eventos = eventos;
        this.fumacaPreta = fumacaPreta;
        this.fechamentos = fechamentos;
        this.relogio = relogio;
    }

    // -----------------------------------------------------------------------
    // Retirada e devolução (RN-12)
    // -----------------------------------------------------------------------

    /** Dados de abertura de um evento de retirada ou devolução. */
    public record DadosDoEvento(
            TipoDeEvento tipo,
            Veiculo veiculo,
            Condutor condutor,
            LocalDate dataDoEvento,
            Integer km,
            String localDoEvento,
            String checklistDaLocadora,
            String observacoes) {
    }

    /**
     * Abre um evento em preenchimento.
     *
     * <p>Nasce incompleto de propósito. A alternativa — receber tudo de uma vez — obrigaria
     * o usuário a subir oito fotos e o CRLV em uma única requisição, no pátio da locadora,
     * pelo celular. Qualquer falha de rede jogaria o trabalho fora.
     */
    @Transactional
    public EventoDeContrato abrirEvento(Long contratoId, DadosDoEvento dados) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        var evento = new EventoDeContrato(contrato, dados.tipo(), dados.veiculo(), dados.dataDoEvento());
        evento.preencher(
                dados.condutor(),
                dados.km(),
                dados.localDoEvento(),
                dados.checklistDaLocadora(),
                dados.observacoes());
        return resolverParaResposta(eventos.save(evento));
    }

    @Transactional
    public EventoDeContrato atualizarEvento(Long eventoId, DadosDoEvento dados) {
        EventoDeContrato evento = buscarEvento(eventoId);
        evento.preencher(
                dados.condutor(),
                dados.km(),
                dados.localDoEvento(),
                dados.checklistDaLocadora(),
                dados.observacoes());
        return resolverParaResposta(evento);
    }

    @Transactional
    public EventoDeContrato anexarFoto(Long eventoId, ItemDoBook item, Anexo anexo, String observacao) {
        EventoDeContrato evento = buscarEvento(eventoId);
        evento.adicionarFoto(item, anexo, observacao);
        return resolverParaResposta(evento);
    }

    @Transactional
    public EventoDeContrato removerFoto(Long eventoId, Long fotoId) {
        EventoDeContrato evento = buscarEvento(eventoId);
        if (!evento.removerFoto(fotoId)) {
            throw new RecursoNaoEncontradoException("Foto do book", fotoId);
        }
        return resolverParaResposta(evento);
    }

    @Transactional
    public EventoDeContrato anexarCrlv(Long eventoId, Anexo crlv) {
        EventoDeContrato evento = buscarEvento(eventoId);
        evento.anexarCrlv(crlv);
        return resolverParaResposta(evento);
    }

    @Transactional
    public EventoDeContrato registrarAceiteDasRegras(Long eventoId) {
        EventoDeContrato evento = buscarEvento(eventoId);
        evento.registrarAceiteDasRegras(relogio.instant());
        return resolverParaResposta(evento);
    }

    /**
     * Conclui o evento (RN-12) e, sendo retirada, aplica a RN-09.
     *
     * <p>A ordem importa, e a RN-09 vem primeiro. Um veículo a diesel reprovado vai ser
     * trocado, e com ele todo o book: pedir as oito fotos antes de dizer que o carro não
     * serve faria o usuário fotografar duas vezes. Falha do veículo antes de falha de
     * preenchimento, porque a primeira invalida o trabalho que a segunda pede.
     */
    @Transactional
    public EventoDeContrato concluirEvento(Long eventoId) {
        EventoDeContrato evento = buscarEvento(eventoId);

        if (evento.getTipo() == TipoDeEvento.RETIRADA) {
            exigirFumacaPretaAprovada(evento.getVeiculo());
        }

        evento.concluir(relogio.instant());
        return resolverParaResposta(evento);
    }

    /**
     * Aplica a RN-09.
     *
     * <p>Distingue os dois casos porque as ações são diferentes: teste pendente se resolve
     * fazendo o teste; teste reprovado só se resolve trocando o veículo. Um erro genérico
     * de "problema com fumaça preta" deixaria o usuário sem saber qual dos dois caminhos
     * seguir.
     */
    private void exigirFumacaPretaAprovada(Veiculo veiculo) {
        var situacao = fumacaPreta.situacaoDe(veiculo);
        if (situacao.pendente()) {
            throw new NegocioException(
                    ErroContrato.FUMACA_PRETA_PENDENTE,
                    "O veículo %s é a diesel e ainda não tem teste de fumaça preta. Registre o teste antes de concluir a retirada."
                            .formatted(veiculo.getPlaca()),
                    Map.of("veiculoId", veiculo.getId(), "placa", veiculo.getPlaca()));
        }
        if (situacao.reprovado()) {
            var teste = situacao.teste().orElseThrow();
            throw new NegocioException(
                    ErroContrato.FUMACA_PRETA_REPROVADA,
                    "O veículo %s reprovou no teste de fumaça preta de %s. %s A retirada só pode ser concluída com outro veículo."
                            .formatted(veiculo.getPlaca(), teste.getDataDoTeste(), teste.justificativa()),
                    Map.of(
                            "veiculoId", veiculo.getId(),
                            "placa", veiculo.getPlaca(),
                            "testeId", teste.getId(),
                            "padrao", teste.getPadrao().getCodigo()));
        }
    }

    @Transactional
    public EventoDeContrato reabrirEvento(Long eventoId) {
        EventoDeContrato evento = buscarEvento(eventoId);
        evento.reabrir();
        return resolverParaResposta(evento);
    }

    @Transactional(readOnly = true)
    public List<EventoDeContrato> eventosDoContrato(Long contratoId) {
        return eventos.doContrato(contratoId).stream().map(this::resolverParaResposta).toList();
    }

    // -----------------------------------------------------------------------
    // Troca de condutor (RN-18)
    // -----------------------------------------------------------------------

    /**
     * Coloca outro condutor no contrato a partir da data informada.
     *
     * <p>O período do anterior é fechado na véspera, sem lacuna — o mesmo desenho da
     * substituição de veículo, para que a linha do tempo do contrato seja legível em uma
     * leitura só.
     */
    @Transactional
    public TrocaCondutor trocarCondutor(Long contratoId, Condutor novo, LocalDate aPartirDe, String motivo) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        exigirContratoAtivo(contrato, "trocar o condutor");

        // Aqui — e não no domínio — porque esta é uma troca operacional: alguém vai
        // assumir o volante a partir de agora. O agregado também registra histórico, e
        // naquele contexto um condutor hoje inativo é normal, não erro.
        if (!novo.estaAtivo()) {
            throw new NegocioException(
                    ErroContrato.CONDUTOR_INATIVO,
                    "%s está com cadastro inativo e não pode assumir o contrato. Reative o condutor antes da troca."
                            .formatted(novo.getNome()));
        }

        return contrato.colocarCondutor(novo, aPartirDe, motivo);
    }

    // -----------------------------------------------------------------------
    // Desmobilização e devolução (RN-17)
    // -----------------------------------------------------------------------

    /**
     * O que a RN-17 exige antes de devolver à locadora.
     *
     * @param eventoDeDevolucaoConcluido book e CRLV da devolução em ordem
     * @param avariasAbertas quantas avarias impedem o status DEVOLVIDO
     * @param fechamentoDoPeriodoFinal se o mês final já foi fechado
     */
    public record VerificacaoDeDevolucao(
            boolean eventoDeDevolucaoConcluido,
            int avariasAbertas,
            boolean fechamentoDoPeriodoFinal,
            List<String> pendencias) {

        /** Indica se o contrato pode ir a DEVOLVIDO; caso contrário, só DESMOBILIZADO. */
        public boolean liberaDevolucao() {
            return pendencias.isEmpty();
        }
    }

    /**
     * Verifica as condições da RN-17.
     *
     * <p>Três das quatro condições já valem: o evento de devolução, o fechamento mensal do
     * período final (Fase 3) e a data de encerramento. A quarta — avarias abertas — depende
     * do módulo de conformidade da Fase 4 e continua como consulta isolada e marcada,
     * devolvendo zero. Deixá-la de fora daria a impressão de que a RN-17 está completa, e
     * ela não está enquanto as avarias não existirem.
     */
    @Transactional(readOnly = true)
    public VerificacaoDeDevolucao verificarDevolucao(Long contratoId) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        List<String> pendencias = new ArrayList<>();

        boolean temEvento = eventos
                .ultimoDoTipo(contrato.getId(), TipoDeEvento.DEVOLUCAO, SituacaoDoEvento.CONCLUIDO)
                .isPresent();
        if (!temEvento) {
            pendencias.add("Falta concluir o evento de devolução com book fotográfico e CRLV.");
        }

        int avariasAbertas = contarAvariasAbertas(contrato.getId());
        if (avariasAbertas > 0) {
            pendencias.add("Há %d avaria(s) em aberto neste contrato.".formatted(avariasAbertas));
        }

        boolean fechado = periodoFinalFechado(contrato);
        if (!fechado) {
            pendencias.add(
                    "O fechamento mensal de %s ainda não foi conferido."
                            .formatted(competenciaFinal(contrato)));
        }

        return new VerificacaoDeDevolucao(temEvento, avariasAbertas, fechado, List.copyOf(pendencias));
    }

    /**
     * Encerra o contrato (RN-17).
     *
     * <p>{@code DESMOBILIZADO} — devolvido pela obra — não exige nada além do registro:
     * é uma decisão da obra, e o veículo pode continuar em tratativa com a locadora. Já
     * {@code DEVOLVIDO} encerra a relação com a locadora e por isso exige as verificações;
     * com pendência, a RN-17 manda permitir apenas a desmobilização.
     */
    @Transactional
    public ContratoDeLocacao encerrar(Long contratoId, StatusContrato novoStatus, LocalDate em) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        exigirContratoAtivo(contrato, "encerrar");

        if (novoStatus == StatusContrato.DEVOLVIDO) {
            var verificacao = verificarDevolucao(contratoId);
            if (!verificacao.liberaDevolucao()) {
                throw new NegocioException(
                        verificacao.avariasAbertas() > 0
                                ? ErroContrato.DEVOLUCAO_COM_AVARIA_ABERTA
                                : ErroContrato.DEVOLUCAO_SEM_EVENTO,
                        "A devolução à locadora está bloqueada: %s Registre a desmobilização enquanto as pendências não forem resolvidas."
                                .formatted(String.join(" ", verificacao.pendencias())),
                        Map.of("pendencias", verificacao.pendencias()));
            }
        }

        contrato.encerrar(novoStatus, em == null ? LocalDate.now(relogio) : em);
        return contrato;
    }

    /**
     * Avarias em aberto do contrato.
     *
     * <p>Ponto de extensão da Fase 4. Devolve zero enquanto o módulo de conformidade não
     * tiver avarias — e não "desconhecido", porque um contrato sem avarias registradas de
     * fato não tem avaria aberta. A diferença é que hoje ninguém consegue registrar uma.
     */
    private int contarAvariasAbertas(Long contratoId) {
        return 0;
    }

    /**
     * Fechamento mensal do período final (RN-17, ligado na Fase 3).
     *
     * <p>A competência conferida é a do encerramento; quando ele ainda não foi informado —
     * o caso de quem abre a tela antes de decidir a data — vale o mês corrente, que é o
     * que a devolução fecharia se acontecesse hoje.
     *
     * <p>Exige {@code CONFERIDO}, e não apenas a existência do registro: o fechamento é
     * calculado sempre, então "existir" não significa nada. O que a RN-17 quer é que
     * alguém tenha olhado os lançamentos daquele mês antes de encerrar a relação com a
     * locadora — e é justamente isso que a conferência registra.
     */
    private boolean periodoFinalFechado(ContratoDeLocacao contrato) {
        return fechamentos
                .daCompetencia(contrato.getId(), competenciaFinal(contrato).atDay(1))
                .map(FechamentoMensal::estaConferido)
                .orElse(false);
    }

    private YearMonth competenciaFinal(ContratoDeLocacao contrato) {
        LocalDate referencia = contrato.getDataEncerramento() == null
                ? LocalDate.now(relogio)
                : contrato.getDataEncerramento();
        return YearMonth.from(referencia);
    }

    // -----------------------------------------------------------------------

    private void exigirContratoAtivo(ContratoDeLocacao contrato, String acao) {
        if (!contrato.estaAtivo()) {
            throw new NegocioException(
                    ErroContrato.CONTRATO_ENCERRADO,
                    "Não é possível %s: o contrato está %s desde %s."
                            .formatted(acao, contrato.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                                    contrato.getDataEncerramento()));
        }
    }

    private ContratoDeLocacao buscarContrato(Long id) {
        return contratos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", id));
    }

    private EventoDeContrato buscarEvento(Long id) {
        return eventos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento de contrato", id));
    }

    /**
     * Resolve as associações que a resposta vai ler.
     *
     * <p>Com {@code open-in-view=false} a sessão fecha ao sair do serviço, e o mapeamento
     * acontece depois. Veículo, condutor, CRLV e fotos são preguiçosos — de propósito, para
     * que a listagem não os arraste —, então quem devolve o evento para virar resposta
     * precisa pedi-los enquanto ainda há sessão. Sem isto, um evento com condutor estoura
     * em {@code LazyInitializationException} na serialização, longe da causa.
     */
    private EventoDeContrato resolverParaResposta(EventoDeContrato evento) {
        Hibernate.initialize(evento.getVeiculo());
        evento.getCondutor().ifPresent(Hibernate::initialize);
        evento.getCrlv().ifPresent(Hibernate::initialize);
        evento.getFotos().forEach(foto -> Hibernate.initialize(foto.getAnexo()));
        return evento;
    }

    /** Último evento concluído do tipo — usado pela tela para saber o que já existe. */
    @Transactional(readOnly = true)
    public Optional<EventoDeContrato> ultimoEventoConcluido(Long contratoId, TipoDeEvento tipo) {
        return eventos.ultimoDoTipo(contratoId, tipo, SituacaoDoEvento.CONCLUIDO);
    }
}
