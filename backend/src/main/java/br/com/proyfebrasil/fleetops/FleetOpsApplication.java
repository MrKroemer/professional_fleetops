package br.com.proyfebrasil.fleetops;

import br.com.proyfebrasil.fleetops.shared.arquivos.PropriedadesDeArmazenamento;
import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do FleetOps — sistema de gestão de frotas locadas da Proyfe Brasil.
 *
 * <p>Monólito modular: cada módulo de domínio ({@code cadastros}, {@code contratos},
 * {@code operacao}, {@code conformidade}, {@code alertas}, {@code importacao},
 * {@code administracao}) é isolado em seu próprio pacote com as camadas
 * {@code domain}, {@code application}, {@code api} e {@code infra}.
 */
@SpringBootApplication
@EnableConfigurationProperties({FleetOpsProperties.class, PropriedadesDeArmazenamento.class})
@EnableScheduling
public class FleetOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetOpsApplication.class, args);
    }
}
