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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * Conferência de uma competência — e <strong>somente</strong> a conferência.
 *
 * <p>A Seção 3.3 descreve o fechamento mensal com km inicial, km final, km percorrida,
 * consumo total e número de abastecimentos. Nenhum desses campos existe nesta entidade,
 * por decisão da RN-21: todo valor calculado é derivado dos lançamentos, nunca armazenado
 * como fonte de verdade editável. A mesma Seção 3.3 confirma — "gerado automaticamente a
 * partir dos lançamentos, nunca digitado".
 *
 * <p>Guardar os totais criaria uma segunda verdade. A nota de um abastecimento chega dias
 * depois do mês fechar, e o lançamento retroativo é rotina; com os totais gravados, o
 * número no banco passaria a divergir da soma dos lançamentos sem que nada acusasse.
 *
 * <p>O que sobra aqui é o que não se deriva: se um humano conferiu aquela competência,
 * quando, e o que anotou. Os números vêm de
 * {@code ServicoDeFechamentoMensal}, calculados na leitura.
 */
@Entity
@Table(name = "fechamento_mensal")
@Audited
public class FechamentoMensal extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusDoFechamento status = StatusDoFechamento.ABERTO;

    @Column(name = "conferido_em")
    private Instant conferidoEm;

    @Column(name = "conferido_por", length = 180)
    private String conferidoPor;

    @Column(name = "observacoes")
    private String observacoes;

    /** Construtor exigido pelo JPA. */
    protected FechamentoMensal() {
    }

    public FechamentoMensal(ContratoDeLocacao contrato, YearMonth competencia) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.competencia = Objects.requireNonNull(competencia, "competência é obrigatória").atDay(1);
    }

    /**
     * Marca a competência como conferida.
     *
     * <p>Não congela número nenhum — não há número aqui para congelar. Registra que alguém
     * olhou os lançamentos daquele mês e os deu por bons. Se um lançamento entrar depois,
     * os totais mudam, e é isso que se quer: a conferência passa a estar desatualizada, o
     * que é visível, em vez de o total ficar errado, o que não seria.
     *
     * @throws NegocioException se a competência já estiver conferida
     */
    public void conferir(String usuario, Instant quando, String observacoes) {
        if (status == StatusDoFechamento.CONFERIDO) {
            throw new NegocioException(
                    ErroOperacao.COMPETENCIA_CONFERIDA,
                    "A competência %s já foi conferida em %s. Reabra antes de conferir de novo."
                            .formatted(YearMonth.from(competencia), conferidoEm));
        }
        this.status = StatusDoFechamento.CONFERIDO;
        this.conferidoEm = Objects.requireNonNull(quando, "momento da conferência é obrigatório");
        this.conferidoPor = usuario;
        this.observacoes = observacoes;
    }

    /** Devolve a competência à edição — um lançamento atrasado costuma exigir isso. */
    public void reabrir() {
        this.status = StatusDoFechamento.ABERTO;
        this.conferidoEm = null;
        this.conferidoPor = null;
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public boolean estaConferido() {
        return status == StatusDoFechamento.CONFERIDO;
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

    public StatusDoFechamento getStatus() {
        return status;
    }

    public Instant getConferidoEm() {
        return conferidoEm;
    }

    public String getConferidoPor() {
        return conferidoPor;
    }

    public String getObservacoes() {
        return observacoes;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof FechamentoMensal fechamento)) {
            return false;
        }
        return id != null && id.equals(fechamento.id);
    }

    @Override
    public int hashCode() {
        return FechamentoMensal.class.hashCode();
    }
}
