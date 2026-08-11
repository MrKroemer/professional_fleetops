package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.money.Dinheiro;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.BatchSize;

/**
 * Grupo tarifário dentro de uma vigência, ex.: {@code AM — KWID/Mobi} na Unidas.
 *
 * <p>O grupo é a unidade de precificação da locadora: modelos diferentes com o mesmo
 * porte compartilham grupo e, portanto, preço.
 */
@Entity
@Table(name = "grupo_tarifario")
public class GrupoTarifario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tabela_preco_id", nullable = false)
    private TabelaPreco tabelaPreco;

    @Column(name = "codigo", length = 20, nullable = false)
    private String codigo;

    @Column(name = "veiculos_do_grupo", length = 300, nullable = false)
    private String veiculosDoGrupo;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 20, nullable = false)
    private CategoriaVeiculo categoria;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "grupoTarifario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PrecoPacoteKm> pacotes = new ArrayList<>();

    /** Construtor exigido pelo JPA. */
    protected GrupoTarifario() {
    }

    GrupoTarifario(TabelaPreco tabelaPreco, String codigo, String veiculosDoGrupo, CategoriaVeiculo categoria) {
        this.tabelaPreco = Objects.requireNonNull(tabelaPreco, "tabela de preço é obrigatória");
        this.codigo = codigo;
        this.veiculosDoGrupo = exigirTexto(veiculosDoGrupo, "veículos do grupo");
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
    }

    /**
     * Define o valor mensal de um pacote de KM, substituindo o anterior se já existir.
     *
     * @throws IllegalArgumentException se o pacote não for positivo ou o valor for negativo
     */
    public PrecoPacoteKm definirPacote(int pacoteKm, BigDecimal valorMensal) {
        if (pacoteKm <= 0) {
            throw new IllegalArgumentException("O pacote de KM deve ser maior que zero.");
        }
        if (valorMensal == null || Dinheiro.eNegativo(valorMensal)) {
            throw new IllegalArgumentException("O valor mensal do pacote não pode ser negativo.");
        }
        pacotes.removeIf(pacote -> pacote.getPacoteKm() == pacoteKm);
        PrecoPacoteKm pacote = new PrecoPacoteKm(this, pacoteKm, Dinheiro.normalizar(valorMensal));
        pacotes.add(pacote);
        return pacote;
    }

    /** Valor mensal do pacote informado, se cadastrado. */
    public Optional<BigDecimal> valorDoPacote(int pacoteKm) {
        return pacotes.stream()
                .filter(pacote -> pacote.getPacoteKm() == pacoteKm)
                .map(PrecoPacoteKm::getValorMensal)
                .findFirst();
    }

    public void alterarDados(String veiculosDoGrupo, CategoriaVeiculo categoria) {
        this.veiculosDoGrupo = exigirTexto(veiculosDoGrupo, "veículos do grupo");
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
    }

    /** Pacotes ordenados por quilometragem, como aparecem nas tabelas das locadoras. */
    public List<PrecoPacoteKm> getPacotes() {
        return pacotes.stream().sorted(Comparator.comparingInt(PrecoPacoteKm::getPacoteKm)).toList();
    }

    public Long getId() {
        return id;
    }

    public TabelaPreco getTabelaPreco() {
        return tabelaPreco;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getVeiculosDoGrupo() {
        return veiculosDoGrupo;
    }

    public CategoriaVeiculo getCategoria() {
        return categoria;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo %s é obrigatório.".formatted(campo));
        }
        return valor.trim();
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof GrupoTarifario grupo)) {
            return false;
        }
        return id != null && id.equals(grupo.id);
    }

    @Override
    public int hashCode() {
        return GrupoTarifario.class.hashCode();
    }
}
