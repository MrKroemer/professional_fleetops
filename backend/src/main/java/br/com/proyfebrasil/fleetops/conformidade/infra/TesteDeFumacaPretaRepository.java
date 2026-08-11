package br.com.proyfebrasil.fleetops.conformidade.infra;

import br.com.proyfebrasil.fleetops.conformidade.domain.TesteDeFumacaPreta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos testes de fumaça preta (FOR.MA.01). */
public interface TesteDeFumacaPretaRepository extends JpaRepository<TesteDeFumacaPreta, Long> {

    @Query("select t from TesteDeFumacaPreta t where t.id = :id and t.excluidoEm is null")
    Optional<TesteDeFumacaPreta> buscarPorId(@Param("id") Long id);

    /**
     * Teste mais recente de um veículo.
     *
     * <p>É o que a RN-09 consulta na retirada: vale o último resultado, não o melhor —
     * um veículo aprovado em janeiro e reprovado em março está reprovado.
     */
    @Query("""
            select t from TesteDeFumacaPreta t
            where t.veiculo.id = :veiculoId and t.excluidoEm is null
            order by t.dataDoTeste desc, t.id desc
            limit 1
            """)
    Optional<TesteDeFumacaPreta> maisRecenteDoVeiculo(@Param("veiculoId") Long veiculoId);

    @Query("""
            select t from TesteDeFumacaPreta t
            join fetch t.veiculo
            where t.veiculo.id = :veiculoId and t.excluidoEm is null
            order by t.dataDoTeste desc, t.id desc
            """)
    List<TesteDeFumacaPreta> historicoDoVeiculo(@Param("veiculoId") Long veiculoId);
}
