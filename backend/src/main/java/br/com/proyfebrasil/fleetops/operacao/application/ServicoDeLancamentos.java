package br.com.proyfebrasil.fleetops.operacao.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.DiaDaSemana;
import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.domain.Abastecimento;
import br.com.proyfebrasil.fleetops.operacao.domain.ErroOperacao;
import br.com.proyfebrasil.fleetops.operacao.domain.RegistroDeKm;
import br.com.proyfebrasil.fleetops.operacao.domain.ServicoOperacional;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import br.com.proyfebrasil.fleetops.operacao.infra.AbastecimentoRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.RegistroDeKmRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.ServicoOperacionalRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lançamentos do dia a dia: quilometragem, abastecimentos e serviços.
 *
 * <p>Reúne as três regras que dependem de <em>outros</em> lançamentos para serem
 * avaliadas — e é por isso que elas não estão nas entidades. A RN-03 compara com o
 * registro vizinho; a RN-04 conta o dia e consulta os dias autorizados do posto; a RN-05
 * conta a semana. Nenhuma delas se decide olhando só o objeto que está sendo salvo.
 */
@Service
public class ServicoDeLancamentos {

    private final ContratoRepository contratos;
    private final RegistroDeKmRepository registros;
    private final AbastecimentoRepository abastecimentos;
    private final ServicoOperacionalRepository servicos;

    public ServicoDeLancamentos(
            ContratoRepository contratos,
            RegistroDeKmRepository registros,
            AbastecimentoRepository abastecimentos,
            ServicoOperacionalRepository servicos) {
        this.contratos = contratos;
        this.registros = registros;
        this.abastecimentos = abastecimentos;
        this.servicos = servicos;
    }

    // ------------------------------------------------------------------ KM (RN-03)

    /** Dados de um registro diário de quilometragem. */
    public record DadosDeKm(
            Condutor condutor,
            LocalDate data,
            int kmInicial,
            int kmFinal,
            String origem,
            String destino,
            String observacao) {
    }

    /**
     * Lança um registro de quilometragem, aplicando a RN-03.
     *
     * <p>A verificação olha para os <strong>dois</strong> vizinhos, e não só para o
     * anterior. O texto da regra fala do registro anterior, mas o lançamento retroativo —
     * comum, porque o FOR.FRO.02 chega em papel dias depois — precisa caber no intervalo
     * entre o que veio antes e o que veio depois. Conferir só para trás deixaria passar um
     * registro que atropela o seguinte, e o erro só apareceria no fechamento.
     */
    @Transactional
    public RegistroDeKm lancarKm(Long contratoId, DadosDeKm dados) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        var registro = new RegistroDeKm(contrato, dados.data(), dados.kmInicial(), dados.kmFinal());
        registro.definirCondutor(dados.condutor());
        registro.alterarTrajeto(dados.origem(), dados.destino(), dados.observacao());
        verificarEncadeamento(contratoId, dados.data(), dados.kmInicial(), dados.kmFinal(), null);
        return registros.save(registro);
    }

    @Transactional
    public RegistroDeKm atualizarKm(Long registroId, DadosDeKm dados) {
        RegistroDeKm registro = registros.buscarPorId(registroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de KM", registroId));
        verificarEncadeamento(
                registro.getContrato().getId(), dados.data(), dados.kmInicial(), dados.kmFinal(), registroId);
        registro.alterarData(dados.data());
        registro.alterarQuilometragem(dados.kmInicial(), dados.kmFinal());
        registro.definirCondutor(dados.condutor());
        registro.alterarTrajeto(dados.origem(), dados.destino(), dados.observacao());
        return registro;
    }

    private void verificarEncadeamento(
            Long contratoId, LocalDate data, int kmInicial, int kmFinal, Long ignorar) {
        registros.anteriorA(contratoId, data, ignorar).ifPresent(anterior -> {
            if (kmInicial < anterior.getKmFinal()) {
                throw new NegocioException(
                        ErroOperacao.KM_RETROCEDE,
                        ("O KM inicial (%d) é menor que o KM final do registro de %s (%d). "
                                + "O hodômetro não anda para trás — confira qual dos dois lançamentos está errado.")
                                .formatted(kmInicial, anterior.getData(), anterior.getKmFinal()),
                        Map.of("kmInformado", kmInicial,
                                "registroAnteriorId", anterior.getId(),
                                "kmAnterior", anterior.getKmFinal(),
                                "dataAnterior", anterior.getData().toString()));
            }
        });

        registros.posteriorA(contratoId, data, ignorar).ifPresent(posterior -> {
            if (kmFinal > posterior.getKmInicial()) {
                throw new NegocioException(
                        ErroOperacao.KM_RETROCEDE,
                        ("O KM final (%d) é maior que o KM inicial do registro de %s (%d). "
                                + "Este lançamento não cabe entre os registros já existentes.")
                                .formatted(kmFinal, posterior.getData(), posterior.getKmInicial()),
                        Map.of("kmInformado", kmFinal,
                                "registroPosteriorId", posterior.getId(),
                                "kmPosterior", posterior.getKmInicial(),
                                "dataPosterior", posterior.getData().toString()));
            }
        });
    }

    @Transactional(readOnly = true)
    public List<RegistroDeKm> kmDoPeriodo(Long contratoId, LocalDate inicio, LocalDate fim) {
        return registros.doPeriodo(contratoId, inicio, fim);
    }

    @Transactional
    public void excluirKm(Long registroId, java.time.Instant momento) {
        registros.buscarPorId(registroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de KM", registroId))
                .excluir(momento);
    }

    // ------------------------------------------------------------------ Abastecimento (RN-04)

    /** Dados de um abastecimento. */
    public record DadosDeAbastecimento(
            Fornecedor posto,
            LocalDate data,
            BigDecimal valor,
            BigDecimal litros,
            Integer km,
            String observacao,
            String justificativa) {
    }

    /**
     * Avaliação de conformidade de um abastecimento (RN-04).
     *
     * @param motivos o que está fora da regra; vazio quando o lançamento é conforme
     */
    public record Conformidade(boolean conforme, List<String> motivos) {
    }

    /**
     * Verifica se o abastecimento cabe nas condições da RN-04.
     *
     * <p>Exposto como consulta para que a tela possa avisar <strong>antes</strong> do
     * envio: o usuário escolhe o posto e a data e já vê que aquele dia não é autorizado,
     * em vez de descobrir no erro. O serviço reavalia na gravação de qualquer forma — a
     * tela informa, o backend decide.
     */
    @Transactional(readOnly = true)
    public Conformidade avaliarAbastecimento(Long contratoId, Fornecedor posto, LocalDate data) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        List<String> motivos = new java.util.ArrayList<>();

        // Sem posto escolhido não há o que avaliar: a tela chama isto enquanto o usuário
        // ainda preenche o formulário, e apontar "não conforme" antes de ele escolher
        // seria acusar uma irregularidade que ninguém cometeu. O lançamento em si exige o
        // posto — a validação está na requisição.
        if (posto != null) {
            if (posto.getTipo() != TipoFornecedor.POSTO) {
                motivos.add("%s não é um posto de combustível.".formatted(posto.getNome()));
            }
            if (!posto.atendeObra(contrato.getObra().getId())) {
                motivos.add("%s não está credenciado para a obra %s."
                        .formatted(posto.getNome(), contrato.getObra().getCodigo()));
            }
            diaNaoAutorizado(posto, data).ifPresent(motivos::add);
        }

        return new Conformidade(motivos.isEmpty(), List.copyOf(motivos));
    }

    /**
     * Lança um abastecimento (RN-04).
     *
     * <p>O teto diário é rígido: um segundo abastecimento no mesmo dia é recusado, e não
     * aceito como não conforme. A diferença em relação às outras duas condições é que
     * posto e dia descrevem <em>onde</em> se abasteceu — informação que se quer registrar
     * mesmo irregular —, enquanto duas notas para o mesmo dia quase sempre são a mesma
     * nota lançada duas vezes. Aceitá-las dobraria o custo do mês em silêncio.
     */
    @Transactional
    public Abastecimento lancarAbastecimento(Long contratoId, DadosDeAbastecimento dados) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        garantirUnicoNoDia(contratoId, dados.data(), null);

        var abastecimento = new Abastecimento(contrato, dados.data(), dados.valor());
        abastecimento.alterarDados(dados.posto(), dados.litros(), dados.km(), dados.observacao());
        aplicarConformidade(contratoId, abastecimento, dados);
        return abastecimentos.save(abastecimento);
    }

    @Transactional
    public Abastecimento atualizarAbastecimento(Long id, DadosDeAbastecimento dados) {
        Abastecimento abastecimento = abastecimentos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Abastecimento", id));
        Long contratoId = abastecimento.getContrato().getId();
        garantirUnicoNoDia(contratoId, dados.data(), id);

        abastecimento.alterarValor(dados.valor());
        abastecimento.alterarDados(dados.posto(), dados.litros(), dados.km(), dados.observacao());
        aplicarConformidade(contratoId, abastecimento, dados);
        return abastecimento;
    }

    /**
     * Marca a não conformidade quando a avaliação reprova, exigindo justificativa.
     *
     * <p>A marcação não é escolha do usuário: ele não decide se o abastecimento foi em dia
     * autorizado, ele informa o posto e a data e o sistema conclui. O que o usuário fornece
     * é a justificativa — e sem ela o lançamento é recusado, como manda a RN-04.
     */
    private void aplicarConformidade(
            Long contratoId, Abastecimento abastecimento, DadosDeAbastecimento dados) {
        var avaliacao = avaliarAbastecimento(contratoId, dados.posto(), dados.data());
        if (avaliacao.conforme()) {
            abastecimento.registrarConformidade(false, null);
            return;
        }
        if (dados.justificativa() == null || dados.justificativa().isBlank()) {
            throw new NegocioException(
                    ErroOperacao.JUSTIFICATIVA_OBRIGATORIA,
                    "Este abastecimento está fora das condições autorizadas (%s). Informe a justificativa para registrá-lo assim mesmo."
                            .formatted(String.join(" ", avaliacao.motivos())),
                    Map.of("motivos", avaliacao.motivos()));
        }
        abastecimento.registrarConformidade(true, dados.justificativa());
    }

    private void garantirUnicoNoDia(Long contratoId, LocalDate data, Long ignorar) {
        if (abastecimentos.existeNoDia(contratoId, data, ignorar)) {
            throw new NegocioException(
                    ErroOperacao.ABASTECIMENTO_DUPLICADO,
                    ("Já existe abastecimento lançado em %s para este contrato. A RN-04 permite um por dia — "
                            + "se forem duas notas do mesmo dia, some os valores em um único lançamento.")
                            .formatted(data),
                    Map.of("data", data.toString()));
        }
    }

    private static Optional<String> diaNaoAutorizado(Fornecedor posto, LocalDate data) {
        var dados = posto.getDadosPosto();
        if (dados == null) {
            return Optional.empty();
        }
        var autorizados = dados.getDiasAutorizados();
        if (autorizados.isEmpty()) {
            return Optional.empty();
        }
        DiaDaSemana dia = DiaDaSemana.de(data.getDayOfWeek());
        if (autorizados.contains(dia)) {
            return Optional.empty();
        }
        return Optional.of("%s não atende %s; dias autorizados: %s."
                .formatted(posto.getNome(), dia.getDescricao(),
                        String.join(", ", autorizados.stream().map(DiaDaSemana::getDescricao).toList())));
    }

    @Transactional(readOnly = true)
    public List<Abastecimento> abastecimentosDoPeriodo(Long contratoId, LocalDate inicio, LocalDate fim) {
        return abastecimentos.doPeriodo(contratoId, inicio, fim);
    }

    @Transactional
    public void excluirAbastecimento(Long id, java.time.Instant momento) {
        abastecimentos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Abastecimento", id))
                .excluir(momento);
    }

    // ------------------------------------------------------------------ Serviços (RN-05)

    /** Dados de um serviço operacional. */
    public record DadosDeServico(
            TipoDeServico tipo,
            Fornecedor fornecedor,
            LocalDate data,
            BigDecimal valor,
            String descricao,
            String justificativa) {
    }

    /**
     * Lança um serviço, aplicando a RN-05 ao lava-jato.
     *
     * <p>"Uma vez por semana" é a semana do calendário — segunda a domingo —, e não sete
     * dias corridos a partir do último. A diferença importa: com janela corrida, lavar na
     * sexta e na segunda seguinte violaria a regra, embora sejam semanas diferentes para
     * quem organiza a rotina da obra.
     */
    @Transactional
    public ServicoOperacional lancarServico(Long contratoId, DadosDeServico dados) {
        ContratoDeLocacao contrato = buscarContrato(contratoId);
        var servico = new ServicoOperacional(contrato, dados.tipo(), dados.data(), dados.valor());
        servico.alterarDados(dados.fornecedor(), dados.descricao());
        aplicarFrequencia(contratoId, servico, dados, null);
        return servicos.save(servico);
    }

    @Transactional
    public ServicoOperacional atualizarServico(Long id, DadosDeServico dados) {
        ServicoOperacional servico = servicos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço operacional", id));
        servico.alterarValor(dados.valor());
        servico.alterarDados(dados.fornecedor(), dados.descricao());
        aplicarFrequencia(servico.getContrato().getId(), servico, dados, id);
        return servico;
    }

    private void aplicarFrequencia(
            Long contratoId, ServicoOperacional servico, DadosDeServico dados, Long ignorar) {
        if (!dados.tipo().isLimitadoPorSemana()) {
            servico.registrarConformidade(false, null);
            return;
        }

        LocalDate segunda = dados.data().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate domingo = segunda.plusDays(6);
        boolean jaHouve = servicos.existeNaJanela(contratoId, dados.tipo(), segunda, domingo, ignorar);

        if (!jaHouve) {
            servico.registrarConformidade(false, null);
            return;
        }
        if (dados.justificativa() == null || dados.justificativa().isBlank()) {
            throw new NegocioException(
                    ErroOperacao.JUSTIFICATIVA_OBRIGATORIA,
                    ("Já houve lava-jato na semana de %s a %s. A RN-05 permite um por semana — "
                            + "informe a justificativa para registrar este assim mesmo.")
                            .formatted(segunda, domingo),
                    Map.of("inicioDaSemana", segunda.toString(), "fimDaSemana", domingo.toString()));
        }
        servico.registrarConformidade(true, dados.justificativa());
    }

    @Transactional(readOnly = true)
    public List<ServicoOperacional> servicosDoPeriodo(Long contratoId, LocalDate inicio, LocalDate fim) {
        return servicos.doPeriodo(contratoId, inicio, fim);
    }

    @Transactional
    public void excluirServico(Long id, java.time.Instant momento) {
        servicos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço operacional", id))
                .excluir(momento);
    }

    // ------------------------------------------------------------------

    private ContratoDeLocacao buscarContrato(Long id) {
        return contratos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", id));
    }
}
