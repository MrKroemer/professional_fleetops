package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.CondutorRequest;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.CondutorResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores.DadosDoCondutor;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores.FiltroCondutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import br.com.proyfebrasil.fleetops.shared.pagination.PaginaResponse;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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

/** Cadastro de condutores, com acompanhamento da validade da CNH (RN-16). */
@RestController
@RequestMapping("/api/v1/condutores")
@Tag(name = "Condutores", description = "Cadastro dos funcionários habilitados a conduzir a frota")
@SecurityRequirement(name = "bearerAuth")
public class CondutorController {

    private final ServicoDeCondutores servico;
    private final CadastroMapper mapper;

    public CondutorController(ServicoDeCondutores servico, CadastroMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Lista condutores com busca, filtros e paginação")
    @ApiResponse(responseCode = "200", description = "Página de condutores")
    public PaginaResponse<CondutorResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusCondutor status,
            @RequestParam(required = false) Long obraId,
            @ParameterObject @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroCondutor(termo, status, obraId), paginacao), mapper::paraResponse);
    }

    @GetMapping("/cnh-em-alerta")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Condutores com CNH vencida ou vencendo",
            description = "Habilitações vencidas ou a vencer dentro de 60 dias (RN-16).")
    @ApiResponse(responseCode = "200", description = "Condutores em alerta, do vencimento mais próximo ao mais distante")
    public List<CondutorResponse> comCnhEmAlerta() {
        return servico.comCnhEmAlerta().stream().map(mapper::paraResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Detalha um condutor")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Condutor encontrado"),
        @ApiResponse(responseCode = "404", description = "Condutor inexistente"),
    })
    public CondutorResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.avaliarCnh(id));
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Cadastra um condutor")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Condutor criado"),
        @ApiResponse(responseCode = "400", description = "CPF inválido"),
        @ApiResponse(responseCode = "409", description = "CPF já cadastrado"),
    })
    public ResponseEntity<CondutorResponse> criar(@Valid @RequestBody CondutorRequest requisicao) {
        Long id = servico.criar(paraDados(requisicao)).getId();
        CondutorResponse criado = mapper.paraResponse(servico.avaliarCnh(id));
        return ResponseEntity.created(URI.create("/api/v1/condutores/" + id)).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Atualiza um condutor")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Condutor atualizado"),
        @ApiResponse(responseCode = "404", description = "Condutor inexistente"),
        @ApiResponse(responseCode = "409", description = "CPF já cadastrado"),
    })
    public CondutorResponse atualizar(@PathVariable Long id, @Valid @RequestBody CondutorRequest requisicao) {
        servico.atualizar(id, paraDados(requisicao));
        return mapper.paraResponse(servico.avaliarCnh(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Exclui logicamente um condutor")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Condutor excluído"),
        @ApiResponse(responseCode = "404", description = "Condutor inexistente"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private DadosDoCondutor paraDados(CondutorRequest requisicao) {
        return new DadosDoCondutor(
                requisicao.nome(),
                requisicao.cpf(),
                requisicao.cargo(),
                requisicao.telefone(),
                requisicao.email(),
                requisicao.cnhNumero(),
                requisicao.cnhCategoria(),
                requisicao.cnhValidade(),
                requisicao.obraAtualId(),
                requisicao.status(),
                requisicao.observacoes());
    }
}
