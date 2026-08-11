package br.com.proyfebrasil.fleetops.shared.auditing;

import br.com.proyfebrasil.fleetops.shared.config.CorrelacaoRequestFilter;
import org.hibernate.envers.RevisionListener;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Preenche a revisão do Envers com o usuário autenticado e o identificador da requisição.
 *
 * <p>O Envers instancia o listener fora do contêiner Spring, por isso os dados são lidos
 * do {@code SecurityContextHolder} e do {@code MDC} em vez de injetados.
 */
public class OuvinteRevisaoAuditoria implements RevisionListener {

    @Override
    public void newRevision(Object revisao) {
        RevisaoAuditoria revisaoAuditoria = (RevisaoAuditoria) revisao;
        revisaoAuditoria.setUsuario(usuarioAtual());
        revisaoAuditoria.setRequestId(MDC.get(CorrelacaoRequestFilter.CHAVE_MDC));
    }

    private String usuarioAtual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated() || autenticacao.getName() == null) {
            return AuditorAtual.SISTEMA;
        }
        return autenticacao.getName();
    }
}
