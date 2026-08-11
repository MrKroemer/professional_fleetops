package br.com.proyfebrasil.fleetops.administracao.infra;

import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria um usuário por perfil de acesso para uso em desenvolvimento.
 *
 * <p><strong>Dados fictícios, restritos ao perfil {@code dev}.</strong> Nunca é executado
 * em produção: a anotação {@link Profile} garante que o bean sequer seja registrado fora
 * do perfil de desenvolvimento.
 *
 * <p>O seed é de dados, não de estrutura — por isso vive aqui e não em uma migração
 * Flyway. Manter dados fictícios fora do Flyway evita que o histórico de migrações do
 * ambiente de desenvolvimento divirja do de produção.
 *
 * <p>É idempotente: usuários já existentes são preservados, inclusive se tiverem tido a
 * senha alterada manualmente durante os testes.
 */
@Component
@Profile("dev")
public class SeedDeUsuariosDesenvolvimento implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SeedDeUsuariosDesenvolvimento.class);

    private record UsuarioSeed(String nome, String email, Perfil perfil) {
    }

    private static final List<UsuarioSeed> USUARIOS = List.of(
            new UsuarioSeed("Administrador (seed)", "admin@proyfebrasil.com.br", Perfil.ADMIN),
            new UsuarioSeed("Gestor de Frota (seed)", "gestor@proyfebrasil.com.br", Perfil.GESTOR_FROTA),
            new UsuarioSeed("Consulta (seed)", "consulta@proyfebrasil.com.br", Perfil.CONSULTA));

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificadorDeSenha;
    private final boolean habilitado;
    private final String senha;

    public SeedDeUsuariosDesenvolvimento(
            UsuarioRepository usuarios,
            PasswordEncoder codificadorDeSenha,
            @Value("${fleetops.seed.habilitado:false}") boolean habilitado,
            @Value("${fleetops.seed.senha:Fleet@2026}") String senha) {
        this.usuarios = usuarios;
        this.codificadorDeSenha = codificadorDeSenha;
        this.habilitado = habilitado;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!habilitado) {
            return;
        }
        String hash = codificadorDeSenha.encode(senha);
        int criados = 0;
        for (UsuarioSeed semente : USUARIOS) {
            if (usuarios.buscarPorEmail(semente.email()).isEmpty()) {
                usuarios.save(new Usuario(semente.nome(), semente.email(), hash, semente.perfil()));
                criados++;
            }
        }
        if (criados > 0) {
            LOG.warn("SEED DE DESENVOLVIMENTO: {} usuário(s) fictício(s) criado(s). "
                    + "Estes dados não devem existir em produção.", criados);
        }
    }
}
