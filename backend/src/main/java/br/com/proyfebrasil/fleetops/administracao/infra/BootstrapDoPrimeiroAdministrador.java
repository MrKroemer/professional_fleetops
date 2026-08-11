package br.com.proyfebrasil.fleetops.administracao.infra;

import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro administrador de uma instalação nova.
 *
 * <p>Sem isto, um banco recém-migrado em produção fica inacessível: não há usuários,
 * e a única tela de criação de usuários exige estar autenticado como {@code ADMIN} —
 * um impasse. O seed de desenvolvimento não resolve o problema porque, corretamente,
 * não roda em produção.
 *
 * <p>Salvaguardas deliberadas:
 * <ul>
 *   <li>só age quando <strong>não existe nenhum usuário</strong> — nunca altera,
 *       reativa ou redefine a senha de um usuário existente;</li>
 *   <li>só age quando {@code FLEETOPS_ADMIN_EMAIL} e {@code FLEETOPS_ADMIN_SENHA}
 *       estão definidos: não há senha padrão embutida;</li>
 *   <li>quando o banco está vazio e as variáveis não foram informadas, registra um
 *       aviso explicando o que fazer, em vez de subir silenciosamente inutilizável.</li>
 * </ul>
 *
 * <p>Estas variáveis existem para o primeiro acesso. Depois de criado o administrador,
 * remova-as do ambiente e troque a senha pela interface.
 */
@Component
@Order(0)
public class BootstrapDoPrimeiroAdministrador implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapDoPrimeiroAdministrador.class);
    private static final int TAMANHO_MINIMO_DA_SENHA = 10;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificadorDeSenha;
    private final String email;
    private final String senha;
    private final String nome;

    public BootstrapDoPrimeiroAdministrador(
            UsuarioRepository usuarios,
            PasswordEncoder codificadorDeSenha,
            @Value("${fleetops.admin-inicial.email:}") String email,
            @Value("${fleetops.admin-inicial.senha:}") String senha,
            @Value("${fleetops.admin-inicial.nome:Administrador}") String nome) {
        this.usuarios = usuarios;
        this.codificadorDeSenha = codificadorDeSenha;
        this.email = email;
        this.senha = senha;
        this.nome = nome;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }

        if (email.isBlank() || senha.isBlank()) {
            LOG.warn("""
                    Nenhum usuário cadastrado e nenhum administrador inicial configurado.
                    Ninguém conseguirá autenticar. Defina FLEETOPS_ADMIN_EMAIL e \
                    FLEETOPS_ADMIN_SENHA no ambiente e reinicie a aplicação.""");
            return;
        }

        if (senha.length() < TAMANHO_MINIMO_DA_SENHA) {
            LOG.error(
                    "FLEETOPS_ADMIN_SENHA tem menos de {} caracteres; o administrador inicial NÃO foi criado.",
                    TAMANHO_MINIMO_DA_SENHA);
            return;
        }

        Usuario administrador = new Usuario(nome, email, codificadorDeSenha.encode(senha), Perfil.ADMIN);
        usuarios.save(administrador);
        LOG.warn("""
                Administrador inicial criado para {}.
                Faça login, troque a senha e remova FLEETOPS_ADMIN_EMAIL e \
                FLEETOPS_ADMIN_SENHA do ambiente.""", administrador.getEmail());
    }
}
