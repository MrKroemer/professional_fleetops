package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link Obra}. Toda consulta ignora registros excluídos logicamente. */
public interface ObraRepository extends JpaRepository<Obra, Long> {

    @Query("select o from Obra o where o.id = :id and o.excluidoEm is null")
    Optional<Obra> buscarPorId(@Param("id") Long id);

    @Query("select o from Obra o where o.codigo = :codigo and o.excluidoEm is null")
    Optional<Obra> buscarPorCodigo(@Param("codigo") String codigo);

    @Query("""
            select o from Obra o
            where o.excluidoEm is null
              and (:termo is null or lower(o.nome) like :termo or lower(o.codigo) like :termo
                   or lower(o.cliente) like :termo or lower(o.cidade) like :termo)
              and (:status is null or o.status = :status)
              and (:uf is null or o.uf = :uf)
            """)
    Page<Obra> pesquisar(
            @Param("termo") String termo,
            @Param("status") StatusObra status,
            @Param("uf") String uf,
            Pageable paginacao);

    @Query("""
            select count(o) > 0 from Obra o
            where o.codigo = :codigo and o.excluidoEm is null and (:id is null or o.id <> :id)
            """)
    boolean existeOutraComCodigo(@Param("codigo") String codigo, @Param("id") Long id);
}
