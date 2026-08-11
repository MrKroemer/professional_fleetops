package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.money.Dinheiro;
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
import java.util.Objects;

/**
 * Valor cobrado por quilômetro excedente ao pacote contratado (RN-06).
 *
 * <p>{@code pacoteKm} nulo indica que o valor vale para todos os pacotes da categoria.
 * Isso acomoda as duas práticas encontradas: a Unidas cobra um valor único por categoria
 * (R$ 0,60 passeio, R$ 1,20 SUV, R$ 3,20 4x4), enquanto a Localiza varia o valor conforme
 * o pacote contratado.
 */
@Entity
@Table(name = "preco_km_excedente")
public class PrecoKmExcedente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tabela_preco_id", nullable = false)
    private TabelaPreco tabelaPreco;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 20, nullable = false)
    private CategoriaVeiculo categoria;

    @Column(name = "pacote_km")
    private Integer pacoteKm;

    @Column(name = "valor_km", precision = 12, scale = 2, nullable = false)
    private BigDecimal valorKm;

    /** Construtor exigido pelo JPA. */
    protected PrecoKmExcedente() {
    }

    PrecoKmExcedente(TabelaPreco tabelaPreco, CategoriaVeiculo categoria, Integer pacoteKm, BigDecimal valorKm) {
        this.tabelaPreco = Objects.requireNonNull(tabelaPreco, "tabela de preço é obrigatória");
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
        if (pacoteKm != null && pacoteKm <= 0) {
            throw new IllegalArgumentException("O pacote de KM deve ser maior que zero.");
        }
        if (valorKm == null || Dinheiro.eNegativo(valorKm)) {
            throw new IllegalArgumentException("O valor do KM excedente não pode ser negativo.");
        }
        this.pacoteKm = pacoteKm;
        this.valorKm = Dinheiro.normalizar(valorKm);
    }

    public Long getId() {
        return id;
    }

    public TabelaPreco getTabelaPreco() {
        return tabelaPreco;
    }

    public CategoriaVeiculo getCategoria() {
        return categoria;
    }

    /** Pacote específico, ou {@code null} quando o valor vale para todos. */
    public Integer getPacoteKm() {
        return pacoteKm;
    }

    public BigDecimal getValorKm() {
        return valorKm;
    }
}
