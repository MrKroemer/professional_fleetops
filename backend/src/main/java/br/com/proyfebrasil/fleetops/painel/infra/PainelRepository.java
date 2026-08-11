package br.com.proyfebrasil.fleetops.painel.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de leitura do painel.
 *
 * <p>São agregações, não carregamento de entidades: contar 270 veículos por categoria em
 * SQL é uma consulta; carregá-los para contar em memória seriam 270 objetos descartados
 * logo em seguida. Por isso o repositório devolve projeções, e estende
 * {@link Repository} em vez de {@code JpaRepository} — não há aqui nenhuma operação de
 * escrita a expor.
 */
public interface PainelRepository extends Repository<Veiculo, Long> {

    /** Um par rótulo/quantidade, usado pelos gráficos de distribuição. */
    interface Contagem {
        String getRotulo();

        long getQuantidade();
    }

    /** Custo mensal estimado por locadora, derivado da tabela de preços vigente. */
    interface CustoPorLocadora {
        String getLocadora();

        long getVeiculos();

        BigDecimal getCustoMensal();
    }

    @Query("select count(v) from Veiculo v where v.excluidoEm is null")
    long totalDeVeiculos();

    @Query("""
            select count(v) from Veiculo v
            where v.excluidoEm is null
              and v.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo.EM_USO
            """)
    long veiculosEmUso();

    @Query("""
            select count(v) from Veiculo v
            where v.excluidoEm is null and v.combustivel =
              br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel.DIESEL
            """)
    long veiculosADiesel();

    @Query("select count(v) from Veiculo v where v.excluidoEm is null and v.possuiRastreador = true")
    long veiculosComRastreador();

    @Query("""
            select cast(v.categoria as string) as rotulo, count(v) as quantidade
            from Veiculo v where v.excluidoEm is null
            group by v.categoria order by count(v) desc
            """)
    List<Contagem> veiculosPorCategoria();

    @Query("""
            select v.locadora.nome as rotulo, count(v) as quantidade
            from Veiculo v where v.excluidoEm is null
            group by v.locadora.nome order by count(v) desc
            """)
    List<Contagem> veiculosPorLocadora();

    @Query("""
            select cast(v.status as string) as rotulo, count(v) as quantidade
            from Veiculo v where v.excluidoEm is null
            group by v.status order by count(v) desc
            """)
    List<Contagem> veiculosPorStatus();

    @Query("select count(o) from Obra o where o.excluidoEm is null")
    long totalDeObras();

    @Query("""
            select count(o) from Obra o
            where o.excluidoEm is null
              and o.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra.ATIVA
            """)
    long obrasAtivas();

    @Query("""
            select o.uf as rotulo, count(o) as quantidade
            from Obra o where o.excluidoEm is null
            group by o.uf order by count(o) desc
            """)
    List<Contagem> obrasPorUf();

    @Query("select count(c) from Condutor c where c.excluidoEm is null")
    long totalDeCondutores();

    @Query("""
            select count(c) from Condutor c
            where c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor.ATIVO
            """)
    long condutoresAtivos();

    @Query("select count(f) from Fornecedor f where f.excluidoEm is null and f.ativo = true")
    long fornecedoresAtivos();

    @Query("""
            select cast(f.tipo as string) as rotulo, count(f) as quantidade
            from Fornecedor f where f.excluidoEm is null and f.ativo = true
            group by f.tipo order by count(f) desc
            """)
    List<Contagem> fornecedoresPorTipo();

    /**
     * Custo mensal estimado da frota, por locadora.
     *
     * <p>Cruza cada veículo com o pacote <strong>mais barato</strong> do seu grupo
     * tarifário na vigência do ano informado. É uma estimativa de referência, não uma
     * fatura: o valor real depende do pacote efetivamente contratado em cada contrato,
     * que só existe a partir da Fase 2. A interface rotula o número como estimativa.
     */
    @Query("""
            select v.locadora.nome as locadora,
                   count(v) as veiculos,
                   sum((select min(p.valorMensal) from PrecoPacoteKm p
                        where p.grupoTarifario = g)) as custoMensal
            from Veiculo v
            join GrupoTarifario g on upper(g.codigo) = upper(v.grupoTarifario)
            join g.tabelaPreco t
            where v.excluidoEm is null
              and t.excluidoEm is null
              and t.anoVigencia = :ano
              and t.locadora = v.locadora
            group by v.locadora.nome
            order by count(v) desc
            """)
    List<CustoPorLocadora> custoMensalEstimadoPorLocadora(@Param("ano") int ano);
}
