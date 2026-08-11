package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.FornecedorResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.ObraResumoResponse;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosGrafica;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosLavaJato;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosPosto;
import br.com.proyfebrasil.fleetops.cadastros.domain.DadosRastreador;
import br.com.proyfebrasil.fleetops.cadastros.domain.Fornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.shared.security.CriptografiaCredenciais;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Fornecedor} para DTO.
 *
 * <p>Fica separado de {@link CadastroMapper} porque a montagem dos blocos por tipo exige
 * lógica condicional que ficaria escondida no meio de mapeamentos triviais.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FornecedorMapper {

    @Mapping(target = "tipoDescricao", expression = "java(fornecedor.getTipo().getDescricao())")
    @Mapping(target = "obras", expression = "java(obrasOrdenadas(fornecedor.getObras()))")
    @Mapping(target = "posto", expression = "java(paraResponse(fornecedor.getDadosPosto()))")
    @Mapping(target = "lavaJato", expression = "java(paraResponse(fornecedor.getDadosLavaJato()))")
    @Mapping(target = "rastreador", expression = "java(paraResponse(fornecedor.getDadosRastreador()))")
    @Mapping(target = "grafica", expression = "java(paraResponse(fornecedor.getDadosGrafica()))")
    FornecedorResponse paraResponse(Fornecedor fornecedor);

    /** Obras em ordem de código, para que a listagem não mude de posição a cada consulta. */
    default List<ObraResumoResponse> obrasOrdenadas(Set<Obra> obras) {
        if (obras == null) {
            return List.of();
        }
        return obras.stream()
                .sorted(Comparator.comparing(Obra::getCodigo))
                .map(obra -> new ObraResumoResponse(obra.getId(), obra.getCodigo(), obra.getNome()))
                .toList();
    }

    /**
     * Dados de posto.
     *
     * <p>Conjunto de dias vazio significa "sem restrição", e não "nenhum dia autorizado" —
     * daí o campo {@code semRestricaoDeDia}, que evita que a interface precise conhecer
     * essa convenção.
     */
    default FornecedorResponse.DadosDePostoResponse paraResponse(DadosPosto dados) {
        if (dados == null) {
            return null;
        }
        return new FornecedorResponse.DadosDePostoResponse(
                dados.getDiasAutorizados(), dados.getDiasAutorizados().isEmpty(), dados.getAcessoFaturas());
    }

    default FornecedorResponse.DadosDeLavaJatoResponse paraResponse(DadosLavaJato dados) {
        if (dados == null) {
            return null;
        }
        return new FornecedorResponse.DadosDeLavaJatoResponse(
                dados.getServicosPorSemana(),
                dados.getPrecoPasseio(),
                dados.getPrecoSuv(),
                dados.getPrecoQuatroXQuatro());
    }

    /** Dados de rastreador. As credenciais saem apenas mascaradas (RN-20). */
    default FornecedorResponse.DadosDeRastreadorResponse paraResponse(DadosRastreador dados) {
        if (dados == null) {
            return null;
        }
        boolean possui = dados.possuiCredenciais();
        return new FornecedorResponse.DadosDeRastreadorResponse(
                dados.getMensalidade(),
                dados.getCustoInstalacao(),
                dados.getCustoDesinstalacao(),
                dados.getEquipadora(),
                dados.getPortalUrl(),
                possui,
                CriptografiaCredenciais.mascaraPara(possui));
    }

    default FornecedorResponse.DadosDeGraficaResponse paraResponse(DadosGrafica dados) {
        if (dados == null) {
            return null;
        }
        return new FornecedorResponse.DadosDeGraficaResponse(
                dados.getTamanhoAdesivo(),
                dados.getPrecoAdesivo(),
                dados.getTamanhoIma(),
                dados.getPrecoIma());
    }
}
