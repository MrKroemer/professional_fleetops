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
 * Dados específicos de um fornecedor de rastreamento veicular.
 *
 * <p>Guarda os custos que compõem o gasto mensal com telemetria e as credenciais do
 * portal — cifradas (RN-20). Nas planilhas atuais esse login e senha estão em texto
 * claro, compartilhados entre todas as obras.
 */
@Entity
@Table(name = "fornecedor_rastreador")
public class DadosRastreador {

    @Id
    @Column(name = "fornecedor_id")
    private Long fornecedorId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "mensalidade", precision = 12, scale = 2)
    private BigDecimal mensalidade;

    @Column(name = "custo_instalacao", precision = 12, scale = 2)
    private BigDecimal custoInstalacao;

    @Column(name = "custo_desinstalacao", precision = 12, scale = 2)
    private BigDecimal custoDesinstalacao;

    @Column(name = "equipadora", length = 180)
    private String equipadora;

    @Column(name = "portal_url", length = 400)
    private String portalUrl;

    @Column(name = "portal_login_cifrado")
    private String portalLoginCifrado;

    @Column(name = "portal_senha_cifrada")
    private String portalSenhaCifrada;

    /** Construtor exigido pelo JPA. */
    protected DadosRastreador() {
    }

    public DadosRastreador(
            BigDecimal mensalidade,
            BigDecimal custoInstalacao,
            BigDecimal custoDesinstalacao,
            String equipadora,
            String portalUrl) {
        definirCustos(mensalidade, custoInstalacao, custoDesinstalacao);
        this.equipadora = equipadora;
        this.portalUrl = portalUrl;
    }

    void vincularA(Fornecedor fornecedor) {
        this.fornecedor = fornecedor;
    }

    public void definirCustos(BigDecimal mensalidade, BigDecimal instalacao, BigDecimal desinstalacao) {
        this.mensalidade = normalizarCusto(mensalidade, "mensalidade");
        this.custoInstalacao = normalizarCusto(instalacao, "instalação");
        this.custoDesinstalacao = normalizarCusto(desinstalacao, "desinstalação");
    }

    public void definirEquipadora(String equipadora, String portalUrl) {
        this.equipadora = equipadora;
        this.portalUrl = portalUrl;
    }

    /**
     * Grava as credenciais já cifradas do portal de telemetria.
     *
     * <p>{@code null} preserva o valor atual, permitindo editar o cadastro sem redigitar
     * a senha.
     */
    public void definirCredenciaisCifradas(String loginCifrado, String senhaCifrada) {
        if (loginCifrado != null) {
            this.portalLoginCifrado = loginCifrado.isBlank() ? null : loginCifrado;
        }
        if (senhaCifrada != null) {
            this.portalSenhaCifrada = senhaCifrada.isBlank() ? null : senhaCifrada;
        }
    }

    public void limparCredenciais() {
        this.portalLoginCifrado = null;
        this.portalSenhaCifrada = null;
    }

    /** Indica se há credencial armazenada, sem revelar seu conteúdo. */
    public boolean possuiCredenciais() {
        return portalLoginCifrado != null || portalSenhaCifrada != null;
    }

    private static BigDecimal normalizarCusto(BigDecimal valor, String rotulo) {
        if (valor == null) {
            return null;
        }
        if (Dinheiro.eNegativo(valor)) {
            throw new IllegalArgumentException("O custo de %s não pode ser negativo.".formatted(rotulo));
        }
        return Dinheiro.normalizar(valor);
    }

    public BigDecimal getMensalidade() {
        return mensalidade;
    }

    public BigDecimal getCustoInstalacao() {
        return custoInstalacao;
    }

    public BigDecimal getCustoDesinstalacao() {
        return custoDesinstalacao;
    }

    public String getEquipadora() {
        return equipadora;
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public String getPortalLoginCifrado() {
        return portalLoginCifrado;
    }

    public String getPortalSenhaCifrada() {
        return portalSenhaCifrada;
    }

    public Long getFornecedorId() {
        return fornecedorId;
    }
}
