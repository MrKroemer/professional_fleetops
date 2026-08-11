package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.TabelaPrecoRequest;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.TabelaPrecoResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco.DadosDaTabela;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco.DadosDoGrupo;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco.DadosDoKmExcedente;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco.DadosDoPacote;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco.FiltroTabela;
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
import java.time.YearMonth;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Tabelas de preço de locação, uma por locadora e ano de vigência (RN-14). */
@RestController
@RequestMapping("/api/v1/tabelas-preco")
@Tag(name = "Tabelas de preço", description = "Vigências anuais de preço de locação por locadora")
@SecurityRequirement(name = "bearerAuth")
public class TabelaPrecoController {

    private final ServicoDeTabelasDePreco servico;
    private final TabelaPrecoMapper mapper;

    public TabelaPrecoController(ServicoDeTabelasDePreco servico, TabelaPrecoMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Lista vigências de tabelas de preço")
    @ApiResponse(responseCode = "200", description = "Página de vigências")
    public PaginaResponse<TabelaPrecoResponse> listar(
            @RequestParam(required = false) Long locadoraId,
            @RequestParam(required = false) Integer ano,
            @ParameterObject
            @PageableDefault(size = 20, sort = "anoVigencia", direction = Sort.Direction.DESC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroTabela(locadoraId, ano), paginacao), mapper::paraResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Detalha uma vigência, com grupos, pacotes e KM excedente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vigência encontrada"),
        @ApiResponse(responseCode = "404", description = "Vigência inexistente"),
    })
    public TabelaPrecoResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.buscar(id));
    }

    @GetMapping("/vigencia")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Tabela aplicável a uma competência",
            description = "Usa o ano da competência informada, não o ano corrente (RN-14).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vigência aplicável"),
        @ApiResponse(responseCode = "404", description = "Nenhuma vigência para a competência"),
    })
    public TabelaPrecoResponse porCompetencia(
            @RequestParam Long locadoraId,
            @Parameter(description = "Competência no formato AAAA-MM, ex.: 2026-03", example = "2026-03")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth competencia) {
        return mapper.paraResponse(servico.vigenciaPara(locadoraId, competencia));
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Cadastra uma vigência de tabela de preços")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Vigência criada"),
        @ApiResponse(responseCode = "409", description = "Locadora já tem tabela para este ano"),
    })
    public ResponseEntity<TabelaPrecoResponse> criar(@Valid @RequestBody TabelaPrecoRequest requisicao) {
        TabelaPrecoResponse criada = mapper.paraResponse(servico.criar(paraDados(requisicao)));
        return ResponseEntity.created(URI.create("/api/v1/tabelas-preco/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(
            summary = "Atualiza uma vigência",
            description = "A grade é substituída por inteiro: grupos e pacotes ausentes deixam de existir.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vigência atualizada"),
        @ApiResponse(responseCode = "404", description = "Vigência inexistente"),
        @ApiResponse(responseCode = "409", description = "Locadora já tem tabela para este ano"),
    })
    public TabelaPrecoResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody TabelaPrecoRequest requisicao) {
        return mapper.paraResponse(servico.atualizar(id, paraDados(requisicao)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Exclui logicamente uma vigência")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Vigência excluída"),
        @ApiResponse(responseCode = "404", description = "Vigência inexistente"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private DadosDaTabela paraDados(TabelaPrecoRequest requisicao) {
        List<DadosDoGrupo> grupos = requisicao.grupos() == null
                ? List.of()
                : requisicao.grupos().stream()
                        .map(grupo -> new DadosDoGrupo(
                                grupo.codigo(),
                                grupo.veiculosDoGrupo(),
                                grupo.categoria(),
                                grupo.pacotes() == null
                                        ? List.<DadosDoPacote>of()
                                        : grupo.pacotes().stream()
                                                .map(p -> new DadosDoPacote(p.pacoteKm(), p.valorMensal()))
                                                .toList()))
                        .toList();

        List<DadosDoKmExcedente> excedentes = requisicao.kmExcedente() == null
                ? List.of()
                : requisicao.kmExcedente().stream()
                        .map(e -> new DadosDoKmExcedente(e.categoria(), e.pacoteKm(), e.valorKm()))
                        .toList();

        return new DadosDaTabela(
                requisicao.locadoraId(), requisicao.anoVigencia(), requisicao.observacoes(), grupos, excedentes);
    }
}
