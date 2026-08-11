package br.com.proyfebrasil.fleetops.contratos.infra;

import br.com.proyfebrasil.fleetops.contratos.domain.EventoDeContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.SituacaoDoEvento;
import br.com.proyfebrasil.fleetops.contratos.domain.TipoDeEvento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos eventos de retirada e devolução. */
public interface EventoDeContratoRepository extends JpaRepository<EventoDeContrato, Long> {

    @Query("select e from EventoDeContrato e where e.id = :id and e.excluidoEm is null")
    Optional<EventoDeContrato> buscarPorId(@Param("id") Long id);

    /**
     * Eventos de um contrato, do mais recente para o mais antigo.
     *
     * <p>Veículo e condutor vêm resolvidos porque a linha do tempo mostra os dois em cada
     * marco; sem isso seriam duas consultas por evento ao montar a tela.
     */
    @Query("""
            select e from EventoDeContrato e
            join fetch e.veiculo
            left join fetch e.condutor
            left join fetch e.crlv
            where e.contrato.id = :contratoId and e.excluidoEm is null
            order by e.dataDoEvento desc, e.id desc
            """)
    List<EventoDeContrato> doContrato(@Param("contratoId") Long contratoId);

    /** Último evento concluído de um tipo — o que a RN-17 consulta antes de devolver. */
    @Query("""
            select e from EventoDeContrato e
            where e.contrato.id = :contratoId and e.tipo = :tipo
              and e.situacao = :situacao and e.excluidoEm is null
            order by e.dataDoEvento desc, e.id desc
            limit 1
            """)
    Optional<EventoDeContrato> ultimoDoTipo(
            @Param("contratoId") Long contratoId,
            @Param("tipo") TipoDeEvento tipo,
            @Param("situacao") SituacaoDoEvento situacao);
}
