package br.com.proyfebrasil.fleetops.cadastros.application;

import static br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras.curinga;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Cpf;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import br.com.proyfebrasil.fleetops.cadastros.infra.CondutorRepository;
import br.com.proyfebrasil.fleetops.cadastros.infra.ObraRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de condutores.
 *
 * <p>Concentra a RN-16: alerta de vencimento de CNH com 60 e 30 dias de antecedência e
 * bloqueio de vínculo a contrato quando a habilitação está vencida.
 */
@Service
public class ServicoDeCondutores {

    private final CondutorRepository condutores;
    private final ObraRepository obras;
    private final Clock relogio;

    public ServicoDeCondutores(CondutorRepository condutores, ObraRepository obras, Clock relogio) {
        this.condutores = condutores;
        this.obras = obras;
        this.relogio = relogio;
    }

    /** Dados de criação ou atualização de um condutor. */
    public record DadosDoCondutor(
            String nome,
            String cpf,
            String cargo,
            String telefone,
            String email,
            String cnhNumero,
            String cnhCategoria,
            LocalDate cnhValidade,
            Long obraAtualId,
            StatusCondutor status,
            String observacoes) {
    }

    /** Filtro de listagem de condutores. */
    public record FiltroCondutor(String termo, StatusCondutor status, Long obraId) {
    }

    /**
     * Condutor com a situação da CNH já avaliada (RN-16).
     *
     * @param condutor         o condutor
     * @param diasParaVencer   dias até o vencimento; negativo se vencida, nulo se sem CNH
     * @param vencida          indica que a habilitação já venceu
     * @param emAlerta         indica que o vencimento entrou na faixa de 60 dias
     */
    public record SituacaoDaCnh(Condutor condutor, Long diasParaVencer, boolean vencida, boolean emAlerta) {
    }

    /**
     * Lista condutores já com a situação da CNH avaliada.
     *
     * <p>A avaliação é feita em memória sobre a página carregada. Consultar o banco por
     * linha para descobrir a situação seria um N+1 desnecessário: a regra depende apenas
     * da data de validade, que já veio na consulta.
     */
    @Transactional(readOnly = true)
    public Page<SituacaoDaCnh> listar(FiltroCondutor filtro, Pageable paginacao) {
        return condutores
                .pesquisar(curinga(filtro.termo()), filtro.status(), filtro.obraId(), paginacao)
                .map(this::avaliar);
    }

    @Transactional(readOnly = true)
    public Condutor buscar(Long id) {
        return condutores.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Condutor", id));
    }

    /** Avalia a situação da CNH de um condutor na data de hoje. */
    @Transactional(readOnly = true)
    public SituacaoDaCnh avaliarCnh(Long id) {
        return avaliar(buscar(id));
    }

    /**
     * Condutores com CNH vencida ou vencendo dentro da maior faixa de alerta (RN-16).
     * Alimenta a central de pendências da Fase 4.
     */
    @Transactional(readOnly = true)
    public List<SituacaoDaCnh> comCnhEmAlerta() {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate limite = hoje.plusDays(Condutor.ANTECEDENCIAS_DE_ALERTA_CNH[0]);
        return condutores.comCnhVencendoAte(limite).stream().map(this::avaliar).toList();
    }

    @Transactional
    public Condutor criar(DadosDoCondutor dados) {
        String cpf = normalizarCpf(dados.cpf());
        garantirCpfDisponivel(cpf, null);
        Condutor condutor = new Condutor(dados.nome(), cpf);
        aplicar(condutor, dados);
        return condutores.save(condutor);
    }

    @Transactional
    public Condutor atualizar(Long id, DadosDoCondutor dados) {
        Condutor condutor = buscar(id);
        String cpf = normalizarCpf(dados.cpf());
        garantirCpfDisponivel(cpf, id);
        aplicar(condutor, dados);
        return condutor;
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).excluir(Instant.now());
    }

    /**
     * Verifica se o condutor pode assumir um contrato de locação (RN-16).
     *
     * <p>Existe como método público porque a Fase 2 precisa desta checagem antes de
     * vincular o condutor ao contrato — a regra vive aqui, e não duplicada lá.
     *
     * @throws NegocioException com {@link ErroCadastro#CNH_VENCIDA} quando a CNH venceu
     */
    @Transactional(readOnly = true)
    public void exigirAptidaoParaContrato(Long condutorId) {
        Condutor condutor = buscar(condutorId);
        LocalDate hoje = LocalDate.now(relogio);
        if (condutor.cnhVencidaEm(hoje)) {
            throw new NegocioException(
                    ErroCadastro.CNH_VENCIDA,
                    "A CNH de %s venceu em %s. Regularize antes de vinculá-lo a um contrato."
                            .formatted(condutor.getNome(), condutor.getCnhValidade()),
                    Map.of(
                            "condutorId", condutorId,
                            "cnhValidade", String.valueOf(condutor.getCnhValidade())));
        }
    }

    private SituacaoDaCnh avaliar(Condutor condutor) {
        LocalDate hoje = LocalDate.now(relogio);
        return new SituacaoDaCnh(
                condutor,
                condutor.diasParaVencimentoDaCnh(hoje),
                condutor.cnhVencidaEm(hoje),
                condutor.cnhEmAlertaEm(hoje));
    }

    private void aplicar(Condutor condutor, DadosDoCondutor dados) {
        condutor.alterarDadosBasicos(
                dados.nome(), dados.cpf(), dados.cargo(), dados.telefone(), dados.email());
        condutor.alterarCnh(dados.cnhNumero(), dados.cnhCategoria(), dados.cnhValidade());
        condutor.alocarNaObra(resolverObra(dados.obraAtualId()));
        condutor.alterarStatus(dados.status() == null ? StatusCondutor.ATIVO : dados.status());
        condutor.alterarObservacoes(dados.observacoes());
    }

    private Obra resolverObra(Long obraId) {
        if (obraId == null) {
            return null;
        }
        return obras.buscarPorId(obraId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra", obraId));
    }

    private String normalizarCpf(String bruto) {
        try {
            return Cpf.normalizar(bruto);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException(ErroCadastro.CPF_INVALIDO, ex.getMessage());
        }
    }

    private void garantirCpfDisponivel(String cpf, Long idAtual) {
        if (condutores.existeOutroComCpf(cpf, idAtual)) {
            throw new NegocioException(
                    ErroCadastro.CPF_DUPLICADO,
                    "Já existe um condutor cadastrado com este CPF.",
                    Map.of("cpf", Cpf.formatar(cpf)));
        }
    }
}
