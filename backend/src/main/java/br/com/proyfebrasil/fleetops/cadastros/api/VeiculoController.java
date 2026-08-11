package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.VeiculoRequest;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.VeiculoResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos.DadosDoVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos.FiltroVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
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

/** Cadastro de veículos, com normalização e unicidade de placa (RN-02). */
@RestController
@RequestMapping("/api/v1/veiculos")
@Tag(name = "Veículos", description = "Cadastro dos veículos alugados das locadoras")
@SecurityRequirement(name = "bearerAuth")
public class VeiculoController {

    private final ServicoDeVeiculos servico;
    private final CadastroMapper mapper;

    public VeiculoController(ServicoDeVeiculos servico, CadastroMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Lista veículos com busca, filtros e paginação",
            description = "A busca por placa aceita qualquer grafia: ABC1D23, abc-1d23 ou parte dela.")
    @ApiResponse(responseCode = "200", description = "Página de veículos")
    public PaginaResponse<VeiculoResponse> listar(
            @Parameter(description = "Busca por placa, modelo, fabricante ou código interno")
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) Long locadoraId,
            @RequestParam(required = false) CategoriaVeiculo categoria,
            @RequestParam(required = false) StatusVeiculo status,
            @ParameterObject @PageableDefault(size = 20, sort = "placa", direction = Sort.Direction.ASC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroVeiculo(termo, locadoraId, categoria, status), paginacao),
                mapper::paraResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Detalha um veículo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
        @ApiResponse(responseCode = "404", description = "Veículo inexistente"),
    })
    public VeiculoResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.buscar(id));
    }

    @GetMapping("/por-placa/{placa}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Busca um veículo pela placa",
            description = "Aceita as grafias Mercosul e antiga, com ou sem separadores (RN-02).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
        @ApiResponse(responseCode = "400", description = "Placa em formato inválido"),
        @ApiResponse(responseCode = "404", description = "Nenhum veículo com esta placa"),
    })
    public VeiculoResponse detalharPorPlaca(@PathVariable String placa) {
        return mapper.paraResponse(servico.buscarPorPlaca(placa));
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Cadastra um veículo")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Veículo criado"),
        @ApiResponse(responseCode = "400", description = "Placa em formato inválido"),
        @ApiResponse(responseCode = "409", description = "Placa já cadastrada"),
    })
    public ResponseEntity<VeiculoResponse> criar(@Valid @RequestBody VeiculoRequest requisicao) {
        VeiculoResponse criado = mapper.paraResponse(servico.criar(paraDados(requisicao)));
        return ResponseEntity.created(URI.create("/api/v1/veiculos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Atualiza um veículo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Veículo atualizado"),
        @ApiResponse(responseCode = "404", description = "Veículo inexistente"),
        @ApiResponse(responseCode = "409", description = "Placa já cadastrada"),
    })
    public VeiculoResponse atualizar(@PathVariable Long id, @Valid @RequestBody VeiculoRequest requisicao) {
        return mapper.paraResponse(servico.atualizar(id, paraDados(requisicao)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Exclui logicamente um veículo")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Veículo excluído"),
        @ApiResponse(responseCode = "404", description = "Veículo inexistente"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private DadosDoVeiculo paraDados(VeiculoRequest requisicao) {
        return new DadosDoVeiculo(
                requisicao.placa(),
                requisicao.modelo(),
                requisicao.fabricante(),
                requisicao.anoFabricacao(),
                requisicao.categoria(),
                requisicao.combustivel(),
                requisicao.locadoraId(),
                requisicao.grupoTarifario(),
                requisicao.codigoInterno(),
                requisicao.possuiRastreador(),
                requisicao.fornecedorRastreador(),
                requisicao.possuiAdesivo(),
                requisicao.status(),
                requisicao.observacoes());
    }
}
