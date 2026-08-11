package br.com.proyfebrasil.fleetops.contratos.api;

import br.com.proyfebrasil.fleetops.cadastros.domain.Placa;
import br.com.proyfebrasil.fleetops.conformidade.application.ServicoDeFumacaPreta.SituacaoDaFumaca;
import br.com.proyfebrasil.fleetops.conformidade.domain.TesteDeFumacaPreta;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.ContratoResumoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.EventoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.FotoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.ItemPendenteResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.SituacaoDaFumacaResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.TesteDeFumacaResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.VerificacaoDeDevolucaoResponse;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDoCicloDeVida.VerificacaoDeDevolucao;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.EventoDeContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.FotoDoBook;
import br.com.proyfebrasil.fleetops.contratos.domain.ItemDoBook;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Conversão das entidades do ciclo de vida para DTOs de resposta.
 *
 * <p>Escrito à mão, e não gerado por MapStruct, por um motivo concreto: o
 * {@link EventoResponse} não é a projeção de uma entidade, e sim a soma de uma entidade
 * com uma <em>regra</em> — os itens faltantes da RN-12, que o domínio calcula. MapStruct
 * geraria a parte trivial e deixaria a interessante em métodos {@code default}, de modo
 * que a maior parte deste arquivo existiria de qualquer jeito, só que espalhada entre
 * código gerado e código escrito.
 *
 * <p>Os cadastros continuam com MapStruct, onde o mapeamento é de fato campo a campo.
 */
@Component
public class CicloDeVidaMapper {

    public ContratoResumoResponse paraResumo(ContratoDeLocacao contrato) {
        return new ContratoResumoResponse(
                contrato.getId(),
                contrato.getCodigoInterno(),
                contrato.getObra().getCodigo(),
                contrato.getObra().getNome(),
                contrato.getLocadora().getNome(),
                contrato.getVeiculoAtual() == null ? null : Placa.formatar(contrato.getVeiculoAtual().getPlaca()),
                contrato.getVeiculoAtual() == null ? null : contrato.getVeiculoAtual().getModelo(),
                contrato.getCondutorAtual() == null ? null : contrato.getCondutorAtual().getNome(),
                contrato.getStatus(),
                contrato.getStatus().getDescricao(),
                contrato.getDataRetirada(),
                contrato.getDataEncerramento(),
                contrato.getPacoteKmContratado(),
                contrato.quantidadeDeSubstituicoes(),
                contrato.quantidadeDeTrocasDeCondutor());
    }

    public EventoResponse paraResposta(EventoDeContrato evento) {
        return new EventoResponse(
                evento.getId(),
                evento.getContrato().getId(),
                evento.getTipo().name(),
                evento.getTipo().getDescricao(),
                evento.getSituacao().name(),
                evento.getSituacao().getDescricao(),
                evento.getDataDoEvento(),
                evento.getVeiculo().getId(),
                Placa.formatar(evento.getVeiculo().getPlaca()),
                evento.getVeiculo().getModelo(),
                evento.getCondutor().map(condutor -> condutor.getNome()).orElse(null),
                evento.getKm(),
                evento.getLocalDoEvento(),
                evento.getChecklistDaLocadora(),
                evento.getRegrasAceitasEm(),
                evento.getConcluidoEm(),
                evento.getObservacoes(),
                evento.getCrlv().map(anexo -> anexo.getId()).orElse(null),
                evento.getCrlv().map(anexo -> anexo.getNomeOriginal()).orElse(null),
                evento.getFotos().stream().map(this::paraResposta).toList(),
                evento.itensFaltantesDoBook().stream().map(CicloDeVidaMapper::paraItemPendente).toList(),
                evento.estaCompleto());
    }

    public FotoResponse paraResposta(FotoDoBook foto) {
        return new FotoResponse(
                foto.getId(),
                foto.getItem().name(),
                foto.getItem().getDescricao(),
                foto.getAnexo().getId(),
                foto.getAnexo().getNomeOriginal(),
                foto.getObservacao());
    }

    public TesteDeFumacaResponse paraResposta(TesteDeFumacaPreta teste) {
        return new TesteDeFumacaResponse(
                teste.getId(),
                teste.getVeiculo().getId(),
                Placa.formatar(teste.getVeiculo().getPlaca()),
                teste.getDataDoTeste(),
                teste.getPadrao().getCodigo(),
                teste.getPadrao().getDescricao(),
                teste.getPadrao().getOpacidadePercentual(),
                teste.getAltitudeEmMetros(),
                teste.isConforme(),
                teste.justificativa(),
                teste.getObservacoes());
    }

    public SituacaoDaFumacaResponse paraResposta(SituacaoDaFumaca situacao) {
        return new SituacaoDaFumacaResponse(
                situacao.exigido(),
                situacao.liberado(),
                situacao.pendente(),
                situacao.reprovado(),
                situacao.teste().map(this::paraResposta).orElse(null));
    }

    public VerificacaoDeDevolucaoResponse paraResposta(VerificacaoDeDevolucao verificacao) {
        return new VerificacaoDeDevolucaoResponse(
                verificacao.liberaDevolucao(),
                verificacao.eventoDeDevolucaoConcluido(),
                verificacao.avariasAbertas(),
                verificacao.fechamentoDoPeriodoFinal(),
                verificacao.pendencias());
    }

    /** Todos os ângulos do book, para a tela montar a grade mesmo antes da primeira foto. */
    public List<ItemPendenteResponse> catalogoDoBook() {
        return List.of(ItemDoBook.values()).stream().map(CicloDeVidaMapper::paraItemPendente).toList();
    }

    private static ItemPendenteResponse paraItemPendente(ItemDoBook item) {
        return new ItemPendenteResponse(item.name(), item.getDescricao());
    }
}
