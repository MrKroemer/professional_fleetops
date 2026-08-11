package br.com.proyfebrasil.fleetops.contratos.infra;

import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a {@link ContratoDeLocacao}. */
public interface ContratoRepository extends JpaRepository<ContratoDeLocacao, Long> {

    /**
     * Página de contratos com os filtros da tela.
     *
     * <p>Obra, locadora, veículo e condutor vêm por `join fetch`: a listagem mostra os
     * quatro em cada linha, e sem isso seriam quatro consultas por contrato — oitenta
     * idas ao banco para desenhar vinte linhas.
     *
     * <p>O `countQuery` é explícito porque o `join fetch` não pode aparecer na contagem;
     * deixá-lo implícito faria o Hibernate montar um SQL inválido.
     */
    @Query(value = """
            select c from ContratoDeLocacao c
            join fetch c.obra o
            join fetch c.locadora l
            left join fetch c.veiculoAtual v
            left join fetch c.condutorAtual cd
            where c.excluidoEm is null
              and (:status is null or c.status = :status)
              and (:obraId is null or o.id = :obraId)
              and (:locadoraId is null or l.id = :locadoraId)
              and (:termo is null
                   or lower(c.codigoInterno) like :termo
                   or lower(o.nome) like :termo
                   or lower(o.codigo) like :termo
                   or lower(v.placa) like :termo
                   or lower(cd.nome) like :termo)
            """,
            countQuery = """
            select count(c) from ContratoDeLocacao c
            join c.obra o
            join c.locadora l
            left join c.veiculoAtual v
            left join c.condutorAtual cd
            where c.excluidoEm is null
              and (:status is null or c.status = :status)
              and (:obraId is null or o.id = :obraId)
              and (:locadoraId is null or l.id = :locadoraId)
              and (:termo is null
                   or lower(c.codigoInterno) like :termo
                   or lower(o.nome) like :termo
                   or lower(o.codigo) like :termo
                   or lower(v.placa) like :termo
                   or lower(cd.nome) like :termo)
            """)
    Page<ContratoDeLocacao> listar(
            @Param("termo") String termo,
            @Param("status") StatusContrato status,
            @Param("obraId") Long obraId,
            @Param("locadoraId") Long locadoraId,
            Pageable paginacao);

    @Query("select c from ContratoDeLocacao c where c.id = :id and c.excluidoEm is null")
    Optional<ContratoDeLocacao> buscarPorId(@Param("id") Long id);

    /**
     * Contratos ativos de um veículo.
     *
     * <p>Devolve <strong>lista</strong>, e não um único contrato. A V3 tinha um índice
     * garantindo unicidade, e a V4 o removeu: a RN-01 fala do contrato — "um contrato tem
     * um veículo ativo por vez" —, não do veículo, e o acervo real traz três placas em dois
     * contratos ativos ao mesmo tempo. A assinatura anterior prometia uma unicidade que o
     * banco deixou de garantir, e estourava em {@code IncorrectResultSizeDataAccessException}
     * exatamente nesses três casos.
     *
     * <p>O conflito continua aparecendo na central de pendências (RN-23), que é onde a
     * decisão humana cabe. Aqui a consulta apenas relata o que existe.
     */
    @Query("""
            select c from ContratoDeLocacao c
            where c.veiculoAtual.id = :veiculoId and c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato.ATIVO
            order by c.dataRetirada desc, c.id desc
            """)
    List<ContratoDeLocacao> ativosDoVeiculo(@Param("veiculoId") Long veiculoId);

    /**
     * Todos os contratos que já tiveram este veículo, do mais recente para o mais antigo.
     *
     * <p>Inclui os encerrados de propósito: um lançamento histórico — a quilometragem de um
     * mês em que o carro rodou — pertence ao contrato daquela época, mesmo que ele tenha
     * sido devolvido depois. Recusá-lo por o contrato estar fechado descartaria justamente
     * o histórico que a RN-18 manda preservar.
     */
    @Query("""
            select c from ContratoDeLocacao c
            where c.veiculoAtual.id = :veiculoId and c.excluidoEm is null
            order by case when c.status = br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato.ATIVO
                          then 0 else 1 end,
                     c.dataRetirada desc, c.id desc
            """)
    List<ContratoDeLocacao> historicoDoVeiculo(@Param("veiculoId") Long veiculoId);

    /**
     * Contratos ativos com obra, condutor e veículo já resolvidos.
     *
     * <p>O `join fetch` existe porque a tela de cards precisa dos três em cada linha:
     * sem ele seriam três consultas por veículo, e com 49 veículos isso é 147 idas ao
     * banco para desenhar uma grade.
     */
    @Query("""
            select c from ContratoDeLocacao c
            join fetch c.obra
            join fetch c.locadora
            left join fetch c.condutorAtual
            left join fetch c.veiculoAtual
            where c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato.ATIVO
              and c.veiculoAtual is not null
            order by c.veiculoAtual.placa
            """)
    List<ContratoDeLocacao> ativosComVeiculo();

    @Query("""
            select count(c) from ContratoDeLocacao c
            where c.excluidoEm is null
              and c.status = br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato.ATIVO
            """)
    long contarAtivos();
}
