package br.com.proyfebrasil.fleetops.administracao.application;

import br.com.proyfebrasil.fleetops.administracao.domain.SessaoRefresh;
import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.security.ServicoDeTokens;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticação, renovação e encerramento de sessão.
 *
 * <p>A renovação usa <strong>rotação de refresh token</strong>: cada uso revoga a sessão
 * anterior e cria uma nova. Assim, um refresh token vazado só é útil até a próxima
 * renovação legítima — e sua reapresentação já revogada é recusada.
 */
@Service
public class ServicoDeAutenticacao {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeAutenticacao.class);

    /**
     * Hash descartável usado para gastar o mesmo tempo de verificação quando o e-mail não
     * existe, impedindo a enumeração de usuários por diferença de tempo de resposta.
     */
    private static final String HASH_FICTICIO =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UsuarioRepository usuarios;
    private final SessaoRefreshRepository sessoes;
    private final RevogadorDeSessoes revogador;
    private final PasswordEncoder codificadorDeSenha;
    private final ServicoDeTokens tokens;

    public ServicoDeAutenticacao(
            UsuarioRepository usuarios,
            SessaoRefreshRepository sessoes,
            RevogadorDeSessoes revogador,
            PasswordEncoder codificadorDeSenha,
            ServicoDeTokens tokens) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.revogador = revogador;
        this.codificadorDeSenha = codificadorDeSenha;
        this.tokens = tokens;
    }

    /**
     * Sessão recém-criada.
     *
     * @param usuario          usuário autenticado
     * @param accessToken      token de acesso e sua expiração
     * @param refreshToken     token de renovação, destinado ao cookie {@code httpOnly}
     */
    public record SessaoCriada(
            Usuario usuario,
            ServicoDeTokens.TokenEmitido accessToken,
            ServicoDeTokens.RefreshEmitido refreshToken) {
    }

    /** Contexto da requisição de login, registrado junto da sessão para rastreabilidade. */
    public record OrigemDaSessao(String userAgent, String enderecoIp) {
    }

    @Transactional
    public SessaoCriada autenticar(String email, String senha, OrigemDaSessao origem) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        Optional<Usuario> encontrado = usuarios.buscarPorEmail(emailNormalizado);

        if (encontrado.isEmpty()) {
            codificadorDeSenha.matches(senha, HASH_FICTICIO);
            LOG.info("Tentativa de login para e-mail inexistente");
            throw credenciaisInvalidas();
        }

        Usuario usuario = encontrado.get();
        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            LOG.info("Senha incorreta para o usuário {}", usuario.getId());
            throw credenciaisInvalidas();
        }
        if (!usuario.podeAutenticar()) {
            LOG.info("Login recusado: usuário {} está inativo", usuario.getId());
            throw new NegocioException(
                    ErroComum.USUARIO_INATIVO,
                    "Este usuário está inativo. Procure um administrador do sistema.");
        }

        usuario.registrarAcesso(Instant.now());
        return abrirSessao(usuario, origem);
    }

    /**
     * Renova a sessão a partir de um refresh token válido, revogando o token apresentado.
     *
     * @throws NegocioException com {@link ErroComum#SESSAO_INVALIDA} quando o token é
     *     desconhecido, já revogado ou expirado
     */
    @Transactional
    public SessaoCriada renovar(String refreshToken, OrigemDaSessao origem) {
        ServicoDeTokens.RefreshValidado validado = tokens.validarRefreshToken(refreshToken);
        Instant agora = Instant.now();

        SessaoRefresh sessao = sessoes.findByJti(validado.jti())
                .orElseThrow(() -> {
                    LOG.warn("Refresh token com jti desconhecido apresentado: {}", validado.jti());
                    return sessaoInvalida();
                });

        if (!sessao.estaValidaEm(agora)) {
            // Reapresentação de um token já revogado indica vazamento: encerra todas as
            // sessões. A revogação vai em transação própria para não ser desfeita pelo
            // rollback da exceção lançada logo abaixo.
            revogador.revogarTodasAsSessoes(sessao.getUsuario().getId());
            LOG.warn("Refresh token revogado reapresentado; todas as sessões do usuário {} foram encerradas",
                    sessao.getUsuario().getId());
            throw sessaoInvalida();
        }

        Usuario usuario = sessao.getUsuario();
        if (!usuario.podeAutenticar()) {
            revogador.revogarTodasAsSessoes(usuario.getId());
            throw new NegocioException(
                    ErroComum.USUARIO_INATIVO,
                    "Este usuário está inativo. Procure um administrador do sistema.");
        }

        sessao.revogar(agora);
        return abrirSessao(usuario, origem);
    }

    /**
     * Encerra a sessão correspondente ao refresh token apresentado.
     *
     * <p>Deliberadamente silencioso: um token inválido não produz erro, porque o logout
     * precisa ser idempotente e não deve revelar se a sessão existia.
     */
    @Transactional
    public void encerrar(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            ServicoDeTokens.RefreshValidado validado = tokens.validarRefreshToken(refreshToken);
            sessoes.findByJti(validado.jti()).ifPresent(sessao -> sessao.revogar(Instant.now()));
        } catch (NegocioException ex) {
            LOG.debug("Logout com refresh token inválido; nada a revogar");
        }
    }

    private SessaoCriada abrirSessao(Usuario usuario, OrigemDaSessao origem) {
        ServicoDeTokens.TokenEmitido access = tokens.emitirAccessToken(usuario.dadosDoToken());
        ServicoDeTokens.RefreshEmitido refresh = tokens.emitirRefreshToken(usuario.dadosDoToken());
        sessoes.save(new SessaoRefresh(
                usuario, refresh.jti(), refresh.expiraEm(), origem.userAgent(), origem.enderecoIp()));
        return new SessaoCriada(usuario, access, refresh);
    }

    private NegocioException credenciaisInvalidas() {
        // Mensagem única para e-mail inexistente e senha incorreta: não revela quais
        // e-mails estão cadastrados.
        return new NegocioException(ErroComum.CREDENCIAIS_INVALIDAS, "E-mail ou senha inválidos.");
    }

    private NegocioException sessaoInvalida() {
        return new NegocioException(ErroComum.SESSAO_INVALIDA, "Sessão expirada. Faça login novamente.");
    }
}
