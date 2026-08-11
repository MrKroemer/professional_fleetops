package br.com.proyfebrasil.fleetops.administracao.application;

import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remove sessões de refresh já expiradas.
 *
 * <p>Sem este expurgo a tabela cresceria indefinidamente, já que cada renovação de token
 * cria um novo registro. A carência preserva as sessões expiradas há pouco tempo, para
 * que ainda sirvam a uma investigação de acesso indevido.
 */
@Component
public class ExpurgoDeSessoes {

    private static final Logger LOG = LoggerFactory.getLogger(ExpurgoDeSessoes.class);
    private static final Duration CARENCIA = Duration.ofDays(30);

    private final SessaoRefreshRepository sessoes;

    public ExpurgoDeSessoes(SessaoRefreshRepository sessoes) {
        this.sessoes = sessoes;
    }

    /** Executa diariamente às 3h05 (horário do servidor, UTC). */
    @Scheduled(cron = "0 5 3 * * *")
    @Transactional
    public void expurgar() {
        int removidas = sessoes.expurgarExpiradasAntesDe(Instant.now().minus(CARENCIA));
        if (removidas > 0) {
            LOG.info("Expurgo de sessões: {} registro(s) expirado(s) removido(s)", removidas);
        }
    }
}
