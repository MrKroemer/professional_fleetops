package br.com.proyfebrasil.fleetops.painel.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.painel.infra.PainelRepository;
import br.com.proyfebrasil.fleetops.painel.infra.PainelRepository.Contagem;
import br.com.proyfebrasil.fleetops.shared.money.Dinheiro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores do painel inicial.
 *
 * <p>Todos derivam de agregações sobre os cadastros — nenhum número é armazenado nem
 * estimado por amostragem. Onde o valor é uma estimativa, o nome do campo diz isso
 * (`custoMensalEstimado`) e a interface repete o aviso: o custo real depende do pacote
 * contratado em cada contrato de locação, que passa a existir na Fase 2.
 */
@Service
public class ServicoDoPainel {

    private final PainelRepository painel;
    private final Clock relogio;

    public ServicoDoPainel(PainelRepository painel, Clock relogio) {
        this.painel = painel;
        this.relogio = relogio;
    }

    /** Uma fatia de distribuição, já com o rótulo em português. */
    public record Fatia(String chave, String rotulo, long quantidade) {
    }

    /** Custo mensal estimado de uma locadora. */
    public record CustoDaLocadora(String locadora, long veiculos, BigDecimal custoMensalEstimado) {
    }

    /** Conjunto completo de indicadores do painel. */
    public record Indicadores(
            long veiculosNaFrota,
            long veiculosEmUso,
            long veiculosADiesel,
            long veiculosComRastreador,
            long obras,
            long obrasAtivas,
            long condutores,
            long condutoresAtivos,
            long fornecedoresAtivos,
            int anoDeReferencia,
            BigDecimal custoMensalEstimado,
            List<Fatia> veiculosPorCategoria,
            List<Fatia> veiculosPorLocadora,
            List<Fatia> veiculosPorStatus,
            List<Fatia> obrasPorUf,
            List<Fatia> fornecedoresPorTipo,
            List<CustoDaLocadora> custoPorLocadora) {
    }

    @Transactional(readOnly = true)
    public Indicadores apurar() {
        int ano = LocalDate.now(relogio).getYear();

        List<CustoDaLocadora> custos = painel.custoMensalEstimadoPorLocadora(ano).stream()
                .map(linha -> new CustoDaLocadora(
                        linha.getLocadora(),
                        linha.getVeiculos(),
                        Dinheiro.normalizar(
                                linha.getCustoMensal() == null ? BigDecimal.ZERO : linha.getCustoMensal())))
                .toList();

        BigDecimal total = custos.stream()
                .map(CustoDaLocadora::custoMensalEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Indicadores(
                painel.totalDeVeiculos(),
                painel.veiculosEmUso(),
                painel.veiculosADiesel(),
                painel.veiculosComRastreador(),
                painel.totalDeObras(),
                painel.obrasAtivas(),
                painel.totalDeCondutores(),
                painel.condutoresAtivos(),
                painel.fornecedoresAtivos(),
                ano,
                Dinheiro.normalizar(total),
                traduzir(painel.veiculosPorCategoria(), chave -> CategoriaVeiculo.valueOf(chave).getDescricao()),
                traduzir(painel.veiculosPorLocadora(), Function.identity()),
                traduzir(painel.veiculosPorStatus(), chave -> StatusVeiculo.valueOf(chave).getDescricao()),
                traduzir(painel.obrasPorUf(), Function.identity()),
                traduzir(painel.fornecedoresPorTipo(), chave -> TipoFornecedor.valueOf(chave).getDescricao()),
                custos);
    }

    /**
     * Converte a chave técnica devolvida pelo banco no rótulo de exibição.
     *
     * <p>A chave original é preservada ao lado do rótulo porque a interface a usa para
     * escolher a cor da série no gráfico — cor por categoria precisa ser estável entre
     * consultas, e o rótulo traduzido não serve como identificador.
     */
    private static List<Fatia> traduzir(List<Contagem> contagens, Function<String, String> rotulador) {
        return contagens.stream()
                .map(contagem -> new Fatia(
                        contagem.getRotulo(), rotulador.apply(contagem.getRotulo()), contagem.getQuantidade()))
                .toList();
    }
}
