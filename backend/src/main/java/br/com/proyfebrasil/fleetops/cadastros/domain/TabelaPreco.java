package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.BatchSize;

/**
 * Tabela de preços de uma locadora para um ano de vigência (RN-14).
 *
 * <p>Existe uma tabela por locadora e ano. Lançamentos consultam a vigência da
 * <strong>competência</strong> do lançamento, nunca a mais recente: um fechamento de
 * março de 2025 reprocessado em 2026 tem de continuar usando os preços de 2025.
 *
 * <p>Pacotes de KM são linhas, e não colunas, porque variam por locadora — a Unidas
 * trabalha com 3000/4500/5000/6000 e a Localiza com 3000/4000/5000. Nas planilhas
 * atuais isso obriga a manter duas grades lado a lado.
 */
@Entity
@Table(name = "tabela_preco")
public class TabelaPreco extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locadora_id", nullable = false)
    private Locadora locadora;

    @Column(name = "ano_vigencia", nullable = false)
    private int anoVigencia;

    @Column(name = "observacoes")
    private String observacoes;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "tabelaPreco", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GrupoTarifario> grupos = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "tabelaPreco", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PrecoKmExcedente> precosKmExcedente = new ArrayList<>();

    /** Construtor exigido pelo JPA. */
    protected TabelaPreco() {
    }

    public TabelaPreco(Locadora locadora, int anoVigencia) {
        this.locadora = Objects.requireNonNull(locadora, "locadora é obrigatória");
        this.anoVigencia = validarAno(anoVigencia);
    }

    public void alterarVigencia(int anoVigencia) {
        this.anoVigencia = validarAno(anoVigencia);
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    // -----------------------------------------------------------------
    // Grupos tarifários
    // -----------------------------------------------------------------

    /**
     * Acrescenta um grupo tarifário.
     *
     * @throws IllegalArgumentException se já existir grupo com o mesmo código na vigência
     */
    public GrupoTarifario adicionarGrupo(String codigo, String veiculosDoGrupo, CategoriaVeiculo categoria) {
        String normalizado = normalizarCodigo(codigo);
        if (buscarGrupo(normalizado).isPresent()) {
            throw new IllegalArgumentException(
                    "Já existe um grupo com o código \"%s\" nesta vigência.".formatted(normalizado));
        }
        GrupoTarifario grupo = new GrupoTarifario(this, normalizado, veiculosDoGrupo, categoria);
        grupos.add(grupo);
        return grupo;
    }

    /** Remove todos os grupos, usado ao substituir a grade inteira em uma edição. */
    public void limparGrupos() {
        grupos.clear();
    }

    /** Localiza um grupo pelo código, ignorando diferenças de caixa. */
    public Optional<GrupoTarifario> buscarGrupo(String codigo) {
        String normalizado = normalizarCodigo(codigo);
        return grupos.stream().filter(grupo -> grupo.getCodigo().equals(normalizado)).findFirst();
    }

    /**
     * Valor mensal contratado para um grupo e pacote de KM.
     *
     * @return o valor, ou vazio se o grupo ou o pacote não estiverem cadastrados
     */
    public Optional<BigDecimal> valorMensal(String codigoDoGrupo, int pacoteKm) {
        return buscarGrupo(codigoDoGrupo).flatMap(grupo -> grupo.valorDoPacote(pacoteKm));
    }

    // -----------------------------------------------------------------
    // KM excedente (RN-06)
    // -----------------------------------------------------------------

    /**
     * Define o valor do KM excedente para uma categoria.
     *
     * @param pacoteKm pacote a que o valor se aplica; {@code null} vale para todos os
     *     pacotes — a Unidas cobra um valor único por categoria, enquanto a Localiza
     *     varia por pacote
     */
    public PrecoKmExcedente definirKmExcedente(CategoriaVeiculo categoria, Integer pacoteKm, BigDecimal valorKm) {
        precosKmExcedente.removeIf(preco ->
                preco.getCategoria() == categoria && Objects.equals(preco.getPacoteKm(), pacoteKm));
        PrecoKmExcedente preco = new PrecoKmExcedente(this, categoria, pacoteKm, valorKm);
        precosKmExcedente.add(preco);
        return preco;
    }

    /** Remove todos os preços de KM excedente. */
    public void limparKmExcedente() {
        precosKmExcedente.clear();
    }

    /**
     * Valor do KM excedente aplicável (RN-06).
     *
     * <p>O valor específico do pacote tem precedência sobre o valor geral da categoria:
     * quando a locadora diferencia por pacote, é essa a regra contratada; o valor geral
     * serve de padrão para quem não diferencia.
     */
    public Optional<BigDecimal> valorKmExcedente(CategoriaVeiculo categoria, int pacoteKm) {
        Optional<BigDecimal> especifico = precosKmExcedente.stream()
                .filter(preco -> preco.getCategoria() == categoria)
                .filter(preco -> Integer.valueOf(pacoteKm).equals(preco.getPacoteKm()))
                .map(PrecoKmExcedente::getValorKm)
                .findFirst();
        if (especifico.isPresent()) {
            return especifico;
        }
        return precosKmExcedente.stream()
                .filter(preco -> preco.getCategoria() == categoria && preco.getPacoteKm() == null)
                .map(PrecoKmExcedente::getValorKm)
                .findFirst();
    }

    /** Grupos ordenados por código, para exibição estável. */
    public List<GrupoTarifario> getGrupos() {
        return grupos.stream().sorted(Comparator.comparing(GrupoTarifario::getCodigo)).toList();
    }

    public List<PrecoKmExcedente> getPrecosKmExcedente() {
        return List.copyOf(precosKmExcedente);
    }

    public Long getId() {
        return id;
    }

    public Locadora getLocadora() {
        return locadora;
    }

    public int getAnoVigencia() {
        return anoVigencia;
    }

    public String getObservacoes() {
        return observacoes;
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("O código do grupo tarifário é obrigatório.");
        }
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static int validarAno(int ano) {
        if (ano < 2000 || ano > 2100) {
            throw new IllegalArgumentException("Ano de vigência inválido: informe um valor entre 2000 e 2100.");
        }
        return ano;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof TabelaPreco tabela)) {
            return false;
        }
        return id != null && id.equals(tabela.id);
    }

    @Override
    public int hashCode() {
        return TabelaPreco.class.hashCode();
    }
}
