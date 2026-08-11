package br.com.proyfebrasil.fleetops.painel.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas que apuram pendências a partir dos cadastros (RN-23).
 *
 * <p>Cada método responde a uma pergunta operacional concreta — "que obra ativa não tem
 * posto credenciado?" — e devolve só o necessário para montar o item da central. São
 * lacunas reais da base, não avisos genéricos.
 */
public interface PendenciaRepository extends Repository<Obra, Long> {

    /** Registro afetado por uma pendência, com o rótulo que a interface exibe. */
    interface Afetado {
        Long getId();

        String getRotulo();

        String getComplemento();
    }

    /**
     * Obras ativas sem fornecedor credenciado de um tipo.
     *
     * <p>Uma obra sem posto obriga o condutor a abastecer fora da rede, o que a RN-04
     * classifica como não conformidade — a pendência antecipa esse problema.
     */
    @Query("""
            select o.id as id, o.nome as rotulo, o.codigo as complemento
            from Obra o
            where o.excluidoEm is null
              and o.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra.ATIVA
              and not exists (
                select 1 from Fornecedor f join f.obras fo
                where fo.id = o.id and f.ativo = true and f.excluidoEm is null
                  and f.tipo = :tipo)
            order by o.codigo
            """)
    List<Afetado> obrasAtivasSemFornecedorDoTipo(
            @Param("tipo") br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor tipo);

    /** Condutores ativos com CNH vencida na data de referência (RN-16). */
    @Query("""
            select c.id as id, c.nome as rotulo, cast(c.cnhValidade as string) as complemento
            from Condutor c
            where c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor.ATIVO
              and c.cnhValidade is not null and c.cnhValidade < :referencia
            order by c.cnhValidade
            """)
    List<Afetado> condutoresComCnhVencida(@Param("referencia") LocalDate referencia);

    /** Condutores ativos cuja CNH vence dentro da faixa de alerta (RN-16). */
    @Query("""
            select c.id as id, c.nome as rotulo, cast(c.cnhValidade as string) as complemento
            from Condutor c
            where c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor.ATIVO
              and c.cnhValidade between :referencia and :limite
            order by c.cnhValidade
            """)
    List<Afetado> condutoresComCnhVencendo(
            @Param("referencia") LocalDate referencia, @Param("limite") LocalDate limite);

    /**
     * Locadoras ativas sem tabela de preços para o ano (RN-14).
     *
     * <p>Sem vigência não há como calcular KM excedente nem conferir fatura, então a
     * ausência é bloqueio para a operação mensal — não um detalhe de cadastro.
     */
    @Query("""
            select l.id as id, l.nome as rotulo, cast(:ano as string) as complemento
            from Locadora l
            where l.excluidoEm is null and l.ativa = true
              and exists (select 1 from Veiculo v where v.locadora = l and v.excluidoEm is null)
              and not exists (
                select 1 from TabelaPreco t
                where t.locadora = l and t.anoVigencia = :ano and t.excluidoEm is null)
            order by l.nome
            """)
    List<Afetado> locadorasSemVigencia(@Param("ano") int ano);

    /** Veículos sem grupo tarifário: o fechamento não saberá qual preço aplicar (RN-06). */
    @Query("""
            select v.id as id, v.placa as rotulo, v.modelo as complemento
            from Veiculo v
            where v.excluidoEm is null
              and v.status <> br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo.DEVOLVIDO
              and (v.grupoTarifario is null or v.grupoTarifario = '')
            order by v.placa
            """)
    List<Afetado> veiculosSemGrupoTarifario();

    /**
     * Veículos cujos períodos se sobrepõem entre contratos diferentes.
     *
     * <p>Um carro não está em duas obras ao mesmo tempo. Quando aparece assim, ou a data
     * de uma substituição está errada ou uma devolução não foi registrada — nos dois
     * casos, é o gestor quem precisa decidir qual das duas versões vale.
     *
     * <p>A comparação usa {@code daterange} com limites inclusivos e trata {@code fim}
     * nulo como período em curso, do mesmo modo que a restrição de exclusão da tabela.
     * O {@code <} entre identificadores evita listar o mesmo par duas vezes.
     */
    @Query(value = """
            select distinct v.id as id,
                   v.placa as rotulo,
                   concat('contratos ', a.contrato_id, ' e ', b.contrato_id) as complemento
            from substituicao_veiculo a
            join substituicao_veiculo b
              on a.veiculo_id = b.veiculo_id
             and a.contrato_id < b.contrato_id
             and daterange(a.inicio, a.fim, '[]') && daterange(b.inicio, b.fim, '[]')
            join veiculo v on v.id = a.veiculo_id
            where v.deleted_at is null
            order by v.placa
            """, nativeQuery = true)
    List<Afetado> veiculosEmContratosSobrepostos();

    /** Locadoras com portal cadastrado mas sem credencial guardada (RN-20). */
    @Query("""
            select l.id as id, l.nome as rotulo, l.portalUrl as complemento
            from Locadora l
            where l.excluidoEm is null and l.ativa = true
              and l.portalUrl is not null and l.portalUrl <> ''
              and l.portalSenhaCifrada is null
            order by l.nome
            """)
    List<Afetado> locadorasComPortalSemCredencial();
}
