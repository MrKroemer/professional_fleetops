package br.com.proyfebrasil.fleetops;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: sobe a aplicação inteira contra um PostgreSQL 16 real.
 *
 * <p>Usar o banco de verdade — e não um H2 em memória — é o que dá valor a estes testes:
 * eles exercitam as migrações Flyway, os índices parciais que dependem de {@code deleted_at}
 * e a validação do mapeamento do Hibernate contra o schema, três coisas que um banco
 * substituto não verificaria.
 *
 * <p>O container segue o padrão <em>singleton</em>: é iniciado uma única vez no primeiro
 * carregamento da classe e permanece de pé até o fim da JVM de testes. Deliberadamente
 * não se usa {@code @Testcontainers}/{@code @Container} aqui, porque essa extensão encerra
 * containers estáticos ao fim de <em>cada</em> classe de teste — o que derrubaria o banco
 * para as classes seguintes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TesteDeIntegracao {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fleetops_test")
            .withUsername("fleetops")
            .withPassword("fleetops_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurarFonteDeDados(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
