package br.com.proyfebrasil.fleetops.painel.infra;

import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas da busca global da barra superior.
 *
 * <p>Três consultas separadas, e não uma união: cada entidade tem campos próprios de
 * busca e um rótulo próprio, e uni-las em SQL exigiria colunas artificiais em todas
 * para caber num formato comum. Três consultas curtas custam menos que uma união
 * genérica — e permitem que cada uma traga o contexto que o resultado precisa mostrar.
 */
public interface BuscaGlobalRepository extends Repository<Veiculo, Long> {

    /** Um resultado da busca, já com o texto que a interface exibe. */
    interface Achado {
        Long getId();

        String getRotulo();

        String getDetalhe();
    }

    /**
     * Veículos por placa, modelo ou código interno.
     *
     * <p>O detalhe traz modelo e locadora porque a placa sozinha não confirma que o
     * usuário achou o veículo certo — placas se parecem entre si.
     */
    @Query("""
            select v.id as id,
                   v.placa as rotulo,
                   concat(v.modelo, ' · ', v.locadora.nome) as detalhe
            from Veiculo v
            where v.excluidoEm is null
              and (lower(v.placa) like :termoPlaca
                   or lower(v.modelo) like :termo
                   or lower(v.codigoInterno) like :termo)
            order by v.placa
            """)
    List<Achado> veiculos(
            @Param("termo") String termo, @Param("termoPlaca") String termoPlaca, Pageable limite);

    /** Condutores por nome ou CPF. */
    @Query("""
            select c.id as id,
                   c.nome as rotulo,
                   coalesce(c.cargo, 'Sem cargo informado') as detalhe
            from Condutor c
            where c.excluidoEm is null
              and (lower(c.nome) like :termo or c.cpf like :termoDigitos)
            order by c.nome
            """)
    List<Achado> condutores(
            @Param("termo") String termo, @Param("termoDigitos") String termoDigitos, Pageable limite);

    /** Obras por código, nome, cliente ou cidade. */
    @Query("""
            select o.id as id,
                   concat(o.codigo, ' — ', o.nome) as rotulo,
                   concat(o.cidade, ' · ', o.uf) as detalhe
            from Obra o
            where o.excluidoEm is null
              and (lower(o.codigo) like :termo
                   or lower(o.nome) like :termo
                   or lower(o.cliente) like :termo
                   or lower(o.cidade) like :termo)
            order by o.codigo
            """)
    List<Achado> obras(@Param("termo") String termo, Pageable limite);
}
