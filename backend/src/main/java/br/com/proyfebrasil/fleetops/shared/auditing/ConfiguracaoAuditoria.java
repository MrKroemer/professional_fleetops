package br.com.proyfebrasil.fleetops.shared.auditing;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Ativa o preenchimento automático dos campos de auditoria de {@link EntidadeAuditavel}. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAtual")
public class ConfiguracaoAuditoria {
}
