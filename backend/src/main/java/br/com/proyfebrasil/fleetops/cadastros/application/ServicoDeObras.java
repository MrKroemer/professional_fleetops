package br.com.proyfebrasil.fleetops.cadastros.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra;
import br.com.proyfebrasil.fleetops.cadastros.infra.ObraRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de obras. */
@Service
public class ServicoDeObras {

    private final ObraRepository obras;

    public ServicoDeObras(ObraRepository obras) {
        this.obras = obras;
    }

    /**
     * Dados de criação ou atualização de uma obra.
     *
     * @param codigo      código interno, ex.: 24.019
     * @param nome        nome da frente de trabalho
     * @param cliente     cliente contratante
     * @param cidade      cidade principal da obra
     * @param uf          sigla da unidade federativa
     * @param status      situação da obra
     * @param dataInicio  início previsto ou efetivo
     * @param dataFim     encerramento previsto ou efetivo
     * @param observacoes anotações livres
     */
    public record DadosDaObra(
            String codigo,
            String nome,
            String cliente,
            String cidade,
            String uf,
            StatusObra status,
            LocalDate dataInicio,
            LocalDate dataFim,
            String observacoes) {
    }

    /** Filtro de listagem de obras. */
    public record FiltroObra(String termo, StatusObra status, String uf) {
    }

    @Transactional(readOnly = true)
    public Page<Obra> listar(FiltroObra filtro, Pageable paginacao) {
        return obras.pesquisar(
                curinga(filtro.termo()),
                filtro.status(),
                filtro.uf() == null || filtro.uf().isBlank() ? null : Obra.normalizarUf(filtro.uf()),
                paginacao);
    }

    @Transactional(readOnly = true)
    public Obra buscar(Long id) {
        return obras.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Obra", id));
    }

    @Transactional
    public Obra criar(DadosDaObra dados) {
        garantirCodigoDisponivel(dados.codigo(), null);
        Obra obra = new Obra(dados.codigo(), dados.nome(), dados.cidade(), dados.uf());
        aplicar(obra, dados);
        return obras.save(obra);
    }

    @Transactional
    public Obra atualizar(Long id, DadosDaObra dados) {
        Obra obra = buscar(id);
        garantirCodigoDisponivel(dados.codigo(), id);
        obra.alterarDadosBasicos(dados.codigo(), dados.nome(), dados.cliente(), dados.cidade(), dados.uf());
        aplicar(obra, dados);
        return obra;
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).excluir(Instant.now());
    }

    private void aplicar(Obra obra, DadosDaObra dados) {
        obra.alterarDadosBasicos(dados.codigo(), dados.nome(), dados.cliente(), dados.cidade(), dados.uf());
        obra.definirPeriodo(dados.dataInicio(), dados.dataFim());
        obra.alterarStatus(dados.status() == null ? StatusObra.ATIVA : dados.status());
        obra.alterarObservacoes(dados.observacoes());
    }

    private void garantirCodigoDisponivel(String codigo, Long idAtual) {
        if (obras.existeOutraComCodigo(codigo == null ? null : codigo.trim(), idAtual)) {
            throw new NegocioException(
                    ErroCadastro.CODIGO_OBRA_DUPLICADO,
                    "Já existe uma obra com o código informado.",
                    Map.of("codigo", String.valueOf(codigo)));
        }
    }

    static String curinga(String termo) {
        return termo == null || termo.isBlank() ? null : "%" + termo.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
