package br.com.proyfebrasil.fleetops.contratos.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
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
 * O período de um veículo dentro de um contrato (RN-01).
 *
 * <p>A planilha atual registra as substituições em colunas repetidas — {@code MODELO2}
 * até {@code MODELO6} —, o que limita o histórico a seis trocas e impede qualquer
 * consulta por data. Aqui cada período é uma linha: sem teto, sem lacuna e sem
 * sobreposição.
 *
 * <p>{@code fim} nulo significa período em curso, e não período sem fim: é a forma
 * usual de representar "ainda vigente" sem inventar uma data futura arbitrária.
 */
@Entity
@Table(name = "substituicao_veiculo")
public class SubstituicaoVeiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    @Column(name = "fim")
    private LocalDate fim;

    @Column(name = "motivo", length = 300)
    private String motivo;

    /** Construtor exigido pelo JPA. */
    protected SubstituicaoVeiculo() {
    }

    SubstituicaoVeiculo(ContratoDeLocacao contrato, Veiculo veiculo, LocalDate inicio, String motivo) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.veiculo = Objects.requireNonNull(veiculo, "veículo é obrigatório");
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
            throw new IllegalArgumentException(
                    "O fim do período não pode ser anterior ao início.");
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

    public Veiculo getVeiculo() {
        return veiculo;
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
