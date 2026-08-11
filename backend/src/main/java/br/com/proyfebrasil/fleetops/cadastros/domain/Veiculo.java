package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
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
import java.time.Year;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * Veículo físico alugado de uma locadora.
 *
 * <p>Note que o veículo <strong>não</strong> conhece a obra nem o condutor: esse vínculo
 * pertence ao contrato de locação (Fase 2), porque o mesmo veículo pode passar por obras
 * e condutores diferentes ao longo do tempo. Colocar a obra aqui destruiria a capacidade
 * de responder "quem dirigia a placa X em 15/03?" exigida pela RN-18.
 */
@Entity
@Table(name = "veiculo")
@Audited
public class Veiculo extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "placa", length = 7, nullable = false)
    private String placa;

    @Column(name = "modelo", length = 120, nullable = false)
    private String modelo;

    @Column(name = "fabricante", length = 120)
    private String fabricante;

    @Column(name = "ano_fabricacao")
    private Integer anoFabricacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 20, nullable = false)
    private CategoriaVeiculo categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "combustivel", length = 20, nullable = false)
    private Combustivel combustivel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locadora_id", nullable = false)
    private Locadora locadora;

    @Column(name = "grupo_tarifario", length = 20)
    private String grupoTarifario;

    @Column(name = "codigo_interno", length = 40)
    private String codigoInterno;

    @Column(name = "possui_rastreador", nullable = false)
    private boolean possuiRastreador;

    @Column(name = "fornecedor_rastreador", length = 160)
    private String fornecedorRastreador;

    @Column(name = "possui_adesivo", nullable = false)
    private boolean possuiAdesivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusVeiculo status = StatusVeiculo.DISPONIVEL;

    @Column(name = "observacoes")
    private String observacoes;

    /** Construtor exigido pelo JPA. */
    protected Veiculo() {
    }

    public Veiculo(
            String placa,
            String modelo,
            CategoriaVeiculo categoria,
            Combustivel combustivel,
            Locadora locadora) {
        this.placa = Placa.normalizar(placa);
        this.modelo = exigirTexto(modelo, "modelo");
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
        this.combustivel = Objects.requireNonNull(combustivel, "combustível é obrigatório");
        this.locadora = Objects.requireNonNull(locadora, "locadora é obrigatória");
        this.status = StatusVeiculo.DISPONIVEL;
    }

    public void alterarIdentificacao(String placa, String modelo, String fabricante, Integer anoFabricacao) {
        this.placa = Placa.normalizar(placa);
        this.modelo = exigirTexto(modelo, "modelo");
        this.fabricante = fabricante;
        this.anoFabricacao = validarAno(anoFabricacao);
    }

    public void alterarClassificacao(
            CategoriaVeiculo categoria, Combustivel combustivel, Locadora locadora, String grupoTarifario) {
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
        this.combustivel = Objects.requireNonNull(combustivel, "combustível é obrigatório");
        this.locadora = Objects.requireNonNull(locadora, "locadora é obrigatória");
        this.grupoTarifario = grupoTarifario;
    }

    /**
     * Registra a presença de rastreador.
     *
     * <p>Sem rastreador, o fornecedor é limpo: manter o nome de uma equipadora em um
     * veículo sem equipamento produziria um cadastro que se contradiz.
     */
    public void definirRastreador(boolean possui, String fornecedor) {
        this.possuiRastreador = possui;
        this.fornecedorRastreador = possui ? fornecedor : null;
    }

    public void definirAdesivo(boolean possui) {
        this.possuiAdesivo = possui;
    }

    public void alterarCodigoInterno(String codigoInterno) {
        this.codigoInterno = codigoInterno;
    }

    public void alterarStatus(StatusVeiculo novoStatus) {
        this.status = Objects.requireNonNull(novoStatus, "status é obrigatório");
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    /**
     * Indica se o veículo exige teste de fumaça preta na retirada (RN-09).
     *
     * <p>O critério é o combustível, não a tração: um 4x4 a gasolina não emite fumaça
     * preta mensurável, enquanto qualquer veículo a diesel emite.
     */
    public boolean exigeTesteDeFumacaPreta() {
        return combustivel.exigeTesteDeFumacaPreta();
    }

    /** Placa formatada para exibição, ex.: {@code ABC-1D23}. */
    public String getPlacaFormatada() {
        return Placa.formatar(placa);
    }

    public Long getId() {
        return id;
    }

    public String getPlaca() {
        return placa;
    }

    public String getModelo() {
        return modelo;
    }

    public String getFabricante() {
        return fabricante;
    }

    public Integer getAnoFabricacao() {
        return anoFabricacao;
    }

    public CategoriaVeiculo getCategoria() {
        return categoria;
    }

    public Combustivel getCombustivel() {
        return combustivel;
    }

    public Locadora getLocadora() {
        return locadora;
    }

    public String getGrupoTarifario() {
        return grupoTarifario;
    }

    public String getCodigoInterno() {
        return codigoInterno;
    }

    public boolean isPossuiRastreador() {
        return possuiRastreador;
    }

    public String getFornecedorRastreador() {
        return fornecedorRastreador;
    }

    public boolean isPossuiAdesivo() {
        return possuiAdesivo;
    }

    public StatusVeiculo getStatus() {
        return status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    private static Integer validarAno(Integer ano) {
        if (ano == null) {
            return null;
        }
        int anoLimite = Year.now().getValue() + 1;
        if (ano < 1980 || ano > anoLimite) {
            throw new IllegalArgumentException(
                    "Ano de fabricação inválido: informe um valor entre 1980 e %d.".formatted(anoLimite));
        }
        return ano;
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
        if (!(outro instanceof Veiculo veiculo)) {
            return false;
        }
        return id != null && id.equals(veiculo.id);
    }

    @Override
    public int hashCode() {
        return Veiculo.class.hashCode();
    }
}
