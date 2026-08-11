package br.com.proyfebrasil.fleetops.administracao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import java.time.Instant;
import java.util.Optional;
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
@DisplayName("ServicoDeUsuarios — administração de usuários")
class ServicoDeUsuariosTest {

    private static final String HASH = "{bcrypt}hash-fake";

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private SessaoRefreshRepository sessoes;

    @Mock
    private PasswordEncoder codificadorDeSenha;

    @InjectMocks
    private ServicoDeUsuarios servico;

    private Usuario existente;

    @BeforeEach
    void preparar() {
        existente = new Usuario("Ana Souza", "ana@proyfebrasil.com.br", HASH, Perfil.GESTOR_FROTA);
        ReflectionTestUtils.setField(existente, "id", 7L);
    }

    @Test
    @DisplayName("cria usuário normalizando o e-mail e cifrando a senha")
    void criaUsuario() {
        when(usuarios.existeOutroComEmail("novo@proyfebrasil.com.br", null)).thenReturn(false);
        when(codificadorDeSenha.encode("SenhaInicial1")).thenReturn(HASH);
        when(usuarios.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Usuario criado = servico.criar(
                "Novo Usuário", "NOVO@Proyfebrasil.com.br", Perfil.CONSULTA, "SenhaInicial1", true);

        assertThat(criado.getEmail()).isEqualTo("novo@proyfebrasil.com.br");
        assertThat(criado.getSenhaHash()).isEqualTo(HASH);
        assertThat(criado.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("cria usuário já desativado quando solicitado")
    void criaUsuarioInativo() {
        when(usuarios.existeOutroComEmail(anyString(), eq(null))).thenReturn(false);
        when(codificadorDeSenha.encode(anyString())).thenReturn(HASH);
        when(usuarios.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Usuario criado = servico.criar("Novo", "novo@proyfebrasil.com.br", Perfil.CONSULTA, "SenhaInicial1", false);

        assertThat(criado.isAtivo()).isFalse();
    }

    @Test
    @DisplayName("rejeita e-mail já cadastrado entre usuários não excluídos")
    void rejeitaEmailDuplicado() {
        when(usuarios.existeOutroComEmail("ana@proyfebrasil.com.br", null)).thenReturn(true);

        assertThatThrownBy(() ->
                        servico.criar("Outra Ana", "ana@proyfebrasil.com.br", Perfil.CONSULTA, "SenhaInicial1", true))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.CONFLITO_DE_ESTADO);
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("rejeita criação sem senha inicial")
    void rejeitaSemSenha() {
        when(usuarios.existeOutroComEmail(anyString(), eq(null))).thenReturn(false);

        assertThatThrownBy(() -> servico.criar("Novo", "novo@proyfebrasil.com.br", Perfil.CONSULTA, "  ", true))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.VALIDACAO);
    }

    @Test
    @DisplayName("mantém a senha atual quando a atualização não informa uma nova")
    void atualizaSemTrocarSenha() {
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(existente));
        when(usuarios.existeOutroComEmail("ana@proyfebrasil.com.br", 7L)).thenReturn(false);

        Usuario atualizado = servico.atualizar(7L, "Ana S. Souza", "ana@proyfebrasil.com.br", Perfil.ADMIN, null, true);

        assertThat(atualizado.getSenhaHash()).isEqualTo(HASH);
        assertThat(atualizado.getNome()).isEqualTo("Ana S. Souza");
        assertThat(atualizado.getPerfil()).isEqualTo(Perfil.ADMIN);
        verify(sessoes, never()).revogarSessoesDoUsuario(anyLong(), any());
    }

    @Test
    @DisplayName("trocar a senha encerra as sessões abertas do usuário")
    void trocaDeSenhaRevogaSessoes() {
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(existente));
        when(usuarios.existeOutroComEmail("ana@proyfebrasil.com.br", 7L)).thenReturn(false);
        when(codificadorDeSenha.encode("NovaSenha123")).thenReturn("{bcrypt}novo-hash");

        servico.atualizar(7L, "Ana", "ana@proyfebrasil.com.br", Perfil.GESTOR_FROTA, "NovaSenha123", true);

        assertThat(existente.getSenhaHash()).isEqualTo("{bcrypt}novo-hash");
        verify(sessoes).revogarSessoesDoUsuario(eq(7L), any(Instant.class));
    }

    @Test
    @DisplayName("desativar um usuário ativo encerra suas sessões")
    void desativacaoRevogaSessoes() {
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(existente));
        when(usuarios.existeOutroComEmail("ana@proyfebrasil.com.br", 7L)).thenReturn(false);

        servico.atualizar(7L, "Ana", "ana@proyfebrasil.com.br", Perfil.GESTOR_FROTA, null, false);

        assertThat(existente.isAtivo()).isFalse();
        verify(sessoes).revogarSessoesDoUsuario(eq(7L), any(Instant.class));
    }

    @Test
    @DisplayName("exclusão é lógica e encerra as sessões")
    void exclusaoLogica() {
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(existente));

        servico.excluir(7L, "admin@proyfebrasil.com.br");

        assertThat(existente.isExcluida()).isTrue();
        verify(usuarios, never()).delete(any());
        verify(sessoes).revogarSessoesDoUsuario(eq(7L), any(Instant.class));
    }

    @Test
    @DisplayName("impede que o usuário exclua a si próprio")
    void impedeAutoExclusao() {
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servico.excluir(7L, "Ana@Proyfebrasil.com.br"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("próprio usuário")
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.CONFLITO_DE_ESTADO);
        assertThat(existente.isExcluida()).isFalse();
    }

    @Test
    @DisplayName("buscar usuário inexistente resulta em 404 de domínio")
    void buscaInexistente() {
        when(usuarios.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroComum.RECURSO_NAO_ENCONTRADO);
    }
}
