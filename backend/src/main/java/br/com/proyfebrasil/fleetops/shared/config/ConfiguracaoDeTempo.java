package br.com.proyfebrasil.fleetops.shared.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fonte de tempo da aplicação.
 *
 * <p>Regras que dependem de data — vencimento de CNH (RN-16), prazo de indicação de
 * condutor (RN-08), próxima revisão (RN-15) — precisam de um relógio injetável para
 * serem testáveis sem esperar o calendário. Chamadas diretas a
 * {@code LocalDate.now()} tornariam esses testes impossíveis de escrever.
 *
 * <p>O relógio opera em UTC, coerente com o armazenamento em {@code timestamptz};
 * a conversão para {@code America/Recife} é feita apenas na exibição (RN-22).
 */
@Configuration
public class ConfiguracaoDeTempo {

    @Bean
    public Clock relogio() {
        return Clock.system(ZoneOffset.UTC);
    }
}
