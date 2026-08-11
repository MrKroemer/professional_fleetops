package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link Locadora}. */
public interface LocadoraRepository extends JpaRepository<Locadora, Long> {

    @Query("select l from Locadora l where l.id = :id and l.excluidoEm is null")
    Optional<Locadora> buscarPorId(@Param("id") Long id);

    @Query("""
            select l from Locadora l
            where l.excluidoEm is null
              and (:termo is null or lower(l.nome) like :termo or lower(l.consultor) like :termo)
              and (:tipo is null or l.tipo = :tipo)
              and (:ativa is null or l.ativa = :ativa)
            """)
    Page<Locadora> pesquisar(
            @Param("termo") String termo,
            @Param("tipo") TipoLocadora tipo,
            @Param("ativa") Boolean ativa,
            Pageable paginacao);

    @Query("""
            select count(l) > 0 from Locadora l
            where lower(l.nome) = lower(:nome) and l.excluidoEm is null and (:id is null or l.id <> :id)
            """)
    boolean existeOutraComNome(@Param("nome") String nome, @Param("id") Long id);
}
