package br.com.proyfebrasil.fleetops.administracao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeAutenticacao.OrigemDaSessao;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeAutenticacao.SessaoCriada;
import br.com.proyfebrasil.fleetops.administracao.domain.SessaoRefresh;
import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import br.com.proyfebrasil.fleetops.shared.security.ServicoDeTokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServicoDeAutenticacao — login, rotação e revogação de sessão")
class ServicoDeAutenticacaoTest {

    private static final String SENHA = "Fleet@2026";
    private static final String HASH = "{bcrypt}hash-fake";
    private static final OrigemDaSessao ORIGEM = new OrigemDaSessao("JUnit", "127.0.0.1");

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private SessaoRefreshRepository sessoes;

    @Mock
    private RevogadorDeSessoes revogador;

    @Mock
    private PasswordEncoder codificadorDeSenha;

    @Mock
    private ServicoDeTokens tokens;

    @InjectMocks
    private ServicoDeAutenticacao servico;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuario = new Usuario("Ana Souza", "ana@proyfebrasil.com.br", HASH, Perfil.GESTOR_FROTA);
        ReflectionTestUtils.setField(usuario, "id", 7L);
    }

    // -----------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------

    @Test
    @DisplayName("autentica com credenciais corretas e abre uma sessão")
    void loginComSucesso() {
        when(usuarios.buscarPorEmail("ana@proyfebrasil.com.br")).thenReturn(Optional.of(usuario));
        when(codificadorDeSenha.matches(SENHA, HASH)).thenReturn(true);
        prepararEmissaoDeTokens();

        SessaoCriada sessao = servico.autenticar("Ana@Proyfebrasil.com.br", SENHA, ORIGEM);

        assertThat(sessao.usuario()).isSameAs(usuario);
        assertThat(sessao.accessToken().valor()).isEqualTo("access-token");
        assertThat(usuario.getUltimoAcessoEm()).isNotNull();
        verify(sessoes).save(any(SessaoRefresh.class));
    }

    @Test
    @DisplayName("recusa e-mail inexistente com a mesma mensagem de senha incorreta")
    void loginComEmailInexistente() {
        when(usuarios.buscarPorEmail("fantasma@proyfebrasil.com.br")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar("fantasma@proyfebrasil.com.br", SENHA, ORIGEM))
                .isInstanceOf(NegocioException.class)
                .hasMessage("E-mail ou senha inválidos.")
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.CREDENCIAIS_INVALIDAS);

        // Verificação de senha executada mesmo sem usuário: impede enumeração por tempo de resposta.
        verify(codificadorDeSenha).matches(eq(SENHA), any());
        verify(sessoes, never()).save(any());
    }

    @Test
    @DisplayName("recusa senha incorreta")
    void loginComSenhaIncorreta() {
        when(usuarios.buscarPorEmail("ana@proyfebrasil.com.br")).thenReturn(Optional.of(usuario));
        when(codificadorDeSenha.matches(SENHA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar("ana@proyfebrasil.com.br", SENHA, ORIGEM))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.CREDENCIAIS_INVALIDAS);
        verify(sessoes, never()).save(any());
    }

    @Test
    @DisplayName("recusa usuário inativo com erro distinto de credenciais")
    void loginComUsuarioInativo() {
        usuario.desativar();
        when(usuarios.buscarPorEmail("ana@proyfebrasil.com.br")).thenReturn(Optional.of(usuario));
        when(codificadorDeSenha.matches(SENHA, HASH)).thenReturn(true);

        assertThatThrownBy(() -> servico.autenticar("ana@proyfebrasil.com.br", SENHA, ORIGEM))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.USUARIO_INATIVO);
    }

    // -----------------------------------------------------------------
    // Renovação
    // -----------------------------------------------------------------

    @Test
    @DisplayName("renova a sessão revogando o refresh token apresentado (rotação)")
    void renovaComRotacao() {
        UUID jti = UUID.randomUUID();
        SessaoRefresh sessaoAtual = sessaoValida(jti);
        when(tokens.validarRefreshToken("refresh-antigo"))
                .thenReturn(new ServicoDeTokens.RefreshValidado(7L, jti));
        when(sessoes.findByJti(jti)).thenReturn(Optional.of(sessaoAtual));
        prepararEmissaoDeTokens();

        SessaoCriada renovada = servico.renovar("refresh-antigo", ORIGEM);

        assertThat(sessaoAtual.getRevogadoEm()).isNotNull();
        assertThat(renovada.refreshToken().valor()).isEqualTo("refresh-token");
        verify(sessoes).save(any(SessaoRefresh.class));
    }

    @Test
    @DisplayName("reapresentar um refresh token já revogado encerra todas as sessões do usuário")
    void reusoDeTokenRevogadoEncerraTudo() {
        UUID jti = UUID.randomUUID();
        SessaoRefresh revogada = sessaoValida(jti);
        revogada.revogar(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokens.validarRefreshToken("refresh-vazado"))
                .thenReturn(new ServicoDeTokens.RefreshValidado(7L, jti));
        when(sessoes.findByJti(jti)).thenReturn(Optional.of(revogada));

        assertThatThrownBy(() -> servico.renovar("refresh-vazado", ORIGEM))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.SESSAO_INVALIDA);

        verify(revogador).revogarTodasAsSessoes(7L);
        verify(sessoes, never()).save(any());
    }

    @Test
    @DisplayName("recusa refresh token cujo jti não existe em banco")
    void renovaComJtiDesconhecido() {
        UUID jti = UUID.randomUUID();
        when(tokens.validarRefreshToken("desconhecido"))
                .thenReturn(new ServicoDeTokens.RefreshValidado(7L, jti));
        when(sessoes.findByJti(jti)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.renovar("desconhecido", ORIGEM))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.SESSAO_INVALIDA);
    }

    @Test
    @DisplayName("recusa renovação de usuário desativado e encerra suas sessões")
    void renovaComUsuarioDesativado() {
        usuario.desativar();
        UUID jti = UUID.randomUUID();
        when(tokens.validarRefreshToken("refresh")).thenReturn(new ServicoDeTokens.RefreshValidado(7L, jti));
        when(sessoes.findByJti(jti)).thenReturn(Optional.of(sessaoValida(jti)));

        assertThatThrownBy(() -> servico.renovar("refresh", ORIGEM))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.USUARIO_INATIVO);
        verify(revogador).revogarTodasAsSessoes(7L);
    }

    // -----------------------------------------------------------------
    // Logout
    // -----------------------------------------------------------------

    @Test
    @DisplayName("logout revoga a sessão correspondente")
    void logoutRevoga() {
        UUID jti = UUID.randomUUID();
        SessaoRefresh sessao = sessaoValida(jti);
        when(tokens.validarRefreshToken("refresh")).thenReturn(new ServicoDeTokens.RefreshValidado(7L, jti));
        when(sessoes.findByJti(jti)).thenReturn(Optional.of(sessao));

        servico.encerrar("refresh");

        assertThat(sessao.getRevogadoEm()).isNotNull();
    }

    @Test
    @DisplayName("logout é idempotente e silencioso com token inválido ou ausente")
    void logoutIdempotente() {
        when(tokens.validarRefreshToken("invalido"))
                .thenThrow(new NegocioException(ErroComum.SESSAO_INVALIDA, "inválido"));

        servico.encerrar("invalido");
        servico.encerrar(null);
        servico.encerrar("  ");

        verify(revogador, never()).revogarTodasAsSessoes(anyLong());
    }

    // -----------------------------------------------------------------

    private void prepararEmissaoDeTokens() {
        Instant expiraEm = Instant.now().plus(15, ChronoUnit.MINUTES);
        when(tokens.emitirAccessToken(any()))
                .thenReturn(new ServicoDeTokens.TokenEmitido("access-token", expiraEm));
        when(tokens.emitirRefreshToken(any()))
                .thenReturn(new ServicoDeTokens.RefreshEmitido(
                        "refresh-token", UUID.randomUUID(), Instant.now().plus(7, ChronoUnit.DAYS)));
    }

    private SessaoRefresh sessaoValida(UUID jti) {
        return new SessaoRefresh(usuario, jti, Instant.now().plus(7, ChronoUnit.DAYS), "JUnit", "127.0.0.1");
    }
}
