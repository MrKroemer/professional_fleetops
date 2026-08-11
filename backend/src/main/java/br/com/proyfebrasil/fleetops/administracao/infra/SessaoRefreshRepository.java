package br.com.proyfebrasil.fleetops.administracao.infra;

import br.com.proyfebrasil.fleetops.administracao.domain.SessaoRefresh;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às sessões de refresh, base da revogação de tokens. */
public interface SessaoRefreshRepository extends JpaRepository<SessaoRefresh, Long> {

    Optional<SessaoRefresh> findByJti(UUID jti);

    /** Revoga todas as sessões abertas de um usuário — usado no logout global e na desativação. */
    @Modifying
    @Query("""
            update SessaoRefresh s set s.revogadoEm = :momento
            where s.usuario.id = :usuarioId and s.revogadoEm is null
            """)
    int revogarSessoesDoUsuario(@Param("usuarioId") Long usuarioId, @Param("momento") Instant momento);

    /** Remove sessões já expiradas; executado pelo expurgo agendado. */
    @Modifying
    @Query("delete from SessaoRefresh s where s.expiraEm < :limite")
    int expurgarExpiradasAntesDe(@Param("limite") Instant limite);
}
