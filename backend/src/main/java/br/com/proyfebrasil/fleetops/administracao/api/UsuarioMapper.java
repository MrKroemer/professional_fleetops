package br.com.proyfebrasil.fleetops.administracao.api;

import br.com.proyfebrasil.fleetops.administracao.api.dto.UsuarioResponse;
import br.com.proyfebrasil.fleetops.administracao.domain.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Usuario} para DTO de resposta.
 *
 * <p>O mapeamento é unidirecional por desenho: a criação e a atualização passam pelos
 * métodos de negócio da entidade, e não por um mapeador que sobrescreveria campos
 * sem validação.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UsuarioMapper {

    @Mapping(target = "perfilDescricao", expression = "java(usuario.getPerfil().getDescricao())")
    UsuarioResponse paraResponse(Usuario usuario);
}
