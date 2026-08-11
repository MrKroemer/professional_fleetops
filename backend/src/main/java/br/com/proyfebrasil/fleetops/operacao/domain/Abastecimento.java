package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * Abastecimento de um contrato (RN-04).
 *
 * <p>A regra tem duas exigências e um escape. As exigências: posto credenciado da obra e
 * dia autorizado daquele posto. O escape: fora disso, o lançamento é aceito marcado como
 * <strong>não conforme</strong>, com justificativa obrigatória.
 *
 * <p>O escape é o ponto da regra, não uma concessão. Recusar o lançamento faria o gestor
 * simplesmente não registrar o abastecimento irregular — e o sistema perderia de uma vez
 * o custo e a não conformidade. Aceitar e marcar preserva os dois.
 *
 * <p>A avaliação de conformidade acontece no serviço, que conhece a obra do contrato e os
 * dias do posto. Aqui fica apenas a invariante que a marcação carrega: não conforme sem
 * justificativa não é um estado válido.
 */
@Entity
@Table(name = "abastecimento")
@Audited
public class Abastecimento extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    /**
     * Fornecedor do lançamento.
     *
     * <p>{@code NOT_AUDITED} porque {@link Fornecedor} não está na trilha do Envers — a
     * decisão é da Fase 1, e este lançamento não é motivo para mudá-la. A revisão guarda a
     * chave estrangeira; quem quiser o estado do fornecedor naquela data continua olhando o
     * cadastro atual, que é o que a Fase 1 escolheu ao deixá-lo fora.
     */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posto_id")
    private Fornecedor posto;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "valor", precision = 12, scale = 2, nullable = false)
    private BigDecimal valor;

    @Column(name = "litros", precision = 10, scale = 3)
    private BigDecimal litros;

    @Column(name = "km")
    private Integer km;

    @Column(name = "nao_conforme", nullable = false)
    private boolean naoConforme;

    @Column(name = "justificativa")
    private String justificativa;

    @Column(name = "observacao")
    private String observacao;

    /** Construtor exigido pelo JPA. */
    protected Abastecimento() {
    }

    public Abastecimento(ContratoDeLocacao contrato, LocalDate data, BigDecimal valor) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.data = Objects.requireNonNull(data, "data é obrigatória");
        alterarValor(valor);
    }

    public final void alterarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor é obrigatório");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("O valor do abastecimento não pode ser negativo.");
        }
        this.valor = valor;
    }

    public void alterarDados(Fornecedor posto, BigDecimal litros, Integer km, String observacao) {
        this.posto = posto;
        if (litros != null && litros.signum() <= 0) {
            throw new IllegalArgumentException("A quantidade de litros deve ser maior que zero.");
        }
        this.litros = litros;
        if (km != null && km < 0) {
            throw new IllegalArgumentException("A quilometragem não pode ser negativa.");
        }
        this.km = km;
        this.observacao = observacao;
    }

    /**
     * Marca ou desmarca a não conformidade (RN-04).
     *
     * @throws IllegalArgumentException se marcado como não conforme sem justificativa
     */
    public void registrarConformidade(boolean naoConforme, String justificativa) {
        if (naoConforme && (justificativa == null || justificativa.isBlank())) {
            throw new IllegalArgumentException(
                    "Um lançamento não conforme exige justificativa — é o que a RN-04 pede em troca de aceitá-lo.");
        }
        this.naoConforme = naoConforme;
        this.justificativa = naoConforme ? justificativa : null;
    }

    /**
     * Preço por litro.
     *
     * <p>Derivado (RN-21). Serve à conferência: um valor fora da faixa do mercado é o
     * primeiro sinal de nota trocada ou de litragem digitada errado.
     */
    public Optional<BigDecimal> precoPorLitro() {
        if (litros == null || litros.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(valor.divide(litros, 3, RoundingMode.HALF_UP));
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public Optional<Fornecedor> getPosto() {
        return Optional.ofNullable(posto);
    }

    public LocalDate getData() {
        return data;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getLitros() {
        return litros;
    }

    public Integer getKm() {
        return km;
    }

    public boolean isNaoConforme() {
        return naoConforme;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public String getObservacao() {
        return observacao;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Abastecimento abastecimento)) {
            return false;
        }
        return id != null && id.equals(abastecimento.id);
    }

    @Override
    public int hashCode() {
        return Abastecimento.class.hashCode();
    }
}
