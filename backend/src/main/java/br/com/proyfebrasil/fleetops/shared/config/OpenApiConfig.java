package br.com.proyfebrasil.fleetops.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI da API. Este documento é o contrato do frontend: os tipos
 * TypeScript são gerados a partir dele, portanto nenhum endpoint pode ficar de fora
 * nem ser descrito de forma imprecisa.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI fleetOpsOpenApi(@Value("${spring.application.version:0.1.0}") String versao) {
        return new OpenAPI()
                .info(new Info()
                        .title("FleetOps — Gestão de Frotas Locadas")
                        .version(versao)
                        .description("""
                                API do sistema de gestão da frota locada da Proyfe Brasil.

                                **Autenticação:** `POST /api/v1/auth/login` devolve um *access token* de curta \
                                duração, que deve ser enviado no cabeçalho `Authorization: Bearer <token>`. \
                                O *refresh token* trafega exclusivamente em cookie `httpOnly` e é renovado \
                                por `POST /api/v1/auth/refresh`.

                                **Erros:** todas as falhas seguem a RFC 7807 (Problem Details) e incluem a \
                                extensão `codigo`, um identificador estável do erro de negócio.
                                """)
                        .contact(new Contact()
                                .name("Setor de Frotas — Proyfe Brasil")
                                .email("atendimento.frota@proyfebrasil.com.br"))
                        .license(new License().name("Uso interno — Proyfe Brasil")))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token obtido em /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
