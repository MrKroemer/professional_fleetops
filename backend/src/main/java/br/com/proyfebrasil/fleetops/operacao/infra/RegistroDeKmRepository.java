package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.operacao.domain.RegistroDeKm;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos registros diários de quilometragem. */
public interface RegistroDeKmRepository extends JpaRepository<RegistroDeKm, Long> {

    @Query("select r from RegistroDeKm r where r.id = :id and r.excluidoEm is null")
    Optional<RegistroDeKm> buscarPorId(@Param("id") Long id);

    /**
     * Último registro do contrato até a data — a referência da RN-03.
     *
     * <p>Recebe a data porque o encadeamento é temporal, não de inserção: lançar hoje um
     * registro de março tem de ser conferido contra o registro de março, não contra o
     * último digitado. `id <> :ignorar` deixa a edição comparar-se com os vizinhos, e não
     * consigo mesma.
     */
    @Query("""
            select r from RegistroDeKm r
            where r.contrato.id = :contratoId and r.excluidoEm is null
              and r.data <= :data and (:ignorar is null or r.id <> :ignorar)
            order by r.data desc, r.id desc
            limit 1
            """)
    Optional<RegistroDeKm> anteriorA(
            @Param("contratoId") Long contratoId,
            @Param("data") LocalDate data,
            @Param("ignorar") Long ignorar);

    /** Primeiro registro depois da data — o lançamento retroativo precisa caber entre os dois. */
    @Query("""
            select r from RegistroDeKm r
            where r.contrato.id = :contratoId and r.excluidoEm is null
              and r.data > :data and (:ignorar is null or r.id <> :ignorar)
            order by r.data asc, r.id asc
            limit 1
            """)
    Optional<RegistroDeKm> posteriorA(
            @Param("contratoId") Long contratoId,
            @Param("data") LocalDate data,
            @Param("ignorar") Long ignorar);

    /**
     * Competência mais recente com algum lançamento de quilometragem.
     *
     * <p>Existe porque os lançamentos chegam com atraso — o FOR.FRO.02 é preenchido em
     * papel e digitado dias ou semanas depois. Um alerta preso ao "mês anterior" do
     * calendário desapareceria exatamente quando a digitação atrasasse, que é quando ele
     * mais faz falta. Perguntando ao dado qual foi o último mês apurado, o alerta fala
     * sempre do mês mais recente que existe.
     */
    @Query("""
            select max(r.data) from RegistroDeKm r where r.excluidoEm is null
            """)
    Optional<LocalDate> dataDoUltimoLancamento();

    @Query("""
            select r from RegistroDeKm r
            left join fetch r.condutor
            where r.contrato.id = :contratoId and r.excluidoEm is null
              and r.data between :inicio and :fim
            order by r.data asc, r.id asc
            """)
    List<RegistroDeKm> doPeriodo(
            @Param("contratoId") Long contratoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
