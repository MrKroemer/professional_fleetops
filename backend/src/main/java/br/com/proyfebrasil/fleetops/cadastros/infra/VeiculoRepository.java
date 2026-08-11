package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link Veiculo}. */
public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    @Query("select v from Veiculo v join fetch v.locadora where v.id = :id and v.excluidoEm is null")
    Optional<Veiculo> buscarPorId(@Param("id") Long id);

    @Query("select v from Veiculo v join fetch v.locadora where v.placa = :placa and v.excluidoEm is null")
    Optional<Veiculo> buscarPorPlaca(@Param("placa") String placa);

    @Query(value = """
            select v from Veiculo v
            join fetch v.locadora
            where v.excluidoEm is null
              and (:termo is null or lower(v.placa) like :termoPlaca or lower(v.modelo) like :termo
                   or lower(v.fabricante) like :termo or lower(v.codigoInterno) like :termo)
              and (:locadoraId is null or v.locadora.id = :locadoraId)
              and (:categoria is null or v.categoria = :categoria)
              and (:status is null or v.status = :status)
            """)
    Page<Veiculo> pesquisar(
            @Param("termo") String termo,
            @Param("termoPlaca") String termoPlaca,
            @Param("locadoraId") Long locadoraId,
            @Param("categoria") CategoriaVeiculo categoria,
            @Param("status") StatusVeiculo status,
            Pageable paginacao);

    /** Unicidade da placa entre veículos não excluídos (RN-02). */
    @Query("""
            select count(v) > 0 from Veiculo v
            where v.placa = :placa and v.excluidoEm is null and (:id is null or v.id <> :id)
            """)
    boolean existeOutroComPlaca(@Param("placa") String placa, @Param("id") Long id);
}
