package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.operacao.domain.Abastecimento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos abastecimentos. */
public interface AbastecimentoRepository extends JpaRepository<Abastecimento, Long> {

    @Query("select a from Abastecimento a where a.id = :id and a.excluidoEm is null")
    Optional<Abastecimento> buscarPorId(@Param("id") Long id);

    /** Existe outro abastecimento no mesmo dia? É o teto diário da RN-04. */
    @Query("""
            select count(a) > 0 from Abastecimento a
            where a.contrato.id = :contratoId and a.data = :data and a.excluidoEm is null
              and (:ignorar is null or a.id <> :ignorar)
            """)
    boolean existeNoDia(
            @Param("contratoId") Long contratoId,
            @Param("data") LocalDate data,
            @Param("ignorar") Long ignorar);

    @Query("""
            select a from Abastecimento a
            left join fetch a.posto
            where a.contrato.id = :contratoId and a.excluidoEm is null
              and a.data between :inicio and :fim
            order by a.data asc, a.id asc
            """)
    List<Abastecimento> doPeriodo(
            @Param("contratoId") Long contratoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
