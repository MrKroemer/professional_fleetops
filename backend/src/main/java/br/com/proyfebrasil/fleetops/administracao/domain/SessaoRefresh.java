package br.com.proyfebrasil.fleetops.administracao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão associada a um refresh token emitido.
 *
 * <p>Sem este registro o refresh token seria irrevogável até expirar: guardar o {@code jti}
 * permite invalidar a sessão no logout, na desativação do usuário e na rotação — cada
 * renovação revoga o token anterior, de modo que o reuso de um token já trocado é
 * detectável e recusado.
 *
 * <p>Não estende {@code EntidadeAuditavel}: é um artefato técnico de sessão, com ciclo de
 * vida próprio e expurgo periódico, não uma entidade de negócio.
 */
@Entity
@Table(name = "sessao_refresh")
public class SessaoRefresh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "jti", nullable = false, unique = true)
    private UUID jti;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "endereco_ip", length = 60)
    private String enderecoIp;

    /** Construtor exigido pelo JPA. */
    protected SessaoRefresh() {
    }

    public SessaoRefresh(Usuario usuario, UUID jti, Instant expiraEm, String userAgent, String enderecoIp) {
        this.usuario = Objects.requireNonNull(usuario, "usuario é obrigatório");
        this.jti = Objects.requireNonNull(jti, "jti é obrigatório");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
        this.criadoEm = Instant.now();
        this.userAgent = truncar(userAgent, 400);
        this.enderecoIp = truncar(enderecoIp, 60);
    }

    /** Sessão utilizável: não revogada e ainda dentro da validade. */
    public boolean estaValidaEm(Instant momento) {
        return revogadoEm == null && expiraEm.isAfter(momento);
    }

    public void revogar(Instant momento) {
        if (revogadoEm == null) {
            this.revogadoEm = momento;
        }
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public UUID getJti() {
        return jti;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadoEm() {
        return revogadoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getEnderecoIp() {
        return enderecoIp;
    }

    private static String truncar(String valor, int limite) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= limite ? valor : valor.substring(0, limite);
    }
}
