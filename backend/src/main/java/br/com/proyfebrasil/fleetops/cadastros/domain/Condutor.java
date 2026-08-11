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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * Condutor: funcionário habilitado a conduzir os veículos da frota.
 *
 * <p>A validade da CNH é regra de negócio, não dado cadastral (RN-16): CNH vencida
 * impede novo vínculo a contrato, e o vencimento próximo gera alerta com 60 e 30 dias
 * de antecedência.
 */
@Entity
@Table(name = "condutor")
@Audited
public class Condutor extends EntidadeAuditavel {

    /** Antecedências, em dias, dos alertas de vencimento de CNH (RN-16). */
    public static final int[] ANTECEDENCIAS_DE_ALERTA_CNH = {60, 30};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", length = 180, nullable = false)
    private String nome;

    @Column(name = "cargo", length = 120)
    private String cargo;

    @Column(name = "cpf", length = 11, nullable = false)
    private String cpf;

    @Column(name = "cnh_numero", length = 20)
    private String cnhNumero;

    @Column(name = "cnh_categoria", length = 4)
    private String cnhCategoria;

    @Column(name = "cnh_validade")
    private LocalDate cnhValidade;

    @Column(name = "telefone", length = 60)
    private String telefone;

    @Column(name = "email", length = 180)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_atual_id")
    private Obra obraAtual;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusCondutor status = StatusCondutor.ATIVO;

    @Column(name = "observacoes")
    private String observacoes;

    /** Construtor exigido pelo JPA. */
    protected Condutor() {
    }

    public Condutor(String nome, String cpf) {
        this.nome = exigirTexto(nome, "nome");
        this.cpf = Cpf.normalizar(cpf);
        this.status = StatusCondutor.ATIVO;
    }

    /** Normaliza a categoria da CNH para caixa alta, validando as letras aceitas. */
    public static String normalizarCategoriaCnh(String bruta) {
        if (bruta == null || bruta.isBlank()) {
            return null;
        }
        String categoria = bruta.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (!categoria.matches("^[ABCDE]{1,4}$")) {
            throw new IllegalArgumentException(
                    "Categoria de CNH inválida: use combinações de A, B, C, D e E, ex.: AB.");
        }
        return categoria;
    }

    public void alterarDadosBasicos(String nome, String cpf, String cargo, String telefone, String email) {
        this.nome = exigirTexto(nome, "nome");
        this.cpf = Cpf.normalizar(cpf);
        this.cargo = cargo;
        this.telefone = telefone;
        this.email = email;
    }

    /** Atualiza os dados da habilitação. */
    public void alterarCnh(String numero, String categoria, LocalDate validade) {
        this.cnhNumero = numero == null || numero.isBlank() ? null : numero.trim();
        this.cnhCategoria = normalizarCategoriaCnh(categoria);
        this.cnhValidade = validade;
    }

    public void alocarNaObra(Obra obra) {
        this.obraAtual = obra;
    }

    public void alterarStatus(StatusCondutor novoStatus) {
        this.status = Objects.requireNonNull(novoStatus, "status é obrigatório");
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    // -----------------------------------------------------------------
    // RN-16 — validade da CNH
    // -----------------------------------------------------------------

    /**
     * Indica se a CNH está vencida na data informada.
     *
     * <p>Uma CNH sem validade cadastrada <strong>não</strong> é considerada vencida:
     * o cadastro incompleto é tratado como pendência de dados, e não como impedimento,
     * para não travar a operação por um campo que ninguém preencheu ainda.
     */
    public boolean cnhVencidaEm(LocalDate referencia) {
        Objects.requireNonNull(referencia, "referência é obrigatória");
        return cnhValidade != null && cnhValidade.isBefore(referencia);
    }

    /** Dias restantes até o vencimento da CNH; negativo se já vencida, nulo se não cadastrada. */
    public Long diasParaVencimentoDaCnh(LocalDate referencia) {
        Objects.requireNonNull(referencia, "referência é obrigatória");
        return cnhValidade == null ? null : ChronoUnit.DAYS.between(referencia, cnhValidade);
    }

    /**
     * Indica se o condutor pode ser vinculado a um contrato de locação (RN-16).
     *
     * <p>Exige estar ativo, não excluído e com CNH dentro da validade.
     */
    public boolean podeSerVinculadoAContrato(LocalDate referencia) {
        return status == StatusCondutor.ATIVO && !isExcluida() && !cnhVencidaEm(referencia);
    }

    /**
     * Indica se o condutor está em atividade na empresa.
     *
     * <p>Separado de {@link #podeSerVinculadoAContrato} de propósito: aquele responde
     * sobre <em>vincular agora</em> e soma as três condições; este responde só sobre o
     * vínculo empregatício, que é o que interessa a quem registra um histórico passado —
     * onde um condutor hoje inativo é o caso normal, não um erro.
     */
    public boolean estaAtivo() {
        return status == StatusCondutor.ATIVO && !isExcluida();
    }

    /** Indica se o vencimento da CNH já entrou em alguma faixa de alerta (RN-16). */
    public boolean cnhEmAlertaEm(LocalDate referencia) {
        Long dias = diasParaVencimentoDaCnh(referencia);
        if (dias == null) {
            return false;
        }
        return dias <= ANTECEDENCIAS_DE_ALERTA_CNH[0];
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCargo() {
        return cargo;
    }

    public String getCpf() {
        return cpf;
    }

    public String getCnhNumero() {
        return cnhNumero;
    }

    public String getCnhCategoria() {
        return cnhCategoria;
    }

    public LocalDate getCnhValidade() {
        return cnhValidade;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
    }

    public Obra getObraAtual() {
        return obraAtual;
    }

    public StatusCondutor getStatus() {
        return status;
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
        if (!(outro instanceof Condutor condutor)) {
            return false;
        }
        return id != null && id.equals(condutor.id);
    }

    @Override
    public int hashCode() {
        return Condutor.class.hashCode();
    }
}
