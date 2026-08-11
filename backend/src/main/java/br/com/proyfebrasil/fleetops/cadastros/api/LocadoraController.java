package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.CanaisDeAtendimentoDto;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.CredencialReveladaResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.LocadoraRequest;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.LocadoraResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeLocadoras;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeLocadoras.CredencialRevelada;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeLocadoras.DadosDaLocadora;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeLocadoras.FiltroLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.CanaisDeAtendimento;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.shared.pagination.PaginaResponse;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
import io.swagger.v3.oas.annotations.Operation;
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
 * Cadastro de locadoras.
 *
 * <p>A revelação de credenciais tem endpoint próprio e permissão mais restrita que a
 * leitura do cadastro: o perfil {@code CONSULTA} enxerga a locadora, mas nunca o segredo
 * do portal (RN-19 e RN-20).
 */
@RestController
@RequestMapping("/api/v1/locadoras")
@Tag(name = "Locadoras", description = "Cadastro das locadoras parceiras e seus canais de atendimento")
@SecurityRequirement(name = "bearerAuth")
public class LocadoraController {

    private final ServicoDeLocadoras servico;
    private final CadastroMapper mapper;

    public LocadoraController(ServicoDeLocadoras servico, CadastroMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Lista locadoras com busca, filtros e paginação")
    @ApiResponse(responseCode = "200", description = "Página de locadoras")
    public PaginaResponse<LocadoraResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) TipoLocadora tipo,
            @RequestParam(required = false) Boolean ativa,
            @ParameterObject @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroLocadora(termo, tipo, ativa), paginacao), mapper::paraResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Detalha uma locadora")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Locadora encontrada"),
        @ApiResponse(responseCode = "404", description = "Locadora inexistente"),
    })
    public LocadoraResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.buscar(id));
    }

    @GetMapping("/{id}/credenciais")
    @PreAuthorize(Autorizacoes.CREDENCIAIS)
    @Operation(
            summary = "Revela as credenciais do portal",
            description = "Operação auditada: o acesso é registrado em log com o usuário solicitante (RN-20).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credenciais reveladas"),
        @ApiResponse(responseCode = "403", description = "Perfil sem permissão para ver credenciais"),
        @ApiResponse(responseCode = "404", description = "Locadora inexistente ou sem credenciais cadastradas"),
    })
    public CredencialReveladaResponse revelarCredenciais(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        CredencialRevelada credencial = servico.revelarCredenciais(id, jwt.getClaimAsString("email"));
        return new CredencialReveladaResponse(credencial.login(), credencial.senha());
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Cadastra uma locadora")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Locadora criada"),
        @ApiResponse(responseCode = "409", description = "Nome já cadastrado"),
    })
    public ResponseEntity<LocadoraResponse> criar(@Valid @RequestBody LocadoraRequest requisicao) {
        LocadoraResponse criada = mapper.paraResponse(servico.criar(paraDados(requisicao)));
        return ResponseEntity.created(URI.create("/api/v1/locadoras/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(
            summary = "Atualiza uma locadora",
            description = "Omitir login e senha preserva as credenciais atuais; enviar vazio as remove.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Locadora atualizada"),
        @ApiResponse(responseCode = "404", description = "Locadora inexistente"),
        @ApiResponse(responseCode = "409", description = "Nome já cadastrado"),
    })
    public LocadoraResponse atualizar(@PathVariable Long id, @Valid @RequestBody LocadoraRequest requisicao) {
        return mapper.paraResponse(servico.atualizar(id, paraDados(requisicao)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Exclui logicamente uma locadora e descarta suas credenciais")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Locadora excluída"),
        @ApiResponse(responseCode = "404", description = "Locadora inexistente"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private DadosDaLocadora paraDados(LocadoraRequest requisicao) {
        return new DadosDaLocadora(
                requisicao.nome(),
                requisicao.tipo(),
                requisicao.consultor(),
                requisicao.telefone(),
                requisicao.email(),
                requisicao.portalUrl(),
                requisicao.portalLogin(),
                requisicao.portalSenha(),
                paraCanais(requisicao.canais()),
                requisicao.observacoes(),
                requisicao.ativa());
    }

    private CanaisDeAtendimento paraCanais(CanaisDeAtendimentoDto dto) {
        if (dto == null) {
            return new CanaisDeAtendimento();
        }
        return new CanaisDeAtendimento(
                dto.reservas(),
                dto.manutencao(),
                dto.guinchoSinistro(),
                dto.assistencia24h(),
                dto.financeiro(),
                dto.suporte(),
                dto.telemetria());
    }
}
