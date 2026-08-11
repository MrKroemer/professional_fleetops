package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * Fornecedor credenciado: posto, lava-jato, borracharia, para-brisas, rastreador,
 * gráfica ou oficina.
 *
 * <p>Os dados que variam por tipo ficam em entidades satélite, ligadas por chave
 * primária compartilhada. A alternativa — uma tabela larga com todas as colunas de
 * todos os tipos — deixaria a maioria dos campos nula em qualquer registro e
 * impediria restrições de integridade úteis.
 *
 * <p>O vínculo com obras é N:N porque um mesmo posto costuma atender várias frentes
 * próximas, como se vê nas planilhas de Uibaí e Ibipeba.
 *
 * <p>Esta entidade não é auditada pelo Envers: fornecedores mudam pouco e seu histórico
 * não sustenta nenhuma regra de negócio, ao contrário de contratos, multas e avarias.
 */
@Entity
@Table(name = "fornecedor")
public class Fornecedor extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoFornecedor tipo;

    @Column(name = "nome", length = 180, nullable = false)
    private String nome;

    @Column(name = "cidade", length = 120)
    private String cidade;

    @Column(name = "uf", length = 2)
    private String uf;

    @Column(name = "endereco", length = 300)
    private String endereco;

    @Column(name = "telefone", length = 120)
    private String telefone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "responsavel", length = 160)
    private String responsavel;

    @Column(name = "funcionamento", length = 200)
    private String funcionamento;

    @Column(name = "forma_faturamento", length = 200)
    private String formaFaturamento;

    @Column(name = "forma_pagamento", length = 200)
    private String formaPagamento;

    @Column(name = "credenciado_em")
    private LocalDate credenciadoEm;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "observacoes")
    private String observacoes;

    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "fornecedor_obra",
            joinColumns = @JoinColumn(name = "fornecedor_id"),
            inverseJoinColumns = @JoinColumn(name = "obra_id"))
    private Set<Obra> obras = new LinkedHashSet<>();

    @OneToOne(mappedBy = "fornecedor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DadosPosto dadosPosto;

    @OneToOne(mappedBy = "fornecedor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DadosLavaJato dadosLavaJato;

    @OneToOne(mappedBy = "fornecedor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DadosRastreador dadosRastreador;

    @OneToOne(mappedBy = "fornecedor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DadosGrafica dadosGrafica;

    /** Construtor exigido pelo JPA. */
    protected Fornecedor() {
    }

    public Fornecedor(TipoFornecedor tipo, String nome) {
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.nome = exigirTexto(nome, "nome");
        this.ativo = true;
    }

    public void alterarDadosBasicos(
            String nome,
            String cidade,
            String uf,
            String endereco,
            String telefone,
            String email,
            String responsavel) {
        this.nome = exigirTexto(nome, "nome");
        this.cidade = cidade;
        this.uf = uf == null || uf.isBlank() ? null : Obra.normalizarUf(uf);
        this.endereco = endereco;
        this.telefone = telefone;
        this.email = email;
        this.responsavel = responsavel;
    }

    public void alterarCondicoesComerciais(
            String funcionamento, String formaFaturamento, String formaPagamento, LocalDate credenciadoEm) {
        this.funcionamento = funcionamento;
        this.formaFaturamento = formaFaturamento;
        this.formaPagamento = formaPagamento;
        this.credenciadoEm = credenciadoEm;
    }

    /** Substitui integralmente as obras atendidas por este fornecedor. */
    public void definirObras(Set<Obra> novasObras) {
        this.obras.clear();
        if (novasObras != null) {
            this.obras.addAll(novasObras);
        }
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    // -----------------------------------------------------------------
    // Dados específicos por tipo
    // -----------------------------------------------------------------

    /**
     * Associa os dados de posto.
     *
     * @throws IllegalStateException se o fornecedor não for do tipo {@code POSTO}
     */
    public void definirDadosPosto(DadosPosto dados) {
        exigirTipo(TipoFornecedor.POSTO);
        this.dadosPosto = dados;
        if (dados != null) {
            dados.vincularA(this);
        }
    }

    /**
     * Associa os dados de lava-jato.
     *
     * @throws IllegalStateException se o fornecedor não for do tipo {@code LAVA_JATO}
     */
    public void definirDadosLavaJato(DadosLavaJato dados) {
        exigirTipo(TipoFornecedor.LAVA_JATO);
        this.dadosLavaJato = dados;
        if (dados != null) {
            dados.vincularA(this);
        }
    }

    /**
     * Associa os dados de rastreador.
     *
     * @throws IllegalStateException se o fornecedor não for do tipo {@code RASTREADOR}
     */
    public void definirDadosRastreador(DadosRastreador dados) {
        exigirTipo(TipoFornecedor.RASTREADOR);
        this.dadosRastreador = dados;
        if (dados != null) {
            dados.vincularA(this);
        }
    }

    /**
     * Associa os dados de gráfica.
     *
     * @throws IllegalStateException se o fornecedor não for do tipo {@code GRAFICA}
     */
    public void definirDadosGrafica(DadosGrafica dados) {
        exigirTipo(TipoFornecedor.GRAFICA);
        this.dadosGrafica = dados;
        if (dados != null) {
            dados.vincularA(this);
        }
    }

    /** Indica se o fornecedor está apto a receber lançamentos. */
    public boolean estaCredenciado() {
        return ativo && !isExcluida();
    }

    /** Indica se este fornecedor atende a obra informada. */
    public boolean atendeObra(Long obraId) {
        return obraId != null && obras.stream().anyMatch(obra -> obraId.equals(obra.getId()));
    }

    private void exigirTipo(TipoFornecedor esperado) {
        if (tipo != esperado) {
            throw new IllegalStateException(
                    "Dados de %s não se aplicam a um fornecedor do tipo %s."
                            .formatted(esperado.getDescricao(), tipo.getDescricao()));
        }
    }

    public Long getId() {
        return id;
    }

    public TipoFornecedor getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public String getCidade() {
        return cidade;
    }

    public String getUf() {
        return uf;
    }

    public String getEndereco() {
        return endereco;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public String getFuncionamento() {
        return funcionamento;
    }

    public String getFormaFaturamento() {
        return formaFaturamento;
    }

    public String getFormaPagamento() {
        return formaPagamento;
    }

    public LocalDate getCredenciadoEm() {
        return credenciadoEm;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Set<Obra> getObras() {
        return obras;
    }

    public DadosPosto getDadosPosto() {
        return dadosPosto;
    }

    public DadosLavaJato getDadosLavaJato() {
        return dadosLavaJato;
    }

    public DadosRastreador getDadosRastreador() {
        return dadosRastreador;
    }

    public DadosGrafica getDadosGrafica() {
        return dadosGrafica;
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
        if (!(outro instanceof Fornecedor fornecedor)) {
            return false;
        }
        return id != null && id.equals(fornecedor.id);
    }

    @Override
    public int hashCode() {
        return Fornecedor.class.hashCode();
    }
}
