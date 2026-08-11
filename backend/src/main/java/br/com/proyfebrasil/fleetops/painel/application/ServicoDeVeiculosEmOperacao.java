package br.com.proyfebrasil.fleetops.painel.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Veículos em operação, com quem os dirige e onde.
 *
 * <p>É um modelo de leitura montado para os cards do painel: junta veículo, obra e
 * condutor em uma consulta só, já com a situação da CNH avaliada. Sem isso o card
 * mostraria apenas a placa — que é justamente o que o gestor menos precisa saber, já
 * que a pergunta dele é "quem está com este carro e em qual frente".
 */
@Service
public class ServicoDeVeiculosEmOperacao {

    private final ContratoRepository contratos;
    private final Clock relogio;

    public ServicoDeVeiculosEmOperacao(ContratoRepository contratos, Clock relogio) {
        this.contratos = contratos;
        this.relogio = relogio;
    }

    /**
     * Um veículo em operação e seu contexto.
     *
     * @param diasParaVencerCnh dias até o vencimento; negativo se vencida, nulo sem CNH
     */
    public record VeiculoEmOperacao(
            Long veiculoId,
            String placa,
            String placaFormatada,
            String modelo,
            String categoria,
            String categoriaDescricao,
            boolean exigeTesteFumacaPreta,
            boolean possuiRastreador,
            boolean possuiAdesivo,
            String locadora,
            String grupoTarifario,
            Long contratoId,
            String obraCodigo,
            String obraNome,
            String localRetirada,
            Integer pacoteKmContratado,
            LocalDate dataRetirada,
            Long condutorId,
            String condutorNome,
            String condutorCargo,
            LocalDate cnhValidade,
            Long diasParaVencerCnh,
            boolean cnhVencida,
            boolean cnhEmAlerta) {
    }

    @Transactional(readOnly = true)
    public List<VeiculoEmOperacao> listar() {
        LocalDate hoje = LocalDate.now(relogio);
        return contratos.ativosComVeiculo().stream().map(contrato -> montar(contrato, hoje)).toList();
    }

    private VeiculoEmOperacao montar(ContratoDeLocacao contrato, LocalDate hoje) {
        var veiculo = contrato.getVeiculoAtual();
        Condutor condutor = contrato.getCondutorAtual();

        return new VeiculoEmOperacao(
                veiculo.getId(),
                veiculo.getPlaca(),
                veiculo.getPlacaFormatada(),
                veiculo.getModelo(),
                veiculo.getCategoria().name(),
                veiculo.getCategoria().getDescricao(),
                veiculo.exigeTesteDeFumacaPreta(),
                veiculo.isPossuiRastreador(),
                veiculo.isPossuiAdesivo(),
                contrato.getLocadora().getNome(),
                veiculo.getGrupoTarifario(),
                contrato.getId(),
                contrato.getObra().getCodigo(),
                contrato.getObra().getNome(),
                contrato.getLocalRetirada(),
                contrato.getPacoteKmContratado(),
                contrato.getDataRetirada(),
                condutor == null ? null : condutor.getId(),
                condutor == null ? null : condutor.getNome(),
                condutor == null ? null : condutor.getCargo(),
                condutor == null ? null : condutor.getCnhValidade(),
                condutor == null ? null : condutor.diasParaVencimentoDaCnh(hoje),
                condutor != null && condutor.cnhVencidaEm(hoje),
                condutor != null && condutor.cnhEmAlertaEm(hoje));
    }
}
