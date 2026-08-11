package br.com.proyfebrasil.fleetops.contratos.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Placa;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.LinhaDoTempoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.MarcoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.SituacaoNaDataResponse;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.EventoDeContrato;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.contratos.infra.EventoDeContratoRepository;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A linha do tempo de um contrato — o que a Fase 2 pede como "linha do tempo visual".
 *
 * <p>Funde três histórias que hoje vivem em tabelas separadas: os períodos de veículo, os
 * períodos de condutor e os eventos de retirada e devolução. Fundi-las é o ponto: o gestor
 * pergunta "o que aconteceu com este contrato?", e não "quais foram as substituições de
 * veículo?". Três listas lado a lado obrigariam a cruzar datas com o dedo na tela.
 *
 * <p>A ordenação é decrescente por data. O interesse do dia a dia está no que aconteceu por
 * último; o começo do contrato está sempre no fim da lista, onde é fácil achar.
 */
@Service
public class ServicoDaLinhaDoTempo {

    private final ContratoRepository contratos;
    private final EventoDeContratoRepository eventos;

    public ServicoDaLinhaDoTempo(ContratoRepository contratos, EventoDeContratoRepository eventos) {
        this.contratos = contratos;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public LinhaDoTempoResponse montar(Long contratoId) {
        ContratoDeLocacao contrato = buscar(contratoId);
        // Com `open-in-view=false` as coleções precisam ser resolvidas aqui dentro; fora
        // desta transação a sessão já estará fechada e a leitura falharia.
        Hibernate.initialize(contrato.getSubstituicoes());
        Hibernate.initialize(contrato.getTrocasDeCondutor());

        List<MarcoResponse> marcos = new ArrayList<>();

        boolean primeiroVeiculo = true;
        for (var periodo : contrato.getSubstituicoes()) {
            marcos.add(new MarcoResponse(
                    primeiroVeiculo ? "VEICULO_INICIAL" : "SUBSTITUICAO_VEICULO",
                    primeiroVeiculo ? "Veículo da retirada" : "Substituição de veículo",
                    periodo.getInicio(),
                    periodo.getFim(),
                    Placa.formatar(periodo.getVeiculo().getPlaca()),
                    periodo.getVeiculo().getModelo(),
                    periodo.getMotivo(),
                    periodo.emCurso(),
                    periodo.getId()));
            primeiroVeiculo = false;
        }

        boolean primeiroCondutor = true;
        for (var periodo : contrato.getTrocasDeCondutor()) {
            marcos.add(new MarcoResponse(
                    primeiroCondutor ? "CONDUTOR_INICIAL" : "TROCA_CONDUTOR",
                    primeiroCondutor ? "Condutor da retirada" : "Troca de condutor",
                    periodo.getInicio(),
                    periodo.getFim(),
                    periodo.getCondutor().getNome(),
                    periodo.getCondutor().getCargo(),
                    periodo.getMotivo(),
                    periodo.emCurso(),
                    periodo.getId()));
            primeiroCondutor = false;
        }

        for (EventoDeContrato evento : eventos.doContrato(contratoId)) {
            marcos.add(new MarcoResponse(
                    "EVENTO_" + evento.getTipo().name(),
                    evento.getTipo().getDescricao(),
                    evento.getDataDoEvento(),
                    null,
                    Placa.formatar(evento.getVeiculo().getPlaca()),
                    evento.getSituacao().getDescricao(),
                    evento.getObservacoes(),
                    !evento.estaConcluido(),
                    evento.getId()));
        }

        // Empate de data resolvido pelo tipo, para que a retirada apareça junto do veículo
        // que ela entregou, e não espalhada no meio das trocas do mesmo dia.
        marcos.sort(Comparator.comparing(MarcoResponse::data).reversed()
                .thenComparing(MarcoResponse::tipo));

        return new LinhaDoTempoResponse(
                contrato.getId(),
                contrato.getCodigoInterno(),
                contrato.getStatus(),
                contrato.getStatus().getDescricao(),
                contrato.getDataRetirada(),
                contrato.getDataEncerramento(),
                contrato.getObra().getNome(),
                contrato.getLocadora().getNome(),
                contrato.getVeiculoAtual() == null ? null : Placa.formatar(contrato.getVeiculoAtual().getPlaca()),
                contrato.getCondutorAtual() == null ? null : contrato.getCondutorAtual().getNome(),
                contrato.quantidadeDeSubstituicoes(),
                contrato.quantidadeDeTrocasDeCondutor(),
                List.copyOf(marcos));
    }

    /**
     * Quem dirigia o quê numa data (RN-18).
     *
     * <p>A pergunta literal da especificação — "quem dirigia a placa X em 15/03?" — vira
     * um endereço que a tela pode consultar. Responde pelos históricos, nunca pelos
     * ponteiros do contrato, que descrevem apenas o hoje.
     */
    @Transactional(readOnly = true)
    public SituacaoNaDataResponse situacaoEm(Long contratoId, LocalDate data) {
        ContratoDeLocacao contrato = buscar(contratoId);
        Hibernate.initialize(contrato.getSubstituicoes());
        Hibernate.initialize(contrato.getTrocasDeCondutor());

        var veiculo = contrato.veiculoEm(data);
        var condutor = contrato.condutorEm(data);

        return new SituacaoNaDataResponse(
                data,
                veiculo.map(v -> Placa.formatar(v.getPlaca())).orElse(null),
                veiculo.map(v -> v.getModelo()).orElse(null),
                condutor.map(c -> c.getNome()).orElse(null),
                veiculo.isPresent() || condutor.isPresent());
    }

    private ContratoDeLocacao buscar(Long id) {
        return contratos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato de locação", id));
    }
}
