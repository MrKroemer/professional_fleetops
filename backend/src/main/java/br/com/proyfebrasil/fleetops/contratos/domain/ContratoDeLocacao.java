package br.com.proyfebrasil.fleetops.contratos.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Contrato de locação de veículo — o agregado central do domínio (Seção 3.2).
 *
 * <p>Cada linha do controle geral de veículos é um contrato: uma obra, um condutor, um
 * veículo, uma data de retirada e um pacote de KM. É aqui que mora o vínculo operacional
 * — e não no veículo —, porque o mesmo veículo passa por obras e condutores diferentes
 * ao longo do tempo.
 *
 * <p>O veículo atual é um ponteiro mantido; a verdade histórica está em
 * {@link SubstituicaoVeiculo}. Assim "quem dirigia a placa X em 15/03?" continua
 * respondível (RN-18), e a listagem não precisa de subconsulta por linha.
 */
@Entity
@Table(name = "contrato_locacao")
@Audited
public class ContratoDeLocacao extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "obra_id", nullable = false)
    private Obra obra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locadora_id", nullable = false)
    private Locadora locadora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veiculo_atual_id")
    private Veiculo veiculoAtual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condutor_atual_id")
    private Condutor condutorAtual;

    @Column(name = "codigo_interno", length = 40)
    private String codigoInterno;

    @Column(name = "local_retirada", length = 180)
    private String localRetirada;

    @Column(name = "data_retirada")
    private LocalDate dataRetirada;

    @Column(name = "data_encerramento")
    private LocalDate dataEncerramento;

    @Column(name = "pacote_km_contratado")
    private Integer pacoteKmContratado;

    @Column(name = "valor_mensal_contratado", precision = 12, scale = 2)
    private BigDecimal valorMensalContratado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusContrato status = StatusContrato.ATIVO;

    @Column(name = "observacoes")
    private String observacoes;

    /**
     * Histórico de veículos.
     *
     * <p>Fora da trilha do Envers: o próprio histórico já é o registro temporal, e
     * versioná-lo produziria um histórico do histórico sem utilidade.
     */
    @NotAudited
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubstituicaoVeiculo> substituicoes = new ArrayList<>();

    /**
     * Histórico de condutores (RN-18).
     *
     * <p>Fora do Envers pelo mesmo motivo dos veículos: a lista já é o registro temporal.
     */
    @NotAudited
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TrocaCondutor> trocasDeCondutor = new ArrayList<>();

    /** Construtor exigido pelo JPA. */
    protected ContratoDeLocacao() {
    }

    public ContratoDeLocacao(Obra obra, Locadora locadora, LocalDate dataRetirada) {
        this.obra = Objects.requireNonNull(obra, "obra é obrigatória");
        this.locadora = Objects.requireNonNull(locadora, "locadora é obrigatória");
        this.dataRetirada = dataRetirada;
        this.status = StatusContrato.ATIVO;
    }

    public void alterarDadosDaLocacao(
            String codigoInterno,
            String localRetirada,
            Integer pacoteKmContratado,
            BigDecimal valorMensalContratado) {
        this.codigoInterno = codigoInterno;
        this.localRetirada = localRetirada;
        if (pacoteKmContratado != null && pacoteKmContratado <= 0) {
            throw new IllegalArgumentException("O pacote de KM contratado deve ser maior que zero.");
        }
        this.pacoteKmContratado = pacoteKmContratado;
        if (valorMensalContratado != null && valorMensalContratado.signum() < 0) {
            throw new IllegalArgumentException("O valor mensal contratado não pode ser negativo.");
        }
        this.valorMensalContratado = valorMensalContratado;
    }

    /**
     * Coloca um veículo no contrato, encerrando o período do anterior (RN-01).
     *
     * <p>Sem lacuna nem sobreposição: o período anterior termina no dia que antecede o
     * início do novo. É o que permite responder qual veículo estava no contrato em uma
     * data qualquer, sem ambiguidade.
     *
     * <p>A troca precisa começar <strong>depois</strong> do início do período em curso, e
     * não apenas não antes: substituir no mesmo dia da colocação deixaria o período
     * anterior terminando na véspera do próprio início — um intervalo negativo, que
     * nenhuma consulta temporal saberia interpretar.
     *
     * @throws IllegalArgumentException se a data não for posterior ao início do período
     *     em curso
     */
    public SubstituicaoVeiculo colocarVeiculo(Veiculo veiculo, LocalDate aPartirDe, String motivo) {
        Objects.requireNonNull(veiculo, "veículo é obrigatório");
        LocalDate inicio = aPartirDe == null ? LocalDate.now() : aPartirDe;

        Optional<SubstituicaoVeiculo> emCurso = periodoEmCurso();
        if (emCurso.isPresent()) {
            SubstituicaoVeiculo anterior = emCurso.get();
            if (!inicio.isAfter(anterior.getInicio())) {
                throw new IllegalArgumentException(
                        "A substituição precisa começar depois de %s, quando o veículo atual entrou no contrato."
                                .formatted(anterior.getInicio()));
            }
            anterior.encerrarEm(inicio.minusDays(1));
        }

        SubstituicaoVeiculo periodo = new SubstituicaoVeiculo(this, veiculo, inicio, motivo);
        substituicoes.add(periodo);
        this.veiculoAtual = veiculo;
        return periodo;
    }

    /**
     * Coloca um condutor no contrato, encerrando o período do anterior (RN-18).
     *
     * <p>Simétrico a {@link #colocarVeiculo}: mesmo desenho de períodos, mesma exigência
     * de que a troca comece <strong>depois</strong> do início do período em curso — pela
     * mesma razão, já que fechar na véspera do próprio início daria intervalo negativo.
     *
     * <p>Acrescenta uma verificação que o veículo não tem: a RN-16 proíbe vincular
     * condutor com CNH vencida. A checagem é feita contra a data do vínculo, e não contra
     * hoje, para que o registro de um histórico passado não seja recusado por uma CNH que
     * venceu depois — o que aconteceu de fato não deixa de ter acontecido.
     *
     * <p>Verifica <strong>apenas</strong> a CNH, e não {@code podeSerVinculadoAContrato},
     * que também exige condutor ativo. A RN-16 fala da CNH; "ativo" descreve o vínculo
     * empregatício de hoje, e um condutor que saiu da empresa em 2023 ainda assim dirigiu
     * legitimamente em 2021. Usar o predicado completo recusaria 137 dos 189 condutores do
     * acervo por um motivo que a regra não prevê. Recusar um condutor inativo em uma troca
     * <em>operacional</em> é legítimo — e por isso essa checagem vive no serviço, onde se
     * sabe que a mudança é de agora e não um registro do passado.
     *
     * @throws NegocioException se a CNH estiver vencida na data do vínculo
     * @throws IllegalArgumentException se a data não for posterior ao início do período em curso
     */
    public TrocaCondutor colocarCondutor(Condutor condutor, LocalDate aPartirDe, String motivo) {
        Objects.requireNonNull(condutor, "condutor é obrigatório");
        LocalDate inicio = aPartirDe == null ? LocalDate.now() : aPartirDe;

        if (condutor.cnhVencidaEm(inicio)) {
            throw new NegocioException(
                    ErroContrato.CONDUTOR_COM_CNH_VENCIDA,
                    "A CNH de %s estava vencida em %s (validade %s). Regularize antes de vincular."
                            .formatted(condutor.getNome(), inicio, condutor.getCnhValidade()));
        }

        Optional<TrocaCondutor> emCurso = periodoDeCondutorEmCurso();
        if (emCurso.isPresent()) {
            TrocaCondutor anterior = emCurso.get();
            if (!inicio.isAfter(anterior.getInicio())) {
                throw new IllegalArgumentException(
                        "A troca de condutor precisa começar depois de %s, quando o condutor atual assumiu."
                                .formatted(anterior.getInicio()));
            }
            anterior.encerrarEm(inicio.minusDays(1));
        }

        TrocaCondutor periodo = new TrocaCondutor(this, condutor, inicio, motivo);
        trocasDeCondutor.add(periodo);
        this.condutorAtual = condutor;
        return periodo;
    }

    /**
     * Ajusta apenas o ponteiro do condutor, sem abrir período.
     *
     * <p>Existe para a correção de cadastro — trocar o condutor errado pelo certo em um
     * contrato recém-lançado —, e não para troca operacional, que é {@link #colocarCondutor}
     * e precisa deixar rastro. Não use para representar uma mudança que de fato ocorreu:
     * o histórico ficaria mudo sobre ela.
     */
    public void corrigirCondutorAtual(Condutor condutor) {
        this.condutorAtual = condutor;
        periodoDeCondutorEmCurso().ifPresent(periodo -> trocasDeCondutor.remove(periodo));
        if (condutor != null) {
            LocalDate desde = dataRetirada == null ? LocalDate.now() : dataRetirada;
            trocasDeCondutor.add(new TrocaCondutor(this, condutor, desde, "Correção de cadastro"));
        }
    }

    /**
     * Encerra o contrato.
     *
     * <p>O período do veículo em curso é fechado junto: um contrato encerrado com
     * período aberto deixaria o veículo eternamente "em uso" nos relatórios.
     */
    public void encerrar(StatusContrato novoStatus, LocalDate em) {
        if (Objects.requireNonNull(novoStatus, "status é obrigatório") == StatusContrato.ATIVO) {
            throw new IllegalArgumentException("Encerrar exige um status diferente de ATIVO.");
        }
        this.status = novoStatus;
        this.dataEncerramento = em;
        periodoEmCurso().ifPresent(periodo -> periodo.encerrarEm(em));
        periodoDeCondutorEmCurso().ifPresent(periodo -> periodo.encerrarEm(em));
    }

    public void alterarStatus(StatusContrato novoStatus) {
        this.status = Objects.requireNonNull(novoStatus, "status é obrigatório");
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    /** Período de veículo ainda aberto, se houver. */
    public Optional<SubstituicaoVeiculo> periodoEmCurso() {
        return substituicoes.stream().filter(periodo -> periodo.getFim() == null).findFirst();
    }

    /**
     * Veículo que estava no contrato na data informada (RN-18).
     *
     * <p>Responde pelo histórico, não pelo ponteiro: é o que permite reconstruir a
     * situação de qualquer data passada.
     */
    public Optional<Veiculo> veiculoEm(LocalDate data) {
        return substituicoes.stream()
                .filter(periodo -> periodo.cobre(data))
                .map(SubstituicaoVeiculo::getVeiculo)
                .findFirst();
    }

    /** Indica se o contrato está em operação. */
    public boolean estaAtivo() {
        return status == StatusContrato.ATIVO && !isExcluida();
    }

    /** Período de condutor ainda aberto, se houver. */
    public Optional<TrocaCondutor> periodoDeCondutorEmCurso() {
        return trocasDeCondutor.stream().filter(periodo -> periodo.getFim() == null).findFirst();
    }

    /**
     * Condutor que estava no contrato na data informada (RN-18).
     *
     * <p>Contrapartida de {@link #veiculoEm}: juntas, as duas respondem "quem dirigia a
     * placa X em 15/03?" sem depender de nenhum ponteiro atual.
     */
    public Optional<Condutor> condutorEm(LocalDate data) {
        return trocasDeCondutor.stream()
                .filter(periodo -> periodo.cobre(data))
                .map(TrocaCondutor::getCondutor)
                .findFirst();
    }

    /** Períodos de condutor em ordem cronológica. */
    public List<TrocaCondutor> getTrocasDeCondutor() {
        return trocasDeCondutor.stream()
                .sorted(Comparator.comparing(TrocaCondutor::getInicio))
                .toList();
    }

    /** Quantidade de trocas de condutor — zero quando só houve o condutor original. */
    public int quantidadeDeTrocasDeCondutor() {
        return Math.max(trocasDeCondutor.size() - 1, 0);
    }

    /** Períodos em ordem cronológica. */
    public List<SubstituicaoVeiculo> getSubstituicoes() {
        return substituicoes.stream()
                .sorted(Comparator.comparing(SubstituicaoVeiculo::getInicio))
                .toList();
    }

    /** Quantidade de trocas de veículo já ocorridas — zero quando nunca houve substituição. */
    public int quantidadeDeSubstituicoes() {
        return Math.max(substituicoes.size() - 1, 0);
    }

    public Long getId() {
        return id;
    }

    public Obra getObra() {
        return obra;
    }

    public Locadora getLocadora() {
        return locadora;
    }

    public Veiculo getVeiculoAtual() {
        return veiculoAtual;
    }

    public Condutor getCondutorAtual() {
        return condutorAtual;
    }

    public String getCodigoInterno() {
        return codigoInterno;
    }

    public String getLocalRetirada() {
        return localRetirada;
    }

    public LocalDate getDataRetirada() {
        return dataRetirada;
    }

    public LocalDate getDataEncerramento() {
        return dataEncerramento;
    }

    public Integer getPacoteKmContratado() {
        return pacoteKmContratado;
    }

    public BigDecimal getValorMensalContratado() {
        return valorMensalContratado;
    }

    public StatusContrato getStatus() {
        return status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ContratoDeLocacao contrato)) {
            return false;
        }
        return id != null && id.equals(contrato.id);
    }

    @Override
    public int hashCode() {
        return ContratoDeLocacao.class.hashCode();
    }
}
