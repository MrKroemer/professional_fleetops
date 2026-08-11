package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * Obra: a frente de trabalho onde os veículos são alocados.
 *
 * <p>É a dimensão pela qual toda a operação é organizada — fornecedores são credenciados
 * por obra, custos são apurados por obra e os controles de KM hoje existem em um arquivo
 * por obra.
 */
@Entity
@Table(name = "obra")
@Audited
public class Obra extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 20, nullable = false)
    private String codigo;

    @Column(name = "nome", length = 180, nullable = false)
    private String nome;

    @Column(name = "cliente", length = 180)
    private String cliente;

    @Column(name = "cidade", length = 120, nullable = false)
    private String cidade;

    @Column(name = "uf", length = 2, nullable = false)
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusObra status = StatusObra.ATIVA;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "observacoes")
    private String observacoes;

    /** Construtor exigido pelo JPA. */
    protected Obra() {
    }

    public Obra(String codigo, String nome, String cidade, String uf) {
        this.codigo = exigirTexto(codigo, "código");
        this.nome = exigirTexto(nome, "nome");
        this.cidade = exigirTexto(cidade, "cidade");
        this.uf = normalizarUf(uf);
        this.status = StatusObra.ATIVA;
    }

    /** Normaliza a UF para caixa alta, validando o formato de duas letras. */
    public static String normalizarUf(String bruta) {
        String texto = exigirTexto(bruta, "UF").trim().toUpperCase(Locale.ROOT);
        if (!texto.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("UF inválida: use a sigla de dois caracteres, ex.: PE.");
        }
        return texto;
    }

    /**
     * Define o período da obra.
     *
     * @throws IllegalArgumentException se a data de fim for anterior à de início
     */
    public void definirPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data de encerramento não pode ser anterior à de início.");
        }
        this.dataInicio = inicio;
        this.dataFim = fim;
    }

    public void alterarDadosBasicos(String codigo, String nome, String cliente, String cidade, String uf) {
        this.codigo = exigirTexto(codigo, "código");
        this.nome = exigirTexto(nome, "nome");
        this.cliente = cliente;
        this.cidade = exigirTexto(cidade, "cidade");
        this.uf = normalizarUf(uf);
    }

    public void alterarStatus(StatusObra novoStatus) {
        this.status = Objects.requireNonNull(novoStatus, "status é obrigatório");
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    /** Indica se a obra ainda recebe novos contratos de locação. */
    public boolean estaAtiva() {
        return status == StatusObra.ATIVA && !isExcluida();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getCliente() {
        return cliente;
    }

    public String getCidade() {
        return cidade;
    }

    public String getUf() {
        return uf;
    }

    public StatusObra getStatus() {
        return status;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public String getObservacoes() {
        return observacoes;
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
        if (!(outro instanceof Obra obra)) {
            return false;
        }
        return id != null && id.equals(obra.id);
    }

    @Override
    public int hashCode() {
        return Obra.class.hashCode();
    }
}
