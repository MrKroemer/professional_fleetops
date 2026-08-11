package br.com.proyfebrasil.fleetops.administracao.infra;

import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link Usuario}. Toda consulta ignora registros excluídos logicamente. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u where u.email = :email and u.excluidoEm is null")
    Optional<Usuario> buscarPorEmail(@Param("email") String email);

    @Query("select u from Usuario u where u.id = :id and u.excluidoEm is null")
    Optional<Usuario> buscarPorId(@Param("id") Long id);

    @Query("""
            select u from Usuario u
            where u.excluidoEm is null
              and (:termo is null or lower(u.nome) like :termo or lower(u.email) like :termo)
              and (:perfil is null or u.perfil = :perfil)
              and (:ativo is null or u.ativo = :ativo)
            """)
    Page<Usuario> pesquisar(
            @Param("termo") String termo,
            @Param("perfil") Perfil perfil,
            @Param("ativo") Boolean ativo,
            Pageable paginacao);

    @Query("select count(u) > 0 from Usuario u where u.email = :email and u.excluidoEm is null and (:id is null or u.id <> :id)")
    boolean existeOutroComEmail(@Param("email") String email, @Param("id") Long id);
}
