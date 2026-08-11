package br.com.proyfebrasil.fleetops.administracao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Usuario — invariantes de domínio")
class UsuarioTest {

    private static final String HASH = "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Test
    @DisplayName("normaliza o e-mail para caixa baixa e sem espaços")
    void normalizaEmail() {
        Usuario usuario = new Usuario("Ana Souza", "  Ana.Souza@Proyfebrasil.COM.BR  ", HASH, Perfil.GESTOR_FROTA);

        assertThat(usuario.getEmail()).isEqualTo("ana.souza@proyfebrasil.com.br");
    }

    @Test
    @DisplayName("rejeita criação sem nome, e-mail, senha ou perfil")
    void exigeCamposObrigatorios() {
        assertThatThrownBy(() -> new Usuario(" ", "a@b.com", HASH, Perfil.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nome");
        assertThatThrownBy(() -> new Usuario("Ana", "", HASH, Perfil.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        assertThatThrownBy(() -> new Usuario("Ana", "a@b.com", null, Perfil.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senhaHash");
        assertThatThrownBy(() -> new Usuario("Ana", "a@b.com", HASH, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("perfil");
    }

    @Test
    @DisplayName("nasce ativo e apto a autenticar")
    void nasceAtivo() {
        Usuario usuario = new Usuario("Ana", "ana@proyfebrasil.com.br", HASH, Perfil.CONSULTA);

        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.podeAutenticar()).isTrue();
    }

    @Test
    @DisplayName("usuário desativado não pode autenticar")
    void desativadoNaoAutentica() {
        Usuario usuario = new Usuario("Ana", "ana@proyfebrasil.com.br", HASH, Perfil.CONSULTA);

        usuario.desativar();

        assertThat(usuario.podeAutenticar()).isFalse();
    }

    @Test
    @DisplayName("usuário excluído logicamente não pode autenticar, mesmo estando ativo")
    void excluidoNaoAutentica() {
        Usuario usuario = new Usuario("Ana", "ana@proyfebrasil.com.br", HASH, Perfil.ADMIN);

        usuario.excluir(Instant.now());

        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.podeAutenticar()).isFalse();
    }

    @Test
    @DisplayName("projeta apenas os dados necessários ao token, sem a senha")
    void projetaDadosDoToken() {
        Usuario usuario = new Usuario("Ana Souza", "ana@proyfebrasil.com.br", HASH, Perfil.GESTOR_FROTA);

        assertThat(usuario.dadosDoToken().email()).isEqualTo("ana@proyfebrasil.com.br");
        assertThat(usuario.dadosDoToken().nome()).isEqualTo("Ana Souza");
        assertThat(usuario.dadosDoToken().perfil()).isEqualTo(Perfil.GESTOR_FROTA);
    }

    @Test
    @DisplayName("alterações de perfil e e-mail preservam as invariantes")
    void alteracoesValidam() {
        Usuario usuario = new Usuario("Ana", "ana@proyfebrasil.com.br", HASH, Perfil.CONSULTA);

        usuario.alterarPerfil(Perfil.ADMIN);
        usuario.alterarEmail("ANA.NOVA@PROYFEBRASIL.COM.BR");
        usuario.registrarAcesso(Instant.parse("2026-03-15T12:00:00Z"));

        assertThat(usuario.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(usuario.getEmail()).isEqualTo("ana.nova@proyfebrasil.com.br");
        assertThat(usuario.getUltimoAcessoEm()).isEqualTo(Instant.parse("2026-03-15T12:00:00Z"));
        assertThatThrownBy(() -> usuario.alterarNome(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
