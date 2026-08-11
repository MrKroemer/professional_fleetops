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
 * Dados específicos de um lava-jato credenciado.
 *
 * <p>A frequência permitida sustenta a RN-05 (por padrão, uma lavagem por veículo por
 * semana) e os preços por categoria permitem conferir o que é faturado — hoje esses
 * valores vivem em colunas soltas da planilha de fornecedores.
 */
@Entity
@Table(name = "fornecedor_lava_jato")
public class DadosLavaJato {

    /** Frequência padrão quando o credenciamento não especifica outra (RN-05). */
    public static final int SERVICOS_POR_SEMANA_PADRAO = 1;

    @Id
    @Column(name = "fornecedor_id")
    private Long fornecedorId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "servicos_por_semana", nullable = false)
    private int servicosPorSemana = SERVICOS_POR_SEMANA_PADRAO;

    @Column(name = "preco_passeio", precision = 12, scale = 2)
    private BigDecimal precoPasseio;

    @Column(name = "preco_suv", precision = 12, scale = 2)
    private BigDecimal precoSuv;

    @Column(name = "preco_quatro_x_quatro", precision = 12, scale = 2)
    private BigDecimal precoQuatroXQuatro;

    /** Construtor exigido pelo JPA. */
    protected DadosLavaJato() {
    }

    public DadosLavaJato(
            int servicosPorSemana,
            BigDecimal precoPasseio,
            BigDecimal precoSuv,
            BigDecimal precoQuatroXQuatro) {
        definirFrequencia(servicosPorSemana);
        definirPrecos(precoPasseio, precoSuv, precoQuatroXQuatro);
    }

    void vincularA(Fornecedor fornecedor) {
        this.fornecedor = fornecedor;
    }

    public void definirFrequencia(int servicosPorSemana) {
        if (servicosPorSemana < 1 || servicosPorSemana > 7) {
            throw new IllegalArgumentException(
                    "A frequência de lava-jato deve ficar entre 1 e 7 serviços por semana.");
        }
        this.servicosPorSemana = servicosPorSemana;
    }

    public void definirPrecos(BigDecimal passeio, BigDecimal suv, BigDecimal quatroXQuatro) {
        this.precoPasseio = normalizarPreco(passeio, "passeio");
        this.precoSuv = normalizarPreco(suv, "SUV");
        this.precoQuatroXQuatro = normalizarPreco(quatroXQuatro, "4x4");
    }

    /** Preço acordado para a categoria, ou {@code null} se não houver preço cadastrado. */
    public BigDecimal precoPara(CategoriaVeiculo categoria) {
        return switch (categoria) {
            case PASSEIO, UTILITARIO -> precoPasseio;
            case SUV -> precoSuv;
            case QUATRO_X_QUATRO -> precoQuatroXQuatro;
        };
    }

    private static BigDecimal normalizarPreco(BigDecimal valor, String rotulo) {
        if (valor == null) {
            return null;
        }
        if (Dinheiro.eNegativo(valor)) {
            throw new IllegalArgumentException("O preço de lava-jato para %s não pode ser negativo.".formatted(rotulo));
        }
        return Dinheiro.normalizar(valor);
    }

    public int getServicosPorSemana() {
        return servicosPorSemana;
    }

    public BigDecimal getPrecoPasseio() {
        return precoPasseio;
    }

    public BigDecimal getPrecoSuv() {
        return precoSuv;
    }

    public BigDecimal getPrecoQuatroXQuatro() {
        return precoQuatroXQuatro;
    }

    public Long getFornecedorId() {
        return fornecedorId;
    }
}
