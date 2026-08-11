package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.operacao.domain.UsoParticular;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às autorizações de uso particular. */
public interface UsoParticularRepository extends JpaRepository<UsoParticular, Long> {

    @Query("select u from UsoParticular u where u.id = :id and u.excluidoEm is null")
    Optional<UsoParticular> buscarPorId(@Param("id") Long id);

    @Query("""
            select u from UsoParticular u
            join fetch u.condutor
            where u.contrato.id = :contratoId and u.excluidoEm is null
            order by u.inicio desc
            """)
    List<UsoParticular> doContrato(@Param("contratoId") Long contratoId);

    /** Autorização que cobre a data — usada para apurar uma ocorrência já acontecida. */
    @Query("""
            select u from UsoParticular u
            join fetch u.condutor
            where u.contrato.id = :contratoId and u.excluidoEm is null
              and :data between u.inicio and u.fim
            """)
    Optional<UsoParticular> vigenteEm(
            @Param("contratoId") Long contratoId, @Param("data") LocalDate data);
}
