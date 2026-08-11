package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.TabelaPreco;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link TabelaPreco}. */
public interface TabelaPrecoRepository extends JpaRepository<TabelaPreco, Long> {

    @Query("select t from TabelaPreco t join fetch t.locadora where t.id = :id and t.excluidoEm is null")
    Optional<TabelaPreco> buscarPorId(@Param("id") Long id);

    /**
     * Vigência aplicável a uma competência (RN-14).
     *
     * <p>Busca pelo ano exato, e não pela tabela mais recente: reprocessar um fechamento
     * antigo tem de reproduzir os preços que valiam na época.
     */
    @Query("""
            select t from TabelaPreco t
            join fetch t.locadora
            where t.locadora.id = :locadoraId and t.anoVigencia = :ano and t.excluidoEm is null
            """)
    Optional<TabelaPreco> buscarVigencia(@Param("locadoraId") Long locadoraId, @Param("ano") int ano);

    @Query(value = """
            select t from TabelaPreco t
            join fetch t.locadora
            where t.excluidoEm is null
              and (:locadoraId is null or t.locadora.id = :locadoraId)
              and (:ano is null or t.anoVigencia = :ano)
            """,
            countQuery = """
            select count(t) from TabelaPreco t
            where t.excluidoEm is null
              and (:locadoraId is null or t.locadora.id = :locadoraId)
              and (:ano is null or t.anoVigencia = :ano)
            """)
    Page<TabelaPreco> pesquisar(
            @Param("locadoraId") Long locadoraId, @Param("ano") Integer ano, Pageable paginacao);

    @Query("""
            select count(t) > 0 from TabelaPreco t
            where t.locadora.id = :locadoraId and t.anoVigencia = :ano
              and t.excluidoEm is null and (:id is null or t.id <> :id)
            """)
    boolean existeOutraVigencia(
            @Param("locadoraId") Long locadoraId, @Param("ano") int ano, @Param("id") Long id);
}
