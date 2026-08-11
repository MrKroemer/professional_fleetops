package br.com.proyfebrasil.fleetops.administracao.api;

import br.com.proyfebrasil.fleetops.administracao.api.dto.UsuarioRequest;
import br.com.proyfebrasil.fleetops.administracao.api.dto.UsuarioResponse;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeUsuarios;
import br.com.proyfebrasil.fleetops.administracao.application.ServicoDeUsuarios.FiltroUsuario;
import br.com.proyfebrasil.fleetops.shared.pagination.PaginaResponse;
import br.com.proyfebrasil.fleetops.shared.security.Perfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administração de usuários — exclusiva do perfil {@code ADMIN} (RN-19).
 *
 * <p>A autorização é declarada endpoint a endpoint com {@code @PreAuthorize} para que a
 * matriz de permissões possa ser testada individualmente, e não inferida de uma regra
 * genérica de URL.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuários", description = "Administração de usuários e perfis de acesso")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final ServicoDeUsuarios servico;
    private final UsuarioMapper mapper;

    public UsuarioController(ServicoDeUsuarios servico, UsuarioMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Lista usuários com busca, filtros e paginação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de usuários"),
        @ApiResponse(responseCode = "403", description = "Perfil sem permissão"),
    })
    public PaginaResponse<UsuarioResponse> listar(
            @Parameter(description = "Busca parcial por nome ou e-mail") @RequestParam(required = false) String termo,
            @Parameter(description = "Filtra por perfil de acesso") @RequestParam(required = false) Perfil perfil,
            @Parameter(description = "Filtra por status") @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroUsuario(termo, perfil, ativo), paginacao), mapper::paraResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um usuário")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
        @ApiResponse(responseCode = "404", description = "Usuário inexistente"),
    })
    public UsuarioResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.buscar(id));
    }

    @PostMapping
    @Operation(summary = "Cria um usuário")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuário criado"),
        @ApiResponse(responseCode = "409", description = "E-mail já cadastrado"),
    })
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest requisicao) {
        UsuarioResponse criado = mapper.paraResponse(servico.criar(
                requisicao.nome(),
                requisicao.email(),
                requisicao.perfil(),
                requisicao.senha(),
                requisicao.ativo()));
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Atualiza um usuário",
            description = "Senha em branco mantém a atual. Trocar a senha ou desativar o usuário encerra suas sessões.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário atualizado"),
        @ApiResponse(responseCode = "404", description = "Usuário inexistente"),
        @ApiResponse(responseCode = "409", description = "E-mail já cadastrado"),
    })
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest requisicao) {
        return mapper.paraResponse(servico.atualizar(
                id,
                requisicao.nome(),
                requisicao.email(),
                requisicao.perfil(),
                requisicao.senha(),
                requisicao.ativo()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui logicamente um usuário", description = "Exclusão física nunca é executada.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Usuário excluído"),
        @ApiResponse(responseCode = "404", description = "Usuário inexistente"),
        @ApiResponse(responseCode = "409", description = "Tentativa de excluir o próprio usuário"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        servico.excluir(id, jwt.getClaimAsString("email"));
        return ResponseEntity.noContent().build();
    }
}
