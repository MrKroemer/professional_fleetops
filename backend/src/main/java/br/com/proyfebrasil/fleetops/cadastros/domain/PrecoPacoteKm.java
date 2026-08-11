package br.com.proyfebrasil.fleetops.cadastros.domain;

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
import java.util.Objects;

/** Valor mensal de um pacote de quilometragem dentro de um grupo tarifário. */
@Entity
@Table(name = "preco_pacote_km")
public class PrecoPacoteKm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_tarifario_id", nullable = false)
    private GrupoTarifario grupoTarifario;

    @Column(name = "pacote_km", nullable = false)
    private int pacoteKm;

    @Column(name = "valor_mensal", precision = 12, scale = 2, nullable = false)
    private BigDecimal valorMensal;

    /** Construtor exigido pelo JPA. */
    protected PrecoPacoteKm() {
    }

    PrecoPacoteKm(GrupoTarifario grupoTarifario, int pacoteKm, BigDecimal valorMensal) {
        this.grupoTarifario = Objects.requireNonNull(grupoTarifario, "grupo tarifário é obrigatório");
        this.pacoteKm = pacoteKm;
        this.valorMensal = valorMensal;
    }

    public Long getId() {
        return id;
    }

    public GrupoTarifario getGrupoTarifario() {
        return grupoTarifario;
    }

    public int getPacoteKm() {
        return pacoteKm;
    }

    public BigDecimal getValorMensal() {
        return valorMensal;
    }
}
