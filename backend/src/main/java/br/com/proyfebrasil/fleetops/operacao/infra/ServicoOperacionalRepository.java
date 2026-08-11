package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.operacao.domain.ServicoOperacional;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos serviços de lava-jato, borracharia e para-brisas. */
public interface ServicoOperacionalRepository extends JpaRepository<ServicoOperacional, Long> {

    @Query("select s from ServicoOperacional s where s.id = :id and s.excluidoEm is null")
    Optional<ServicoOperacional> buscarPorId(@Param("id") Long id);

    /**
     * Existe serviço do mesmo tipo na janela informada? É a frequência da RN-05.
     *
     * <p>A janela é passada pelo chamador, e não calculada aqui, porque "semana" é uma
     * decisão de domínio — a semana do calendário, de segunda a domingo, e não sete dias
     * corridos a partir do lançamento.
     */
    @Query("""
            select count(s) > 0 from ServicoOperacional s
            where s.contrato.id = :contratoId and s.tipo = :tipo and s.excluidoEm is null
              and s.data between :inicio and :fim
              and (:ignorar is null or s.id <> :ignorar)
            """)
    boolean existeNaJanela(
            @Param("contratoId") Long contratoId,
            @Param("tipo") TipoDeServico tipo,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("ignorar") Long ignorar);

    @Query("""
            select s from ServicoOperacional s
            left join fetch s.fornecedor
            where s.contrato.id = :contratoId and s.excluidoEm is null
              and s.data between :inicio and :fim
            order by s.data asc, s.id asc
            """)
    List<ServicoOperacional> doPeriodo(
            @Param("contratoId") Long contratoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
