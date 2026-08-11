package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * Lava-jato, borracharia ou para-brisas (Seção 3.3, RN-05).
 *
 * <p>Uma entidade para os três porque os campos são os mesmos e só a frequência difere —
 * e a frequência é atributo do {@link TipoDeServico}, não estrutura. Três entidades
 * idênticas obrigariam a manter três repositórios, três DTOs e três telas para exprimir
 * um enum.
 *
 * <p>A mecânica de não conformidade é a mesma do abastecimento, e pelo mesmo motivo: o
 * segundo lava-jato da semana aconteceu, e recusá-lo esconderia o custo junto com a
 * irregularidade.
 */
@Entity
@Table(name = "servico_operacional")
@Audited
public class ServicoOperacional extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    /**
     * Fornecedor do lançamento.
     *
     * <p>{@code NOT_AUDITED} porque {@link Fornecedor} não está na trilha do Envers — a
     * decisão é da Fase 1, e este lançamento não é motivo para mudá-la. A revisão guarda a
     * chave estrangeira; quem quiser o estado do fornecedor naquela data continua olhando o
     * cadastro atual, que é o que a Fase 1 escolheu ao deixá-lo fora.
     */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoDeServico tipo;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "valor", precision = 12, scale = 2, nullable = false)
    private BigDecimal valor;

    @Column(name = "descricao", length = 300)
    private String descricao;

    @Column(name = "nao_conforme", nullable = false)
    private boolean naoConforme;

    @Column(name = "justificativa")
    private String justificativa;

    /** Construtor exigido pelo JPA. */
    protected ServicoOperacional() {
    }

    public ServicoOperacional(
            ContratoDeLocacao contrato, TipoDeServico tipo, LocalDate data, BigDecimal valor) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.tipo = Objects.requireNonNull(tipo, "tipo de serviço é obrigatório");
        this.data = Objects.requireNonNull(data, "data é obrigatória");
        alterarValor(valor);
    }

    public final void alterarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor é obrigatório");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("O valor do serviço não pode ser negativo.");
        }
        this.valor = valor;
    }

    public void alterarDados(Fornecedor fornecedor, String descricao) {
        this.fornecedor = fornecedor;
        this.descricao = descricao;
    }

    /** Marca ou desmarca a não conformidade (RN-05). */
    public void registrarConformidade(boolean naoConforme, String justificativa) {
        if (naoConforme && (justificativa == null || justificativa.isBlank())) {
            throw new IllegalArgumentException("Um lançamento não conforme exige justificativa.");
        }
        this.naoConforme = naoConforme;
        this.justificativa = naoConforme ? justificativa : null;
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public Optional<Fornecedor> getFornecedor() {
        return Optional.ofNullable(fornecedor);
    }

    public TipoDeServico getTipo() {
        return tipo;
    }

    public LocalDate getData() {
        return data;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isNaoConforme() {
        return naoConforme;
    }

    public String getJustificativa() {
        return justificativa;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ServicoOperacional servico)) {
            return false;
        }
        return id != null && id.equals(servico.id);
    }

    @Override
    public int hashCode() {
        return ServicoOperacional.class.hashCode();
    }
}
