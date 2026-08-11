package br.com.proyfebrasil.fleetops.shared.auditing;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base de toda entidade de negócio: auditoria de criação/alteração e exclusão lógica.
 *
 * <p>Os nomes de coluna seguem literalmente a especificação ({@code created_at},
 * {@code created_by}, {@code updated_at}, {@code updated_by}, {@code deleted_at}),
 * enquanto os atributos Java permanecem em português, como o restante do domínio.
 *
 * <p>Instantes são persistidos em {@code timestamptz} (RN-22); a conversão para
 * {@code America/Recife} acontece apenas na exibição.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadeAuditavel {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @CreatedBy
    @Column(name = "created_by", length = 180, nullable = false, updatable = false)
    private String criadoPor;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant atualizadoEm;

    @LastModifiedBy
    @Column(name = "updated_by", length = 180, nullable = false)
    private String atualizadoPor;

    @Column(name = "deleted_at")
    private Instant excluidoEm;

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public Instant getExcluidoEm() {
        return excluidoEm;
    }

    /** Marca a entidade como excluída logicamente. Exclusão física nunca é usada no domínio. */
    public void excluir(Instant momento) {
        this.excluidoEm = momento;
    }

    public boolean isExcluida() {
        return excluidoEm != null;
    }
}
