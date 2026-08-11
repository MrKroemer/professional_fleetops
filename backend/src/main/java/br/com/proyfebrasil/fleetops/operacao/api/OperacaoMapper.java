package br.com.proyfebrasil.fleetops.operacao.api;

import br.com.proyfebrasil.fleetops.cadastros.domain.Placa;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.AbastecimentoResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.ConformidadeResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.FaturaResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.FechamentoResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.RegistroDeKmResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.ServicoResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.UsoParticularResponse;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFechamentoMensal.Fechamento;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.Conformidade;
import br.com.proyfebrasil.fleetops.operacao.domain.Abastecimento;
import br.com.proyfebrasil.fleetops.operacao.domain.FaturaDaLocadora;
import br.com.proyfebrasil.fleetops.operacao.domain.RegistroDeKm;
import br.com.proyfebrasil.fleetops.operacao.domain.ServicoOperacional;
import br.com.proyfebrasil.fleetops.operacao.domain.UsoParticular;
import org.springframework.stereotype.Component;

/**
 * Conversão das entidades da operação para DTOs.
 *
 * <p>Escrito à mão pelo mesmo motivo do ciclo de vida: o fechamento não é projeção de
 * entidade nenhuma — é o resultado de um cálculo — e os lançamentos carregam valores
 * derivados (preço por litro, KM excedido) que MapStruct deixaria em métodos
 * {@code default} de qualquer forma.
 */
@Component
public class OperacaoMapper {

    public RegistroDeKmResponse paraResposta(RegistroDeKm registro) {
        return new RegistroDeKmResponse(
                registro.getId(),
                registro.getData(),
                registro.getKmInicial(),
                registro.getKmFinal(),
                registro.kmPercorrido(),
                registro.getCondutor().map(condutor -> condutor.getNome()).orElse(null),
                registro.getOrigem(),
                registro.getDestino(),
                registro.getObservacao());
    }

    public AbastecimentoResponse paraResposta(Abastecimento abastecimento) {
        return new AbastecimentoResponse(
                abastecimento.getId(),
                abastecimento.getData(),
                abastecimento.getValor(),
                abastecimento.isNaoConforme(),
                abastecimento.getPosto().map(posto -> posto.getNome()).orElse(null),
                abastecimento.getLitros(),
                abastecimento.precoPorLitro().orElse(null),
                abastecimento.getKm(),
                abastecimento.getJustificativa(),
                abastecimento.getObservacao());
    }

    public ServicoResponse paraResposta(ServicoOperacional servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getTipo().name(),
                servico.getTipo().getDescricao(),
                servico.getData(),
                servico.getValor(),
                servico.isNaoConforme(),
                servico.getFornecedor().map(fornecedor -> fornecedor.getNome()).orElse(null),
                servico.getDescricao(),
                servico.getJustificativa());
    }

    public ConformidadeResponse paraResposta(Conformidade conformidade) {
        return new ConformidadeResponse(conformidade.conforme(), conformidade.motivos());
    }

    public FechamentoResponse paraResposta(Fechamento fechamento) {
        return new FechamentoResponse(
                fechamento.contratoId(),
                fechamento.competencia().toString(),
                fechamento.placa() == null ? null : Placa.formatar(fechamento.placa()),
                fechamento.obra(),
                fechamento.kmInicial(),
                fechamento.kmFinal(),
                fechamento.kmPercorrido(),
                fechamento.pacoteContratado(),
                fechamento.kmExcedente(),
                fechamento.valorDoKmExcedente(),
                fechamento.custoDoExcedente(),
                fechamento.vigenciaIndisponivel(),
                fechamento.consumoTotal(),
                fechamento.quantidadeDeAbastecimentos(),
                fechamento.custoDeLavaJato(),
                fechamento.custoDeBorracharia(),
                fechamento.custoDeParaBrisas(),
                fechamento.custoTotal(),
                fechamento.lancamentosNaoConformes(),
                fechamento.estourouOPacote(),
                fechamento.status().name(),
                fechamento.observacoes());
    }

    public FaturaResponse paraResposta(FaturaDaLocadora fatura) {
        var contrato = fatura.getContrato();
        return new FaturaResponse(
                fatura.getId(),
                contrato.getId(),
                fatura.getCompetencia().toString(),
                fatura.getValorContratado(),
                fatura.getValorFaturado(),
                fatura.getExtrasAprovados(),
                fatura.getDivergencia(),
                fatura.getStatus().name(),
                fatura.getStatus().getDescricao(),
                fatura.exigeTratativa(),
                fatura.getNumeroDaNota(),
                fatura.getVencimento(),
                fatura.getObservacoes(),
                contrato.getObra().getNome(),
                contrato.getVeiculoAtual() == null ? null : Placa.formatar(contrato.getVeiculoAtual().getPlaca()));
    }

    public UsoParticularResponse paraResposta(UsoParticular uso) {
        return new UsoParticularResponse(
                uso.getId(),
                uso.getCondutor().getNome(),
                uso.getTipo().name(),
                uso.getTipo().getDescricao(),
                uso.getInicio(),
                uso.getFim(),
                uso.getKmAutorizado(),
                uso.getKmPercorrido(),
                uso.kmExcedido(),
                uso.estaValida(),
                uso.getAceiteEm(),
                uso.getObservacoes());
    }
}
