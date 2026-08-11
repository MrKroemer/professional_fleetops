package br.com.proyfebrasil.fleetops.administracao.application;

import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.administracao.infra.SessaoRefreshRepository;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Administração de usuários. Todas as operações de escrita são restritas ao perfil ADMIN (RN-19). */
@Service
public class ServicoDeUsuarios {

    private final UsuarioRepository usuarios;
    private final SessaoRefreshRepository sessoes;
    private final PasswordEncoder codificadorDeSenha;

    public ServicoDeUsuarios(
            UsuarioRepository usuarios, SessaoRefreshRepository sessoes, PasswordEncoder codificadorDeSenha) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    /**
     * Filtro de listagem.
     *
     * @param termo  busca parcial por nome ou e-mail; {@code null} não filtra
     * @param perfil perfil exato; {@code null} não filtra
     * @param ativo  status; {@code null} não filtra
     */
    public record FiltroUsuario(String termo, Perfil perfil, Boolean ativo) {
    }

    @Transactional(readOnly = true)
    public Page<Usuario> listar(FiltroUsuario filtro, Pageable paginacao) {
        String termo = filtro.termo() == null || filtro.termo().isBlank()
                ? null
                : "%" + filtro.termo().trim().toLowerCase(Locale.ROOT) + "%";
        return usuarios.pesquisar(termo, filtro.perfil(), filtro.ativo(), paginacao);
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    @Transactional
    public Usuario criar(String nome, String email, Perfil perfil, String senha, boolean ativo) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        garantirEmailDisponivel(emailNormalizado, null);
        exigirSenha(senha);

        Usuario usuario = new Usuario(nome, emailNormalizado, codificadorDeSenha.encode(senha), perfil);
        if (!ativo) {
            usuario.desativar();
        }
        return usuarios.save(usuario);
    }

    @Transactional
    public Usuario atualizar(Long id, String nome, String email, Perfil perfil, String senha, boolean ativo) {
        Usuario usuario = buscar(id);
        String emailNormalizado = Usuario.normalizarEmail(email);
        garantirEmailDisponivel(emailNormalizado, id);

        usuario.alterarNome(nome);
        usuario.alterarEmail(emailNormalizado);
        usuario.alterarPerfil(perfil);
        if (senha != null && !senha.isBlank()) {
            usuario.alterarSenha(codificadorDeSenha.encode(senha));
            // Trocar a senha encerra as sessões abertas: quem tiver um refresh token antigo
            // perde o acesso imediatamente.
            sessoes.revogarSessoesDoUsuario(id, Instant.now());
        }

        boolean estavaAtivo = usuario.isAtivo();
        if (ativo) {
            usuario.ativar();
        } else {
            usuario.desativar();
            if (estavaAtivo) {
                sessoes.revogarSessoesDoUsuario(id, Instant.now());
            }
        }
        return usuario;
    }

    /**
     * Exclusão lógica do usuário, encerrando suas sessões.
     *
     * @throws NegocioException quando o usuário tenta excluir a si próprio — remover o
     *     último administrador logado deixaria o sistema sem quem administrá-lo
     */
    @Transactional
    public void excluir(Long id, String emailDoSolicitante) {
        Usuario usuario = buscar(id);
        if (usuario.getEmail().equalsIgnoreCase(emailDoSolicitante)) {
            throw new NegocioException(
                    ErroComum.CONFLITO_DE_ESTADO,
                    "Não é possível excluir o próprio usuário. Peça a outro administrador.");
        }
        usuario.excluir(Instant.now());
        sessoes.revogarSessoesDoUsuario(id, Instant.now());
    }

    private void garantirEmailDisponivel(String email, Long idAtual) {
        if (usuarios.existeOutroComEmail(email, idAtual)) {
            throw new NegocioException(
                    ErroComum.CONFLITO_DE_ESTADO,
                    "Já existe um usuário cadastrado com este e-mail.",
                    Map.of("email", email));
        }
    }

    private void exigirSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new NegocioException(ErroComum.VALIDACAO, "Informe a senha inicial do usuário.");
        }
    }
}
