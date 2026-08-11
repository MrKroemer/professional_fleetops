package br.com.proyfebrasil.fleetops.cadastros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.Set;

/**
 * Dados específicos de um posto de combustível credenciado.
 *
 * <p>Os dias autorizados são a base da RN-04: um abastecimento fora do dia combinado
 * com o posto só é aceito com justificativa explícita e vai para o relatório de não
 * conformidades.
 */
@Entity
@Table(name = "fornecedor_posto")
public class DadosPosto {

    @Id
    @Column(name = "fornecedor_id")
    private Long fornecedorId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    /** Siglas separadas por vírgula, ex.: {@code TER,QUI,SAB}. Vazio = sem restrição. */
    @Column(name = "dias_autorizados", length = 60, nullable = false)
    private String diasAutorizados = "";

    @Column(name = "acesso_faturas", length = 120)
    private String acessoFaturas;

    /** Construtor exigido pelo JPA. */
    protected DadosPosto() {
    }

    public DadosPosto(Set<DiaDaSemana> diasAutorizados, String acessoFaturas) {
        this.diasAutorizados = DiaDaSemana.serializar(diasAutorizados);
        this.acessoFaturas = acessoFaturas;
    }

    void vincularA(Fornecedor fornecedor) {
        this.fornecedor = fornecedor;
    }

    /** Dias em que o posto aceita abastecimento. Conjunto vazio significa sem restrição. */
    public Set<DiaDaSemana> getDiasAutorizados() {
        return DiaDaSemana.interpretar(diasAutorizados);
    }

    public void definirDiasAutorizados(Set<DiaDaSemana> dias) {
        this.diasAutorizados = DiaDaSemana.serializar(dias);
    }

    /**
     * Indica se o abastecimento é autorizado no dia informado (RN-04).
     *
     * <p>Sem dias cadastrados, qualquer dia é permitido: a restrição precisa ser
     * declarada explicitamente, e não presumida por omissão.
     */
    public boolean autorizaAbastecimentoEm(DayOfWeek dia) {
        Set<DiaDaSemana> dias = getDiasAutorizados();
        return dias.isEmpty() || dias.contains(DiaDaSemana.de(dia));
    }

    public String getAcessoFaturas() {
        return acessoFaturas;
    }

    public void definirAcessoFaturas(String acessoFaturas) {
        this.acessoFaturas = acessoFaturas;
    }

    public Long getFornecedorId() {
        return fornecedorId;
    }
}
