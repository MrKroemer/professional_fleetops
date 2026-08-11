package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Locadora parceira de quem os veículos são alugados.
 *
 * <p>Concentra os canais de atendimento que hoje vivem espalhados em uma planilha e em
 * cabeçalhos de formulários: reservas, manutenção, guincho, assistência 24h, financeiro,
 * suporte e telemetria. Ter esses números junto do cadastro é o que permite ao condutor
 * acionar o canal certo sem depender de quem está no escritório.
 *
 * <p>As credenciais do portal são cifradas (RN-20) e ficam fora da trilha do Envers:
 * versionar um segredo apenas multiplica as cópias dele.
 */
@Entity
@Table(name = "locadora")
@Audited
public class Locadora extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", length = 160, nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoLocadora tipo;

    @Column(name = "consultor", length = 160)
    private String consultor;

    @Column(name = "telefone", length = 60)
    private String telefone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "portal_url", length = 400)
    private String portalUrl;

    @NotAudited
    @Column(name = "portal_login_cifrado")
    private String portalLoginCifrado;

    @NotAudited
    @Column(name = "portal_senha_cifrada")
    private String portalSenhaCifrada;

    @Embedded
    private CanaisDeAtendimento canais = new CanaisDeAtendimento();

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;

    /** Construtor exigido pelo JPA. */
    protected Locadora() {
    }

    public Locadora(String nome, TipoLocadora tipo) {
        this.nome = exigirTexto(nome, "nome");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.ativa = true;
        this.canais = new CanaisDeAtendimento();
    }

    public void alterarDadosBasicos(
            String nome, TipoLocadora tipo, String consultor, String telefone, String email, String portalUrl) {
        this.nome = exigirTexto(nome, "nome");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.consultor = consultor;
        this.telefone = telefone;
        this.email = email;
        this.portalUrl = portalUrl;
    }

    public void alterarCanais(CanaisDeAtendimento novosCanais) {
        this.canais = novosCanais == null ? new CanaisDeAtendimento() : novosCanais;
    }

    /**
     * Grava as credenciais já cifradas do portal.
     *
     * <p>Recebe o texto cifrado, e não o valor em claro, porque a cifragem é
     * responsabilidade do serviço de aplicação, que detém a chave. Assim a entidade
     * nunca chega a segurar um segredo legível.
     *
     * <p>{@code null} preserva a credencial atual — o que permite que uma edição de
     * cadastro não exija redigitar a senha.
     */
    public void definirCredenciaisCifradas(String loginCifrado, String senhaCifrada) {
        if (loginCifrado != null) {
            this.portalLoginCifrado = loginCifrado.isBlank() ? null : loginCifrado;
        }
        if (senhaCifrada != null) {
            this.portalSenhaCifrada = senhaCifrada.isBlank() ? null : senhaCifrada;
        }
    }

    /** Remove as credenciais do portal. */
    public void limparCredenciais() {
        this.portalLoginCifrado = null;
        this.portalSenhaCifrada = null;
    }

    /** Indica se há alguma credencial armazenada, sem revelar seu conteúdo. */
    public boolean possuiCredenciais() {
        return portalLoginCifrado != null || portalSenhaCifrada != null;
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public void ativar() {
        this.ativa = true;
    }

    public void desativar() {
        this.ativa = false;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoLocadora getTipo() {
        return tipo;
    }

    public String getConsultor() {
        return consultor;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
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

    public CanaisDeAtendimento getCanais() {
        return canais;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtiva() {
        return ativa;
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
        if (!(outro instanceof Locadora locadora)) {
            return false;
        }
        return id != null && id.equals(locadora.id);
    }

    @Override
    public int hashCode() {
        return Locadora.class.hashCode();
    }
}
