package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.CanaisDeAtendimentoDto;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.CondutorResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.LocadoraResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.LocadoraResumoResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.ObraResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.ObraResumoResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.VeiculoResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores.SituacaoDaCnh;
import br.com.proyfebrasil.fleetops.cadastros.domain.CanaisDeAtendimento;
import br.com.proyfebrasil.fleetops.cadastros.domain.Cpf;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.shared.security.CriptografiaCredenciais;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão das entidades de cadastro para DTOs de resposta.
 *
 * <p>O mapeamento é unidirecional: criação e atualização passam pelos métodos de negócio
 * das entidades, que validam invariantes. Um mapeador de entrada as sobrescreveria campo
 * a campo, sem passar por nenhuma dessas validações.
 *
 * <p>{@code unmappedTargetPolicy = ERROR} é deliberado: acrescentar um campo ao DTO sem
 * mapeá-lo quebra a compilação, em vez de produzir silenciosamente um {@code null} na
 * resposta da API.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        imports = {CriptografiaCredenciais.class, Cpf.class})
public interface CadastroMapper {

    // -----------------------------------------------------------------
    // Obra
    // -----------------------------------------------------------------

    @Mapping(target = "statusDescricao", expression = "java(obra.getStatus().getDescricao())")
    ObraResponse paraResponse(Obra obra);

    ObraResumoResponse paraResumo(Obra obra);

    // -----------------------------------------------------------------
    // Locadora
    // -----------------------------------------------------------------

    @Mapping(target = "tipoDescricao", expression = "java(locadora.getTipo().getDescricao())")
    @Mapping(target = "possuiCredenciais", expression = "java(locadora.possuiCredenciais())")
    @Mapping(
            target = "credencialMascarada",
            expression = "java(CriptografiaCredenciais.mascaraPara(locadora.possuiCredenciais()))")
    LocadoraResponse paraResponse(Locadora locadora);

    LocadoraResumoResponse paraResumo(Locadora locadora);

    CanaisDeAtendimentoDto paraDto(CanaisDeAtendimento canais);

    // -----------------------------------------------------------------
    // Condutor
    // -----------------------------------------------------------------

    /**
     * Converte o condutor junto da situação da CNH já avaliada (RN-16).
     *
     * <p>A avaliação vem do serviço, e não é recalculada aqui: assim existe uma única
     * fonte de verdade sobre a data de referência usada na comparação.
     */
    @Mapping(target = "id", source = "condutor.id")
    @Mapping(target = "nome", source = "condutor.nome")
    @Mapping(target = "cpf", source = "condutor.cpf")
    @Mapping(target = "cpfFormatado", expression = "java(Cpf.formatar(situacao.condutor().getCpf()))")
    @Mapping(target = "cargo", source = "condutor.cargo")
    @Mapping(target = "telefone", source = "condutor.telefone")
    @Mapping(target = "email", source = "condutor.email")
    @Mapping(target = "cnhNumero", source = "condutor.cnhNumero")
    @Mapping(target = "cnhCategoria", source = "condutor.cnhCategoria")
    @Mapping(target = "cnhValidade", source = "condutor.cnhValidade")
    @Mapping(target = "cnhVencida", source = "vencida")
    @Mapping(target = "cnhEmAlerta", source = "emAlerta")
    @Mapping(target = "diasParaVencerCnh", source = "diasParaVencer")
    @Mapping(target = "obraAtual", source = "condutor.obraAtual")
    @Mapping(target = "status", source = "condutor.status")
    @Mapping(target = "statusDescricao", expression = "java(situacao.condutor().getStatus().getDescricao())")
    @Mapping(target = "observacoes", source = "condutor.observacoes")
    @Mapping(target = "criadoEm", source = "condutor.criadoEm")
    @Mapping(target = "atualizadoEm", source = "condutor.atualizadoEm")
    CondutorResponse paraResponse(SituacaoDaCnh situacao);

    // -----------------------------------------------------------------
    // Veículo
    // -----------------------------------------------------------------

    @Mapping(target = "placaFormatada", expression = "java(veiculo.getPlacaFormatada())")
    @Mapping(target = "categoriaDescricao", expression = "java(veiculo.getCategoria().getDescricao())")
    @Mapping(target = "combustivelDescricao", expression = "java(veiculo.getCombustivel().getDescricao())")
    @Mapping(target = "exigeTesteFumacaPreta", expression = "java(veiculo.exigeTesteDeFumacaPreta())")
    @Mapping(target = "statusDescricao", expression = "java(veiculo.getStatus().getDescricao())")
    VeiculoResponse paraResponse(Veiculo veiculo);
}
