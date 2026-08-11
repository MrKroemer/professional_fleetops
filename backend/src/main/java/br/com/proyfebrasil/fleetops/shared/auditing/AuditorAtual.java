package br.com.proyfebrasil.fleetops.shared.auditing;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Identifica o autor de cada escrita. Operações executadas fora de um contexto
 * autenticado (migrações de seed, jobs agendados) são atribuídas a {@value #SISTEMA},
 * nunca a um usuário real.
 */
@Component
public class AuditorAtual implements AuditorAware<String> {

    public static final String SISTEMA = "sistema";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            return Optional.of(SISTEMA);
        }
        String nome = autenticacao.getName();
        return Optional.of(nome == null || nome.isBlank() ? SISTEMA : nome);
    }
}
