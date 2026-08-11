package br.com.proyfebrasil.fleetops.shared.auditing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Revisão do histórico do Hibernate Envers, estendida para registrar <em>quem</em>
 * fez a alteração — sem isso o histórico responde "o que mudou" mas não "por quem"
 * (RN-18 e requisito de auditoria da Seção 3.5).
 */
@Entity
@Table(name = "revisao_auditoria")
@RevisionEntity(OuvinteRevisaoAuditoria.class)
public class RevisaoAuditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "id")
    private long id;

    @RevisionTimestamp
    @Column(name = "revisado_em", nullable = false)
    private long revisadoEm;

    @Column(name = "usuario", length = 180, nullable = false)
    private String usuario;

    @Column(name = "request_id", length = 64)
    private String requestId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getRevisadoEm() {
        return revisadoEm;
    }

    public void setRevisadoEm(long revisadoEm) {
        this.revisadoEm = revisadoEm;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof RevisaoAuditoria revisao)) {
            return false;
        }
        return id == revisao.id && revisadoEm == revisao.revisadoEm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, revisadoEm);
    }
}
