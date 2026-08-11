package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.operacao.domain.FechamentoMensal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso à conferência das competências. */
public interface FechamentoRepository extends JpaRepository<FechamentoMensal, Long> {

    @Query("""
            select f from FechamentoMensal f
            where f.contrato.id = :contratoId and f.competencia = :competencia and f.excluidoEm is null
            """)
    Optional<FechamentoMensal> daCompetencia(
            @Param("contratoId") Long contratoId, @Param("competencia") LocalDate competencia);
}
