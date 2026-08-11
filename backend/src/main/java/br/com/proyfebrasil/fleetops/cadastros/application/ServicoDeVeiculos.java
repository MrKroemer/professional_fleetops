package br.com.proyfebrasil.fleetops.cadastros.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Placa;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.cadastros.infra.LocadoraRepository;
import br.com.proyfebrasil.fleetops.cadastros.infra.VeiculoRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de veículos.
 *
 * <p>Responsável pela RN-02: a placa é normalizada na entrada — caixa alta, sem
 * separadores — e única entre os veículos não excluídos.
 */
@Service
public class ServicoDeVeiculos {

    private final VeiculoRepository veiculos;
    private final LocadoraRepository locadoras;

    public ServicoDeVeiculos(VeiculoRepository veiculos, LocadoraRepository locadoras) {
        this.veiculos = veiculos;
        this.locadoras = locadoras;
    }

    /** Dados de criação ou atualização de um veículo. */
    public record DadosDoVeiculo(
            String placa,
            String modelo,
            String fabricante,
            Integer anoFabricacao,
            CategoriaVeiculo categoria,
            Combustivel combustivel,
            Long locadoraId,
            String grupoTarifario,
            String codigoInterno,
            boolean possuiRastreador,
            String fornecedorRastreador,
            boolean possuiAdesivo,
            StatusVeiculo status,
            String observacoes) {
    }

    /** Filtro de listagem de veículos. */
    public record FiltroVeiculo(String termo, Long locadoraId, CategoriaVeiculo categoria, StatusVeiculo status) {
    }

    @Transactional(readOnly = true)
    public Page<Veiculo> listar(FiltroVeiculo filtro, Pageable paginacao) {
        return veiculos.pesquisar(
                termoDeBusca(filtro.termo()),
                termoDePlaca(filtro.termo()),
                filtro.locadoraId(),
                filtro.categoria(),
                filtro.status(),
                paginacao);
    }

    @Transactional(readOnly = true)
    public Veiculo buscar(Long id) {
        return veiculos.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", id));
    }

    /** Busca por placa, aceitando qualquer grafia — a normalização é aplicada antes. */
    @Transactional(readOnly = true)
    public Veiculo buscarPorPlaca(String placa) {
        String normalizada = normalizarPlaca(placa);
        return veiculos.buscarPorPlaca(normalizada)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", normalizada));
    }

    @Transactional
    public Veiculo criar(DadosDoVeiculo dados) {
        String placa = normalizarPlaca(dados.placa());
        garantirPlacaDisponivel(placa, null);
        Veiculo veiculo = new Veiculo(
                placa, dados.modelo(), dados.categoria(), dados.combustivel(), resolverLocadora(dados.locadoraId()));
        aplicar(veiculo, dados);
        return veiculos.save(veiculo);
    }

    @Transactional
    public Veiculo atualizar(Long id, DadosDoVeiculo dados) {
        Veiculo veiculo = buscar(id);
        String placa = normalizarPlaca(dados.placa());
        garantirPlacaDisponivel(placa, id);
        aplicar(veiculo, dados);
        return veiculo;
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).excluir(Instant.now());
    }

    private void aplicar(Veiculo veiculo, DadosDoVeiculo dados) {
        veiculo.alterarIdentificacao(
                dados.placa(), dados.modelo(), dados.fabricante(), dados.anoFabricacao());
        veiculo.alterarClassificacao(
                dados.categoria(), dados.combustivel(), resolverLocadora(dados.locadoraId()), dados.grupoTarifario());
        veiculo.alterarCodigoInterno(dados.codigoInterno());
        veiculo.definirRastreador(dados.possuiRastreador(), dados.fornecedorRastreador());
        veiculo.definirAdesivo(dados.possuiAdesivo());
        veiculo.alterarStatus(dados.status() == null ? StatusVeiculo.DISPONIVEL : dados.status());
        veiculo.alterarObservacoes(dados.observacoes());
    }

    private Locadora resolverLocadora(Long locadoraId) {
        if (locadoraId == null) {
            throw new NegocioException(ErroCadastro.PLACA_INVALIDA, "Informe a locadora do veículo.");
        }
        return locadoras.buscarPorId(locadoraId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Locadora", locadoraId));
    }

    /** Converte falha de formato de placa em erro de negócio com código estável (RN-02). */
    private String normalizarPlaca(String bruta) {
        try {
            return Placa.normalizar(bruta);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException(ErroCadastro.PLACA_INVALIDA, ex.getMessage());
        }
    }

    private void garantirPlacaDisponivel(String placa, Long idAtual) {
        if (veiculos.existeOutroComPlaca(placa, idAtual)) {
            throw new NegocioException(
                    ErroCadastro.PLACA_DUPLICADA,
                    "A placa %s já está cadastrada em outro veículo.".formatted(Placa.formatar(placa)),
                    Map.of("placa", placa));
        }
    }

    /** Termo para os campos textuais, preservando hífens significativos como em "T-Cross". */
    private String termoDeBusca(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        return "%" + termo.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /**
     * Termo para a busca por placa: sem separadores e em caixa baixa.
     *
     * <p>Sem essa variante, procurar por {@code abc-1d23} não encontraria o veículo
     * gravado como {@code ABC1D23}, e uma busca parcial por {@code ABC} também falharia.
     */
    private String termoDePlaca(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        return "%" + termo.trim().replaceAll("[\\s.-]", "").toLowerCase(Locale.ROOT) + "%";
    }
}
