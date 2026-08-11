package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração de segurança da API (RN-19).
 *
 * <p>API stateless com Bearer token. A proteção CSRF do Spring está desativada porque:
 * o access token não é enviado em cookie (logo não há envio automático pelo browser) e o
 * único cookie existente — o de refresh — é {@code SameSite=Strict} e restrito ao caminho
 * {@code /api/v1/auth}, de modo que uma requisição forjada por outro site jamais o carrega.
 * Ver {@link CookieDeRefresh} para o registro completo do trade-off.
 *
 * <p>A autorização fina por perfil fica nos controllers, via {@code @PreAuthorize}, para
 * que a regra viva junto do endpoint e possa ser testada endpoint a endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ConfiguracaoSeguranca {

    private static final String[] ROTAS_PUBLICAS = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/actuator/health",
        "/actuator/health/**",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
    };

    private final FleetOpsProperties propriedades;

    public ConfiguracaoSeguranca(FleetOpsProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Bean
    public SecurityFilterChain cadeiaDeFiltros(
            HttpSecurity http,
            PontoDeEntradaNaoAutenticado pontoDeEntrada,
            ManipuladorDeAcessoNegado acessoNegado)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(fonteCors()))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeAutenticacao()))
                        .authenticationEntryPoint(pontoDeEntrada)
                        .accessDeniedHandler(acessoNegado))
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(pontoDeEntrada)
                        .accessDeniedHandler(acessoNegado));
        return http.build();
    }

    /**
     * Codificador de senhas com prefixo de algoritmo ({@code {bcrypt}...}), o que permite
     * migrar o algoritmo no futuro sem invalidar as senhas já gravadas.
     */
    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Converte o JWT em uma autenticação do Spring: o principal passa a ser o e-mail
     * (usado também como autor nos campos de auditoria) e a autoridade deriva do perfil.
     */
    @Bean
    public JwtAuthenticationConverter conversorDeAutenticacao() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setPrincipalClaimName(ServicoDeTokens.CLAIM_EMAIL);
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            String perfil = jwt.getClaimAsString(ServicoDeTokens.CLAIM_PERFIL);
            if (perfil == null || perfil.isBlank()) {
                return List.<GrantedAuthority>of();
            }
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority(Perfil.PREFIXO_AUTHORITY + perfil));
        });
        return conversor;
    }

    private CorsConfigurationSource fonteCors() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(propriedades.cors().origens());
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));
        configuracao.setExposedHeaders(List.of("X-Request-Id"));
        // Necessário para que o browser envie o cookie httpOnly de refresh.
        configuracao.setAllowCredentials(true);
        configuracao.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", configuracao);
        return fonte;
    }
}
