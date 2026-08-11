package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Fatura mensal da locadora (RN-13).
 *
 * <p>A divergência é <strong>coluna gerada</strong> no banco, não campo. A RN-13 dá a
 * fórmula e a RN-21 manda derivá-la: gravada como campo comum, uma correção no valor
 * faturado deixaria a divergência velha no lugar, e a conferência passaria a afirmar algo
 * que os números não sustentam.
 *
 * <p>A segunda metade da RN-13 é uma proibição: divergência diferente de zero exige
 * status de conferência <em>diferente</em> de "OK", com observação. Ela vive em
 * {@link #alterarConferencia}, porque é ali que alguém tenta declarar a fatura correta.
 * Sem isso, uma fatura com R$ 400 a mais poderia ser marcada como conferida e sumir da
 * lista de pendências levando o prejuízo junto.
 */
@Entity
@Table(name = "fatura_locadora")
@Audited
public class FaturaDaLocadora extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @Column(name = "valor_contratado", precision = 12, scale = 2, nullable = false)
    private BigDecimal valorContratado = BigDecimal.ZERO;

    @Column(name = "valor_faturado", precision = 12, scale = 2, nullable = false)
    private BigDecimal valorFaturado = BigDecimal.ZERO;

    @Column(name = "extras_aprovados", precision = 12, scale = 2, nullable = false)
    private BigDecimal extrasAprovados = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_conferencia", length = 20, nullable = false)
    private StatusDeConferencia status = StatusDeConferencia.PENDENTE;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "numero_da_nota", length = 60)
    private String numeroDaNota;

    @Column(name = "vencimento")
    private LocalDate vencimento;

    /**
     * Divergência calculada pelo banco.
     *
     * <p>Fora do Envers: deriva de três colunas que já são versionadas, e uma cópia
     * versionada poderia divergir delas se a fórmula mudasse.
     */
    @NotAudited
    @Generated
    @Column(name = "divergencia", insertable = false, updatable = false)
    private BigDecimal divergencia;

    /** Construtor exigido pelo JPA. */
    protected FaturaDaLocadora() {
    }

    public FaturaDaLocadora(ContratoDeLocacao contrato, YearMonth competencia) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.competencia = Objects.requireNonNull(competencia, "competência é obrigatória").atDay(1);
    }

    public void alterarValores(
            BigDecimal valorContratado, BigDecimal valorFaturado, BigDecimal extrasAprovados) {
        this.valorContratado = naoNegativo(valorContratado, "valor contratado");
        this.valorFaturado = naoNegativo(valorFaturado, "valor faturado");
        this.extrasAprovados = naoNegativo(extrasAprovados, "extras aprovados");
        // O valor em memória acompanha a fórmula do banco até a próxima leitura; sem isto,
        // uma tela que edita e relê no mesmo ciclo mostraria a divergência anterior.
        this.divergencia = calcularDivergencia();
    }

    public void alterarDadosDaNota(String numeroDaNota, LocalDate vencimento) {
        this.numeroDaNota = numeroDaNota;
        this.vencimento = vencimento;
    }

    /**
     * Aplica a segunda metade da RN-13.
     *
     * @throws NegocioException ao declarar "OK" com divergência, ou ao mudar de status sem
     *     observação quando há divergência
     */
    public void alterarConferencia(StatusDeConferencia novoStatus, String observacoes) {
        Objects.requireNonNull(novoStatus, "status de conferência é obrigatório");
        BigDecimal apurada = calcularDivergencia();

        if (apurada.signum() != 0 && novoStatus != StatusDeConferencia.PENDENTE) {
            if (novoStatus.afirmaQueEstaCorreta()) {
                throw new NegocioException(
                        ErroOperacao.FATURA_DIVERGENTE_SEM_OBSERVACAO,
                        ("Esta fatura tem divergência de R$ %s e não pode ser marcada como conferida. "
                                + "Use contestação ou ajuste, explicando o motivo.").formatted(apurada),
                        Map.of("divergencia", apurada));
            }
            if (observacoes == null || observacoes.isBlank()) {
                throw new NegocioException(
                        ErroOperacao.FATURA_DIVERGENTE_SEM_OBSERVACAO,
                        "Uma fatura com divergência de R$ %s exige observação explicando a tratativa."
                                .formatted(apurada),
                        Map.of("divergencia", apurada));
            }
        }

        this.status = novoStatus;
        this.observacoes = observacoes;
    }

    /**
     * Divergência da RN-13.
     *
     * <p>Antes da gravação a coluna gerada ainda não existe; nesse instante o valor sai do
     * cálculo em Java, que é a mesma fórmula.
     */
    public BigDecimal getDivergencia() {
        return divergencia != null ? divergencia : calcularDivergencia();
    }

    private BigDecimal calcularDivergencia() {
        return valorFaturado.subtract(valorContratado.add(extrasAprovados));
    }

    /** Indica se a fatura ainda precisa de tratativa. */
    public boolean exigeTratativa() {
        return getDivergencia().signum() != 0 && status != StatusDeConferencia.AJUSTADA;
    }

    private static BigDecimal naoNegativo(BigDecimal valor, String campo) {
        BigDecimal resolvido = valor == null ? BigDecimal.ZERO : valor;
        if (resolvido.signum() < 0) {
            throw new IllegalArgumentException("O %s não pode ser negativo.".formatted(campo));
        }
        return resolvido;
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public YearMonth getCompetencia() {
        return YearMonth.from(competencia);
    }

    public BigDecimal getValorContratado() {
        return valorContratado;
    }

    public BigDecimal getValorFaturado() {
        return valorFaturado;
    }

    public BigDecimal getExtrasAprovados() {
        return extrasAprovados;
    }

    public StatusDeConferencia getStatus() {
        return status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getNumeroDaNota() {
        return numeroDaNota;
    }

    public LocalDate getVencimento() {
        return vencimento;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof FaturaDaLocadora fatura)) {
            return false;
        }
        return id != null && id.equals(fatura.id);
    }

    @Override
    public int hashCode() {
        return FaturaDaLocadora.class.hashCode();
    }
}
