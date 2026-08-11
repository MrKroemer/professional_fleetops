package br.com.proyfebrasil.fleetops.painel.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository.CelulaDaFrota;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository.PontoDePreco;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository.ValorDoGrupo;
import br.com.proyfebrasil.fleetops.shared.money.Dinheiro;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Análises cruzadas do painel.
 *
 * <p>Cada uma responde a uma pergunta que nenhum eixo isolado responde. Todas derivam
 * dos cadastros: não há projeção, extrapolação nem série sintética.
 */
@Service
public class ServicoDeAnalises {

    /**
     * Máximo de séries categóricas por gráfico.
     *
     * <p>Três é o teto validado da paleta: com quatro matizes, verde e magenta deixam
     * de ser distinguíveis sob deuteranopia no tema escuro. A quarta classe em diante
     * é dobrada em "Outros", que usa cinza — cinza não carrega identidade.
     */
    public static final int MAXIMO_DE_SERIES = 3;

    private final AnaliseRepository analises;

    public ServicoDeAnalises(AnaliseRepository analises) {
        this.analises = analises;
    }

    /** Uma linha do cruzamento categoria × locadora. */
    public record LinhaDaMatriz(String chave, String rotulo, long total, Map<String, Long> porLocadora) {
    }

    /** Cruzamento da frota entre categoria e locadora. */
    public record MatrizDaFrota(List<String> locadoras, List<LinhaDaMatriz> linhas) {
    }

    /** Um ponto da curva de preço. */
    public record PontoDaCurva(int pacoteKm, BigDecimal valorMedio) {
    }

    /** A curva de uma locadora ao longo dos pacotes de KM. */
    public record CurvaDeLocadora(String locadora, List<PontoDaCurva> pontos) {
    }

    /**
     * Variação de preço de um grupo entre duas vigências.
     *
     * @param variacaoPercentual variação em pontos percentuais, com duas casas
     */
    public record ReajusteDoGrupo(
            String locadora,
            String grupo,
            String categoria,
            String categoriaDescricao,
            int pacoteKm,
            BigDecimal valorAnterior,
            BigDecimal valorAtual,
            BigDecimal variacaoPercentual) {
    }

    /** Comparação de vigências, com os anos efetivamente usados. */
    public record ComparacaoDeVigencias(
            Integer anoAnterior, Integer anoAtual, List<ReajusteDoGrupo> reajustes, BigDecimal variacaoMedia) {
    }

    /**
     * Cruza a frota entre categoria e locadora.
     *
     * <p>As locadoras que não estão entre as maiores viram uma coluna "Outras": a
     * matriz é lida de relance, e uma coluna por parceira avulsa arruinaria isso.
     */
    @Transactional(readOnly = true)
    public MatrizDaFrota matrizDaFrota() {
        List<CelulaDaFrota> celulas = analises.frotaPorCategoriaELocadora();

        Map<String, Long> totalPorLocadora = new LinkedHashMap<>();
        for (CelulaDaFrota celula : celulas) {
            totalPorLocadora.merge(celula.getLocadora(), celula.getQuantidade(), Long::sum);
        }

        List<String> principais = totalPorLocadora.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAXIMO_DE_SERIES)
                .map(Map.Entry::getKey)
                .toList();
        boolean temCauda = totalPorLocadora.size() > principais.size();

        List<String> colunas = new ArrayList<>(principais);
        if (temCauda) {
            colunas.add("Outras");
        }

        Map<String, Map<String, Long>> porCategoria = new LinkedHashMap<>();
        Map<String, Long> totaisDaCategoria = new LinkedHashMap<>();
        for (CelulaDaFrota celula : celulas) {
            String coluna = principais.contains(celula.getLocadora()) ? celula.getLocadora() : "Outras";
            porCategoria
                    .computeIfAbsent(celula.getCategoria(), chave -> new LinkedHashMap<>())
                    .merge(coluna, celula.getQuantidade(), Long::sum);
            totaisDaCategoria.merge(celula.getCategoria(), celula.getQuantidade(), Long::sum);
        }

        List<LinhaDaMatriz> linhas = porCategoria.entrySet().stream()
                .map(entrada -> new LinhaDaMatriz(
                        entrada.getKey(),
                        CategoriaVeiculo.valueOf(entrada.getKey()).getDescricao(),
                        totaisDaCategoria.getOrDefault(entrada.getKey(), 0L),
                        entrada.getValue()))
                .sorted(Comparator.comparingLong(LinhaDaMatriz::total).reversed())
                .toList();

        return new MatrizDaFrota(colunas, linhas);
    }

    /**
     * Curva de preço médio por pacote de quilometragem, no ano informado.
     *
     * <p>Só locadoras com ao menos dois pontos entram: uma linha de um ponto só não é
     * uma curva, e desenhá-la sugeriria uma tendência que o dado não sustenta.
     */
    @Transactional(readOnly = true)
    public List<CurvaDeLocadora> curvasDePreco(int ano) {
        Map<String, List<PontoDaCurva>> porLocadora = new LinkedHashMap<>();
        for (PontoDePreco ponto : analises.curvaDePrecoPorPacote(ano)) {
            porLocadora
                    .computeIfAbsent(ponto.getLocadora(), chave -> new ArrayList<>())
                    .add(new PontoDaCurva(ponto.getPacoteKm(), Dinheiro.normalizar(ponto.getValorMedio())));
        }
        return porLocadora.entrySet().stream()
                .filter(entrada -> entrada.getValue().size() >= 2)
                .limit(MAXIMO_DE_SERIES)
                .map(entrada -> new CurvaDeLocadora(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    /**
     * Compara as duas vigências mais recentes, grupo a grupo.
     *
     * <p>É o número que ninguém consegue extrair da planilha sem conferir célula a
     * célula: quanto cada grupo tarifário subiu de um ano para o outro.
     */
    @Transactional(readOnly = true)
    public ComparacaoDeVigencias compararVigencias() {
        List<Integer> anos = new ArrayList<>(new TreeSet<>(analises.anosComVigencia())).reversed();
        if (anos.size() < 2) {
            return new ComparacaoDeVigencias(null, anos.isEmpty() ? null : anos.getFirst(), List.of(), null);
        }

        int anoAtual = anos.get(0);
        int anoAnterior = anos.get(1);

        Map<String, ValorDoGrupo> anteriores = new LinkedHashMap<>();
        for (ValorDoGrupo valor : analises.valoresDaVigencia(anoAnterior)) {
            anteriores.put(chaveDoGrupo(valor), valor);
        }

        List<ReajusteDoGrupo> reajustes = new ArrayList<>();
        for (ValorDoGrupo atual : analises.valoresDaVigencia(anoAtual)) {
            ValorDoGrupo anterior = anteriores.get(chaveDoGrupo(atual));
            if (anterior == null || anterior.getValorMensal().signum() == 0) {
                continue;
            }
            BigDecimal variacao = atual.getValorMensal()
                    .subtract(anterior.getValorMensal())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(anterior.getValorMensal(), 2, RoundingMode.HALF_UP);
            reajustes.add(new ReajusteDoGrupo(
                    atual.getLocadora(),
                    atual.getGrupo(),
                    atual.getCategoria(),
                    CategoriaVeiculo.valueOf(atual.getCategoria()).getDescricao(),
                    atual.getPacoteKm(),
                    Dinheiro.normalizar(anterior.getValorMensal()),
                    Dinheiro.normalizar(atual.getValorMensal()),
                    variacao));
        }

        reajustes.sort(Comparator.comparing(ReajusteDoGrupo::variacaoPercentual).reversed());

        BigDecimal media = reajustes.isEmpty()
                ? null
                : reajustes.stream()
                        .map(ReajusteDoGrupo::variacaoPercentual)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(reajustes.size()), 2, RoundingMode.HALF_UP);

        return new ComparacaoDeVigencias(anoAnterior, anoAtual, reajustes, media);
    }

    private static String chaveDoGrupo(ValorDoGrupo valor) {
        return "%s|%s|%d".formatted(valor.getLocadora(), valor.getGrupo(), valor.getPacoteKm());
    }
}
