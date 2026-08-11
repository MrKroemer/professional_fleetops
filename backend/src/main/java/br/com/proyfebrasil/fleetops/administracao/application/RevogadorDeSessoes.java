package br.com.proyfebrasil.fleetops.administracao.application;

import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revoga sessões em transação própria.
 *
 * <p>Existe por um motivo específico: quando um refresh token já revogado é reapresentado,
 * o sistema encerra <em>todas</em> as sessões do usuário e em seguida lança a exceção que
 * devolve 401. Se a revogação ocorresse na mesma transação, o rollback provocado por essa
 * exceção a desfaria — e o ataque continuaria com as demais sessões abertas.
 *
 * <p>{@link Propagation#REQUIRES_NEW} garante que a revogação seja confirmada
 * independentemente do desfecho da transação que a acionou. Fica em um bean separado
 * porque a chamada precisa passar pelo proxy do Spring para que a propagação valha.
 */
@Component
public class RevogadorDeSessoes {

    private final SessaoRefreshRepository sessoes;

    public RevogadorDeSessoes(SessaoRefreshRepository sessoes) {
        this.sessoes = sessoes;
    }

    /**
     * Encerra todas as sessões abertas do usuário, confirmando a alteração mesmo que a
     * transação chamadora seja revertida em seguida.
     *
     * @return quantidade de sessões revogadas
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revogarTodasAsSessoes(Long usuarioId) {
        return sessoes.revogarSessoesDoUsuario(usuarioId, Instant.now());
    }
}
