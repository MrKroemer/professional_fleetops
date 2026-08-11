package br.com.proyfebrasil.fleetops.administracao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.proyfebrasil.fleetops.TesteDeIntegracao;
import br.com.proyfebrasil.fleetops.administracao.infra.UsuarioRepository;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifica que a trilha do Hibernate Envers está de fato gravando.
 *
 * <p>Sem este teste, a auditoria só seria exercitada em produção — e uma configuração
 * incorreta do Envers falha silenciosamente: a aplicação sobe normalmente e simplesmente
 * não registra histórico algum.
 *
 * <p>O teste de revisões <strong>não</strong> pode ser {@code @Transactional}: o Envers
 * grava a trilha no commit, não no flush. Rodar dentro de uma transação revertida pelo
 * runner de testes produziria zero revisões e um falso negativo. Por isso as escritas
 * passam por {@link TransactionTemplate}, que confirma cada uma delas.
 */
@DisplayName("Auditoria — trilha do Envers e campos de auditoria")
class AuditoriaEnversIT extends TesteDeIntegracao {

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    @Transactional
    @WithMockUser(username = "auditor@proyfebrasil.com.br")
    @DisplayName("preenche created_by/updated_by com o usuário autenticado")
    void preencheCamposDeAuditoria() {
        Usuario usuario = usuarios.save(
                new Usuario("Auditado", "auditado@proyfebrasil.com.br", "{bcrypt}hash", Perfil.CONSULTA));
        entityManager.flush();

        assertThat(usuario.getCriadoPor()).isEqualTo("auditor@proyfebrasil.com.br");
        assertThat(usuario.getAtualizadoPor()).isEqualTo("auditor@proyfebrasil.com.br");
        assertThat(usuario.getCriadoEm()).isNotNull();
        assertThat(usuario.getExcluidoEm()).isNull();
    }

    @Test
    @WithMockUser(username = "auditor@proyfebrasil.com.br")
    @DisplayName("registra uma revisão por alteração, com autor, e sem gravar o hash da senha")
    void registraRevisoes() {
        Long id = transacao.execute(status -> usuarios
                .save(new Usuario("Antes", "revisao@proyfebrasil.com.br", "{bcrypt}hash", Perfil.CONSULTA))
                .getId());

        transacao.executeWithoutResult(status -> {
            Usuario usuario = usuarios.buscarPorId(id).orElseThrow();
            usuario.alterarPerfil(Perfil.GESTOR_FROTA);
            usuario.alterarNome("Depois");
        });

        List<Number> revisoes = transacao.execute(status ->
                AuditReaderFactory.get(entityManager).getRevisions(Usuario.class, id));
        assertThat(revisoes).hasSizeGreaterThanOrEqualTo(2);

        Usuario naPrimeiraRevisao = transacao.execute(status ->
                AuditReaderFactory.get(entityManager).find(Usuario.class, id, revisoes.get(0)));
        assertThat(naPrimeiraRevisao.getNome()).isEqualTo("Antes");
        assertThat(naPrimeiraRevisao.getPerfil()).isEqualTo(Perfil.CONSULTA);

        Usuario naUltimaRevisao = transacao.execute(status -> AuditReaderFactory.get(entityManager)
                .find(Usuario.class, id, revisoes.get(revisoes.size() - 1)));
        assertThat(naUltimaRevisao.getNome()).isEqualTo("Depois");
        assertThat(naUltimaRevisao.getPerfil()).isEqualTo(Perfil.GESTOR_FROTA);

        Map<String, Object> revisao = jdbcTemplate.queryForMap(
                "select usuario, request_id from revisao_auditoria order by id desc limit 1");
        assertThat(revisao.get("usuario")).isEqualTo("auditor@proyfebrasil.com.br");

        // A senha e o último acesso ficam fora da trilha (marcados com @NotAudited).
        List<String> colunas = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'usuario_aud'", String.class);
        assertThat(colunas)
                .contains("nome", "email", "perfil", "ativo", "rev", "revtype")
                .doesNotContain("senha_hash", "ultimo_acesso_em");
    }
}
