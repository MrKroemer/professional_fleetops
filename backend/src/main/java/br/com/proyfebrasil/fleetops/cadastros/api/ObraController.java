package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.ObraRequest;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.ObraResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras.DadosDaObra;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras.FiltroObra;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusObra;
import br.com.proyfebrasil.fleetops.shared.pagination.PaginaResponse;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de obras. */
@RestController
@RequestMapping("/api/v1/obras")
@Tag(name = "Obras", description = "Cadastro das frentes de trabalho onde a frota é alocada")
@SecurityRequirement(name = "bearerAuth")
public class ObraController {

    private final ServicoDeObras servico;
    private final CadastroMapper mapper;

    public ObraController(ServicoDeObras servico, CadastroMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Lista obras com busca, filtros e paginação")
    @ApiResponse(responseCode = "200", description = "Página de obras")
    public PaginaResponse<ObraResponse> listar(
            @Parameter(description = "Busca por código, nome, cliente ou cidade")
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusObra status,
            @Parameter(description = "Sigla da UF, ex.: BA") @RequestParam(required = false) String uf,
            @ParameterObject @PageableDefault(size = 20, sort = "codigo", direction = Sort.Direction.ASC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroObra(termo, status, uf), paginacao), mapper::paraResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Detalha uma obra")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Obra encontrada"),
        @ApiResponse(responseCode = "404", description = "Obra inexistente"),
    })
    public ObraResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.buscar(id));
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Cadastra uma obra")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Obra criada"),
        @ApiResponse(responseCode = "409", description = "Código de obra já utilizado"),
    })
    public ResponseEntity<ObraResponse> criar(@Valid @RequestBody ObraRequest requisicao) {
        ObraResponse criada = mapper.paraResponse(servico.criar(paraDados(requisicao)));
        return ResponseEntity.created(URI.create("/api/v1/obras/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Atualiza uma obra")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Obra atualizada"),
        @ApiResponse(responseCode = "404", description = "Obra inexistente"),
        @ApiResponse(responseCode = "409", description = "Código de obra já utilizado"),
    })
    public ObraResponse atualizar(@PathVariable Long id, @Valid @RequestBody ObraRequest requisicao) {
        return mapper.paraResponse(servico.atualizar(id, paraDados(requisicao)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Exclui logicamente uma obra")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Obra excluída"),
        @ApiResponse(responseCode = "404", description = "Obra inexistente"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private DadosDaObra paraDados(ObraRequest requisicao) {
        return new DadosDaObra(
                requisicao.codigo(),
                requisicao.nome(),
                requisicao.cliente(),
                requisicao.cidade(),
                requisicao.uf(),
                requisicao.status(),
                requisicao.dataInicio(),
                requisicao.dataFim(),
                requisicao.observacoes());
    }
}
