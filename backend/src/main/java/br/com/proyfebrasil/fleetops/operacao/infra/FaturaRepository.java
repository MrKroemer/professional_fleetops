package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.operacao.domain.FaturaDaLocadora;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às faturas mensais das locadoras. */
public interface FaturaRepository extends JpaRepository<FaturaDaLocadora, Long> {

    @Query("select f from FaturaDaLocadora f where f.id = :id and f.excluidoEm is null")
    Optional<FaturaDaLocadora> buscarPorId(@Param("id") Long id);

    @Query("""
            select f from FaturaDaLocadora f
            where f.contrato.id = :contratoId and f.competencia = :competencia and f.excluidoEm is null
            """)
    Optional<FaturaDaLocadora> daCompetencia(
            @Param("contratoId") Long contratoId, @Param("competencia") LocalDate competencia);

    @Query("""
            select f from FaturaDaLocadora f
            join fetch f.contrato c
            join fetch c.obra
            left join fetch c.veiculoAtual
            where c.id = :contratoId and f.excluidoEm is null
            order by f.competencia desc
            """)
    List<FaturaDaLocadora> doContrato(@Param("contratoId") Long contratoId);

    /** Faturas com divergência em aberto — o que a central de pendências mostra (RN-13). */
    @Query("""
            select f from FaturaDaLocadora f
            join fetch f.contrato c
            join fetch c.obra
            left join fetch c.veiculoAtual
            where f.excluidoEm is null and f.divergencia <> 0
              and f.status <> br.com.proyfebrasil.fleetops.operacao.domain.StatusDeConferencia.AJUSTADA
            order by abs(f.divergencia) desc
            """)
    List<FaturaDaLocadora> comDivergenciaEmAberto();
}
