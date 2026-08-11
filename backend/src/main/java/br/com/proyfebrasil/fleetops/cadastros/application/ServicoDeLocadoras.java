package br.com.proyfebrasil.fleetops.cadastros.application;

import static br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras.curinga;

import br.com.proyfebrasil.fleetops.cadastros.domain.CanaisDeAtendimento;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.infra.LocadoraRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import br.com.proyfebrasil.fleetops.shared.security.CriptografiaCredenciais;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de locadoras.
 *
 * <p>As credenciais do portal são cifradas aqui, na camada de aplicação, que é onde a
 * chave está disponível — a entidade nunca chega a segurar um segredo em claro (RN-20).
 */
@Service
public class ServicoDeLocadoras {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeLocadoras.class);

    private final LocadoraRepository locadoras;
    private final CriptografiaCredenciais criptografia;

    public ServicoDeLocadoras(LocadoraRepository locadoras, CriptografiaCredenciais criptografia) {
        this.locadoras = locadoras;
        this.criptografia = criptografia;
    }

    /**
     * Dados de criação ou atualização de uma locadora.
     *
     * @param portalLogin login do portal em claro; {@code null} preserva o atual
     * @param portalSenha senha do portal em claro; {@code null} preserva a atual
     */
    public record DadosDaLocadora(
            String nome,
            TipoLocadora tipo,
            String consultor,
            String telefone,
            String email,
            String portalUrl,
            String portalLogin,
            String portalSenha,
            CanaisDeAtendimento canais,
            String observacoes,
            boolean ativa) {
    }

    /** Filtro de listagem de locadoras. */
    public record FiltroLocadora(String termo, TipoLocadora tipo, Boolean ativa) {
    }

    /** Credencial revelada sob solicitação explícita e auditada (RN-20). */
    public record CredencialRevelada(String login, String senha) {
    }

    @Transactional(readOnly = true)
    public Page<Locadora> listar(FiltroLocadora filtro, Pageable paginacao) {
        return locadoras.pesquisar(curinga(filtro.termo()), filtro.tipo(), filtro.ativa(), paginacao);
    }

    @Transactional(readOnly = true)
    public Locadora buscar(Long id) {
        return locadoras.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Locadora", id));
    }

    @Transactional
    public Locadora criar(DadosDaLocadora dados) {
        garantirNomeDisponivel(dados.nome(), null);
        Locadora locadora = new Locadora(dados.nome(), dados.tipo());
        aplicar(locadora, dados);
        return locadoras.save(locadora);
    }

    @Transactional
    public Locadora atualizar(Long id, DadosDaLocadora dados) {
        Locadora locadora = buscar(id);
        garantirNomeDisponivel(dados.nome(), id);
        aplicar(locadora, dados);
        return locadora;
    }

    @Transactional
    public void excluir(Long id) {
        Locadora locadora = buscar(id);
        // Excluir o cadastro sem descartar o segredo deixaria uma credencial cifrada
        // órfã no banco, sem dono e sem quem a rotacione.
        locadora.limparCredenciais();
        locadora.excluir(Instant.now());
    }

    /**
     * Revela as credenciais do portal em claro.
     *
     * <p>Operação sensível e por isso explicitamente registrada em log com o solicitante:
     * o acesso a um segredo tem de deixar rastro, mesmo quando autorizado (RN-20).
     *
     * @throws NegocioException se a locadora não tiver credenciais cadastradas
     */
    @Transactional(readOnly = true)
    public CredencialRevelada revelarCredenciais(Long id, String solicitante) {
        Locadora locadora = buscar(id);
        if (!locadora.possuiCredenciais()) {
            throw new NegocioException(
                    ErroCadastro.CREDENCIAL_INDISPONIVEL,
                    "Esta locadora não tem credenciais de portal cadastradas.");
        }
        LOG.warn("Credenciais da locadora {} ({}) reveladas para {}",
                locadora.getId(), locadora.getNome(), solicitante);
        return new CredencialRevelada(
                criptografia.decifrar(locadora.getPortalLoginCifrado()),
                criptografia.decifrar(locadora.getPortalSenhaCifrada()));
    }

    private void aplicar(Locadora locadora, DadosDaLocadora dados) {
        locadora.alterarDadosBasicos(
                dados.nome(), dados.tipo(), dados.consultor(), dados.telefone(), dados.email(), dados.portalUrl());
        locadora.alterarCanais(dados.canais());
        locadora.alterarObservacoes(dados.observacoes());
        locadora.definirCredenciaisCifradas(
                cifrarSePresente(dados.portalLogin()), cifrarSePresente(dados.portalSenha()));
        if (dados.ativa()) {
            locadora.ativar();
        } else {
            locadora.desativar();
        }
    }

    /**
     * Cifra o valor informado. {@code null} devolve {@code null}, o que instrui a entidade
     * a preservar a credencial atual — é assim que editar o cadastro não obriga a
     * redigitar a senha.
     */
    private String cifrarSePresente(String valorEmClaro) {
        if (valorEmClaro == null) {
            return null;
        }
        return valorEmClaro.isBlank() ? "" : criptografia.cifrar(valorEmClaro);
    }

    private void garantirNomeDisponivel(String nome, Long idAtual) {
        if (locadoras.existeOutraComNome(nome == null ? null : nome.trim(), idAtual)) {
            throw new NegocioException(
                    ErroCadastro.NOME_LOCADORA_DUPLICADO,
                    "Já existe uma locadora cadastrada com este nome.",
                    Map.of("nome", String.valueOf(nome)));
        }
    }
}
