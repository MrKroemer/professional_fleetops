package br.com.proyfebrasil.fleetops.painel.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas analíticas do painel — cruzamentos entre dois ou mais eixos.
 *
 * <p>Separadas de {@link PainelRepository}, que responde a contagens de um eixo só.
 * Aqui cada consulta responde a uma pergunta de decisão: qual locadora é mais barata
 * em cada faixa de quilometragem, como a frota se distribui entre categoria e
 * parceira, quanto os preços subiram de um ano para o outro.
 */
public interface AnaliseRepository extends Repository<Veiculo, Long> {

    /** Uma célula do cruzamento categoria × locadora. */
    interface CelulaDaFrota {
        String getCategoria();

        String getLocadora();

        long getQuantidade();
    }

    /** Um ponto da curva de preço: quanto custa, em média, um pacote naquela locadora. */
    interface PontoDePreco {
        String getLocadora();

        int getPacoteKm();

        BigDecimal getValorMedio();
    }

    /** Valor mensal de um grupo em um pacote, usado para comparar vigências. */
    interface ValorDoGrupo {
        String getLocadora();

        String getGrupo();

        String getCategoria();

        int getPacoteKm();

        BigDecimal getValorMensal();
    }

    /**
     * Frota cruzada por categoria e locadora.
     *
     * <p>Responde a "a concentração em uma parceira é igual em todas as categorias?" —
     * uma dependência de fornecedor único em 4x4, por exemplo, é risco operacional que
     * nenhum dos dois eixos isolados revelaria.
     */
    @Query("""
            select cast(v.categoria as string) as categoria,
                   v.locadora.nome as locadora,
                   count(v) as quantidade
            from Veiculo v
            where v.excluidoEm is null
              and v.status <> br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo.DEVOLVIDO
            group by v.categoria, v.locadora.nome
            order by v.categoria, count(v) desc
            """)
    List<CelulaDaFrota> frotaPorCategoriaELocadora();

    /**
     * Curva de preço médio por pacote de quilometragem, em cada locadora.
     *
     * <p>É a comparação que a planilha não permite fazer de relance: as grades da Unidas
     * e da Localiza ficam lado a lado, com pacotes diferentes, e ninguém consegue ver
     * em que faixa cada uma passa a ser mais cara.
     */
    @Query("""
            select t.locadora.nome as locadora,
                   p.pacoteKm as pacoteKm,
                   avg(p.valorMensal) as valorMedio
            from PrecoPacoteKm p
            join p.grupoTarifario g
            join g.tabelaPreco t
            where t.excluidoEm is null and t.anoVigencia = :ano
            group by t.locadora.nome, p.pacoteKm
            order by t.locadora.nome, p.pacoteKm
            """)
    List<PontoDePreco> curvaDePrecoPorPacote(@Param("ano") int ano);

    /** Valores de todos os grupos em um ano, para comparar duas vigências. */
    @Query("""
            select t.locadora.nome as locadora,
                   g.codigo as grupo,
                   cast(g.categoria as string) as categoria,
                   p.pacoteKm as pacoteKm,
                   p.valorMensal as valorMensal
            from PrecoPacoteKm p
            join p.grupoTarifario g
            join g.tabelaPreco t
            where t.excluidoEm is null and t.anoVigencia = :ano
            """)
    List<ValorDoGrupo> valoresDaVigencia(@Param("ano") int ano);

    /** Anos de vigência cadastrados, do mais recente para o mais antigo. */
    @Query("""
            select distinct t.anoVigencia from TabelaPreco t
            where t.excluidoEm is null order by t.anoVigencia desc
            """)
    List<Integer> anosComVigencia();
}
