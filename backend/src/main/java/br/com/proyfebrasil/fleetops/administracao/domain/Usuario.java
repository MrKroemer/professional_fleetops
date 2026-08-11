package br.com.proyfebrasil.fleetops.administracao.domain;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import br.com.proyfebrasil.fleetops.shared.security.DadosDoToken;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Usuário do sistema, com exatamente um perfil de acesso (RN-19).
 *
 * <p>Auditado pelo Envers: alterações de perfil e de status são eventos de segurança e
 * precisam de trilha própria, independente dos campos {@code updated_by}/{@code updated_at}.
 * A senha nunca entra na trilha — ver {@code @NotAudited} em {@link #senhaHash}.
 */
@Entity
@Table(name = "usuario")
@Audited
public class Usuario extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", length = 160, nullable = false)
    private String nome;

    @Column(name = "email", length = 180, nullable = false)
    private String email;

    @NotAudited
    @Column(name = "senha_hash", length = 120, nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", length = 20, nullable = false)
    private Perfil perfil;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @NotAudited
    @Column(name = "ultimo_acesso_em")
    private Instant ultimoAcessoEm;

    /** Construtor exigido pelo JPA. */
    protected Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash, Perfil perfil) {
        this.nome = exigirTexto(nome, "nome");
        this.email = normalizarEmail(email);
        this.senhaHash = exigirTexto(senhaHash, "senhaHash");
        this.perfil = Objects.requireNonNull(perfil, "perfil é obrigatório");
        this.ativo = true;
    }

    /** Normaliza o e-mail: identidade de login é sempre em caixa baixa e sem espaços. */
    public static String normalizarEmail(String email) {
        return exigirTexto(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /** Indica se o usuário pode autenticar: precisa estar ativo e não excluído. */
    public boolean podeAutenticar() {
        return ativo && !isExcluida();
    }

    /** Projeção usada para emitir tokens, sem expor a entidade à camada de segurança. */
    public DadosDoToken dadosDoToken() {
        return new DadosDoToken(id, email, nome, perfil);
    }

    public void registrarAcesso(Instant momento) {
        this.ultimoAcessoEm = momento;
    }

    public void alterarNome(String novoNome) {
        this.nome = exigirTexto(novoNome, "nome");
    }

    public void alterarEmail(String novoEmail) {
        this.email = normalizarEmail(novoEmail);
    }

    public void alterarPerfil(Perfil novoPerfil) {
        this.perfil = Objects.requireNonNull(novoPerfil, "perfil é obrigatório");
    }

    public void alterarSenha(String novoHash) {
        this.senhaHash = exigirTexto(novoHash, "senhaHash");
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getUltimoAcessoEm() {
        return ultimoAcessoEm;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Usuario usuario)) {
            return false;
        }
        return id != null && id.equals(usuario.id);
    }

    @Override
    public int hashCode() {
        return Usuario.class.hashCode();
    }
}
