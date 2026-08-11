package br.com.proyfebrasil.fleetops.cadastros.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link Fornecedor} e seus dados específicos por tipo. */
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    @Query("select f from Fornecedor f where f.id = :id and f.excluidoEm is null")
    Optional<Fornecedor> buscarPorId(@Param("id") Long id);

    /**
     * Carrega o fornecedor com obras e dados específicos já resolvidos.
     * Evita o problema de N+1 ao montar a resposta de detalhe.
     */
    @Query("""
            select distinct f from Fornecedor f
            left join fetch f.obras
            left join fetch f.dadosPosto
            left join fetch f.dadosLavaJato
            left join fetch f.dadosRastreador
            left join fetch f.dadosGrafica
            where f.id = :id and f.excluidoEm is null
            """)
    Optional<Fornecedor> buscarDetalhado(@Param("id") Long id);

    @Query("""
            select distinct f from Fornecedor f
            left join f.obras o
            where f.excluidoEm is null
              and (:termo is null or lower(f.nome) like :termo or lower(f.cidade) like :termo
                   or lower(f.responsavel) like :termo)
              and (:tipo is null or f.tipo = :tipo)
              and (:ativo is null or f.ativo = :ativo)
              and (:obraId is null or o.id = :obraId)
            """)
    Page<Fornecedor> pesquisar(
            @Param("termo") String termo,
            @Param("tipo") TipoFornecedor tipo,
            @Param("ativo") Boolean ativo,
            @Param("obraId") Long obraId,
            Pageable paginacao);

    @Query("""
            select count(f) > 0 from Fornecedor f
            where lower(f.nome) = lower(:nome) and f.tipo = :tipo
              and f.excluidoEm is null and (:id is null or f.id <> :id)
            """)
    boolean existeOutroComNomeETipo(
            @Param("nome") String nome, @Param("tipo") TipoFornecedor tipo, @Param("id") Long id);
}
