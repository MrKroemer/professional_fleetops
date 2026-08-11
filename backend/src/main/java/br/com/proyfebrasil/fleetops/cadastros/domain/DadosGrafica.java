package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.money.Dinheiro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Dados específicos de uma gráfica credenciada.
 *
 * <p>Adesivo e imã são alternativas para a identificação visual do veículo: o imã sai
 * mais caro, mas é removível — o que importa em veículo alugado, onde o adesivo pode
 * gerar cobrança da locadora na devolução.
 */
@Entity
@Table(name = "fornecedor_grafica")
public class DadosGrafica {

    @Id
    @Column(name = "fornecedor_id")
    private Long fornecedorId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "tamanho_adesivo", length = 40)
    private String tamanhoAdesivo;

    @Column(name = "preco_adesivo", precision = 12, scale = 2)
    private BigDecimal precoAdesivo;

    @Column(name = "tamanho_ima", length = 40)
    private String tamanhoIma;

    @Column(name = "preco_ima", precision = 12, scale = 2)
    private BigDecimal precoIma;

    /** Construtor exigido pelo JPA. */
    protected DadosGrafica() {
    }

    public DadosGrafica(
            String tamanhoAdesivo, BigDecimal precoAdesivo, String tamanhoIma, BigDecimal precoIma) {
        this.tamanhoAdesivo = tamanhoAdesivo;
        this.tamanhoIma = tamanhoIma;
        definirPrecos(precoAdesivo, precoIma);
    }

    void vincularA(Fornecedor fornecedor) {
        this.fornecedor = fornecedor;
    }

    public void definirTamanhos(String tamanhoAdesivo, String tamanhoIma) {
        this.tamanhoAdesivo = tamanhoAdesivo;
        this.tamanhoIma = tamanhoIma;
    }

    public void definirPrecos(BigDecimal precoAdesivo, BigDecimal precoIma) {
        this.precoAdesivo = normalizarPreco(precoAdesivo, "adesivo");
        this.precoIma = normalizarPreco(precoIma, "imã");
    }

    private static BigDecimal normalizarPreco(BigDecimal valor, String rotulo) {
        if (valor == null) {
            return null;
        }
        if (Dinheiro.eNegativo(valor)) {
            throw new IllegalArgumentException("O preço de %s não pode ser negativo.".formatted(rotulo));
        }
        return Dinheiro.normalizar(valor);
    }

    public String getTamanhoAdesivo() {
        return tamanhoAdesivo;
    }

    public BigDecimal getPrecoAdesivo() {
        return precoAdesivo;
    }

    public String getTamanhoIma() {
        return tamanhoIma;
    }

    public BigDecimal getPrecoIma() {
        return precoIma;
    }

    public Long getFornecedorId() {
        return fornecedorId;
    }
}
