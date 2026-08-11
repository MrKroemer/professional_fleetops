package br.com.proyfebrasil.fleetops.shared.arquivos;

import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * Metadados de um arquivo guardado no bucket.
 *
 * <p>O binário não passa por aqui: a entidade guarda a chave do objeto, e o acesso se dá
 * por URL pré-assinada de vida curta. Gravar o conteúdo no Postgres incharia backups e
 * transações com bytes que nenhuma consulta lê.
 *
 * <p>O {@code sha256} é o que transforma o registro em prova: um book fotográfico existe
 * para sustentar uma discussão sobre avaria com a locadora, e nesse contexto importa
 * poder demonstrar que o arquivo servido é o mesmo que foi enviado no dia da retirada.
 */
@Entity
@Table(name = "anexo")
@Audited
public class Anexo extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave", length = 300, nullable = false, updatable = false)
    private String chave;

    @Column(name = "nome_original", length = 260, nullable = false)
    private String nomeOriginal;

    @Column(name = "tipo_conteudo", length = 120, nullable = false)
    private String tipoDeConteudo;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoEmBytes;

    @Column(name = "sha256", length = 64, nullable = false, updatable = false)
    private String sha256;

    /** Construtor exigido pelo JPA. */
    protected Anexo() {
    }

    public Anexo(String chave, String nomeOriginal, String tipoDeConteudo, long tamanhoEmBytes, String sha256) {
        this.chave = Objects.requireNonNull(chave, "chave é obrigatória");
        this.nomeOriginal = Objects.requireNonNull(nomeOriginal, "nome do arquivo é obrigatório");
        this.tipoDeConteudo = Objects.requireNonNull(tipoDeConteudo, "tipo de conteúdo é obrigatório");
        if (tamanhoEmBytes <= 0) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }
        this.tamanhoEmBytes = tamanhoEmBytes;
        this.sha256 = Objects.requireNonNull(sha256, "impressão digital é obrigatória");
    }

    public Long getId() {
        return id;
    }

    public String getChave() {
        return chave;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public String getTipoDeConteudo() {
        return tipoDeConteudo;
    }

    public long getTamanhoEmBytes() {
        return tamanhoEmBytes;
    }

    public String getSha256() {
        return sha256;
    }

    /** Indica se o anexo é uma imagem — o book aceita só imagens; o CRLV também aceita PDF. */
    public boolean ehImagem() {
        return tipoDeConteudo.startsWith("image/");
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Anexo anexo)) {
            return false;
        }
        return id != null && id.equals(anexo.id);
    }

    @Override
    public int hashCode() {
        return Anexo.class.hashCode();
    }
}
