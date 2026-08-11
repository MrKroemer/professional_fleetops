package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.LocadoraResumoResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.TabelaPrecoResponse;
import br.com.proyfebrasil.fleetops.cadastros.domain.GrupoTarifario;
import br.com.proyfebrasil.fleetops.cadastros.domain.PrecoKmExcedente;
import br.com.proyfebrasil.fleetops.cadastros.domain.PrecoPacoteKm;
import br.com.proyfebrasil.fleetops.cadastros.domain.TabelaPreco;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Conversão de {@link TabelaPreco} e sua grade de grupos, pacotes e KM excedente. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TabelaPrecoMapper {

    @Mapping(target = "locadora", expression = "java(paraResumo(tabela))")
    @Mapping(target = "grupos", expression = "java(gruposDe(tabela))")
    @Mapping(target = "kmExcedente", expression = "java(excedentesDe(tabela))")
    TabelaPrecoResponse paraResponse(TabelaPreco tabela);

    /** Identificação da locadora dona da vigência. */
    default LocadoraResumoResponse paraResumo(TabelaPreco tabela) {
        return new LocadoraResumoResponse(tabela.getLocadora().getId(), tabela.getLocadora().getNome());
    }

    /** Grupos já ordenados por código, como aparecem na tabela enviada pela locadora. */
    default List<TabelaPrecoResponse.GrupoResponse> gruposDe(TabelaPreco tabela) {
        return tabela.getGrupos().stream().map(this::paraResponse).toList();
    }

    default TabelaPrecoResponse.GrupoResponse paraResponse(GrupoTarifario grupo) {
        return new TabelaPrecoResponse.GrupoResponse(
                grupo.getId(),
                grupo.getCodigo(),
                grupo.getVeiculosDoGrupo(),
                grupo.getCategoria(),
                grupo.getCategoria().getDescricao(),
                grupo.getPacotes().stream().map(this::paraResponse).toList());
    }

    default TabelaPrecoResponse.PacoteResponse paraResponse(PrecoPacoteKm pacote) {
        return new TabelaPrecoResponse.PacoteResponse(pacote.getPacoteKm(), pacote.getValorMensal());
    }

    /**
     * Preços de KM excedente ordenados por categoria e pacote.
     *
     * <p>As entradas sem pacote — que valem para todos — vêm primeiro, porque representam
     * a regra geral da locadora, e as específicas aparecem como exceções logo abaixo.
     */
    default List<TabelaPrecoResponse.KmExcedenteResponse> excedentesDe(TabelaPreco tabela) {
        return tabela.getPrecosKmExcedente().stream()
                .sorted(Comparator.comparing((PrecoKmExcedente preco) -> preco.getCategoria().ordinal())
                        .thenComparing(
                                PrecoKmExcedente::getPacoteKm,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(this::paraResponse)
                .toList();
    }

    default TabelaPrecoResponse.KmExcedenteResponse paraResponse(PrecoKmExcedente preco) {
        return new TabelaPrecoResponse.KmExcedenteResponse(
                preco.getCategoria(),
                preco.getCategoria().getDescricao(),
                preco.getPacoteKm(),
                preco.getValorKm());
    }
}
