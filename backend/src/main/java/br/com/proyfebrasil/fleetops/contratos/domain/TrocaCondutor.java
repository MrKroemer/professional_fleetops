package br.com.proyfebrasil.fleetops.contratos.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * O período de um condutor dentro de um contrato (RN-18).
 *
 * <p>A Seção 3.2 descreve a troca como um evento "com data e condutor anterior/novo".
 * Esta entidade guarda, em vez disso, um período por condutor — e anterior e novo saem
 * dos períodos vizinhos, que a linha do tempo já ordena.
 *
 * <p>A diferença aparece na pergunta que a RN-18 exige responder. "Quem dirigia em
 * 15/03?" contra períodos é uma busca por intervalo. Contra eventos, seria preciso ler
 * todos os eventos anteriores àquela data e dobrá-los em ordem — mais caro, e capaz de
 * responder errado em silêncio se um evento faltar. Com períodos, um buraco no histórico
 * é visível no próprio dado, e a restrição de exclusão do banco impede sobreposição.
 *
 * <p>{@code fim} nulo significa período em curso — a mesma convenção de
 * {@link SubstituicaoVeiculo}, deliberadamente: as duas linhas do tempo aparecem lado a
 * lado na tela do contrato, e divergir na representação confundiria quem lê o código e
 * quem lê a tela.
 */
@Entity
@Table(name = "troca_condutor")
public class TrocaCondutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condutor_id", nullable = false)
    private Condutor condutor;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    @Column(name = "fim")
    private LocalDate fim;

    @Column(name = "motivo", length = 300)
    private String motivo;

    /** Construtor exigido pelo JPA. */
    protected TrocaCondutor() {
    }

    TrocaCondutor(ContratoDeLocacao contrato, Condutor condutor, LocalDate inicio, String motivo) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.condutor = Objects.requireNonNull(condutor, "condutor é obrigatório");
        this.inicio = Objects.requireNonNull(inicio, "início é obrigatório");
        this.motivo = motivo;
    }

    /**
     * Fecha o período.
     *
     * @throws IllegalArgumentException se a data for anterior ao início
     */
    void encerrarEm(LocalDate data) {
        if (data != null && data.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período não pode ser anterior ao início.");
        }
        this.fim = data;
    }

    /** Indica se este período cobre a data informada. */
    public boolean cobre(LocalDate data) {
        if (data == null || data.isBefore(inicio)) {
            return false;
        }
        return fim == null || !data.isAfter(fim);
    }

    /** Indica se o período ainda está aberto. */
    public boolean emCurso() {
        return fim == null;
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public Condutor getCondutor() {
        return condutor;
    }

    public LocalDate getInicio() {
        return inicio;
    }

    public LocalDate getFim() {
        return fim;
    }

    public String getMotivo() {
        return motivo;
    }
}
