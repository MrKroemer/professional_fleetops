package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link Condutor}. */
public interface CondutorRepository extends JpaRepository<Condutor, Long> {

    @Query("select c from Condutor c left join fetch c.obraAtual where c.id = :id and c.excluidoEm is null")
    Optional<Condutor> buscarPorId(@Param("id") Long id);

    @Query("select c from Condutor c where c.cpf = :cpf and c.excluidoEm is null")
    Optional<Condutor> buscarPorCpf(@Param("cpf") String cpf);

    /**
     * Localiza pelo nome exato, ignorando caixa.
     *
     * <p>Usado apenas pela carga do acervo, onde as planilhas identificam o condutor pelo
     * primeiro nome. Em operação o identificador é o CPF — nome não é chave.
     */
    @Query("select c from Condutor c where lower(c.nome) = lower(:nome) and c.excluidoEm is null")
    Optional<Condutor> buscarPorNome(@Param("nome") String nome);

    @Query(value = """
            select c from Condutor c
            left join fetch c.obraAtual
            where c.excluidoEm is null
              and (:termo is null or lower(c.nome) like :termo or c.cpf like :termo
                   or lower(c.cargo) like :termo or lower(c.email) like :termo)
              and (:status is null or c.status = :status)
              and (:obraId is null or c.obraAtual.id = :obraId)
            """,
            countQuery = """
            select count(c) from Condutor c
            where c.excluidoEm is null
              and (:termo is null or lower(c.nome) like :termo or c.cpf like :termo
                   or lower(c.cargo) like :termo or lower(c.email) like :termo)
              and (:status is null or c.status = :status)
              and (:obraId is null or c.obraAtual.id = :obraId)
            """)
    Page<Condutor> pesquisar(
            @Param("termo") String termo,
            @Param("status") StatusCondutor status,
            @Param("obraId") Long obraId,
            Pageable paginacao);

    @Query("""
            select count(c) > 0 from Condutor c
            where c.cpf = :cpf and c.excluidoEm is null and (:id is null or c.id <> :id)
            """)
    boolean existeOutroComCpf(@Param("cpf") String cpf, @Param("id") Long id);

    /**
     * Condutores ativos cuja CNH vence até a data limite, incluindo as já vencidas.
     * Base dos alertas de 60 e 30 dias (RN-16).
     */
    @Query(value = """
            select c from Condutor c
            left join fetch c.obraAtual
            where c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor.ATIVO
              and c.cnhValidade is not null
              and c.cnhValidade <= :limite
            order by c.cnhValidade asc
            """)
    List<Condutor> comCnhVencendoAte(@Param("limite") LocalDate limite);
}
