package br.com.proyfebrasil.fleetops.cadastros.api;

import br.com.proyfebrasil.fleetops.cadastros.api.dto.CredencialReveladaResponse;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.FornecedorRequest;
import br.com.proyfebrasil.fleetops.cadastros.api.dto.FornecedorResponse;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.CredencialRevelada;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.DadosDeGrafica;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.DadosDeLavaJato;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.DadosDePosto;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.DadosDeRastreador;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.DadosDoFornecedor;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores.FiltroFornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
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
import java.util.Set;
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

/** Cadastro de fornecedores credenciados, com os campos próprios de cada tipo. */
@RestController
@RequestMapping("/api/v1/fornecedores")
@Tag(name = "Fornecedores", description = "Postos, lava-jatos, borracharias, para-brisas, rastreadores, gráficas e oficinas")
@SecurityRequirement(name = "bearerAuth")
public class FornecedorController {

    private final ServicoDeFornecedores servico;
    private final FornecedorMapper mapper;

    public FornecedorController(ServicoDeFornecedores servico, FornecedorMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Lista fornecedores com busca, filtros e paginação")
    @ApiResponse(responseCode = "200", description = "Página de fornecedores")
    public PaginaResponse<FornecedorResponse> listar(
            @Parameter(description = "Busca por nome, cidade ou responsável")
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) TipoFornecedor tipo,
            @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Filtra pelos fornecedores que atendem esta obra")
            @RequestParam(required = false) Long obraId,
            @ParameterObject @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                servico.listar(new FiltroFornecedor(termo, tipo, ativo, obraId), paginacao),
                mapper::paraResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Detalha um fornecedor, com os dados específicos do seu tipo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fornecedor encontrado"),
        @ApiResponse(responseCode = "404", description = "Fornecedor inexistente"),
    })
    public FornecedorResponse detalhar(@PathVariable Long id) {
        return mapper.paraResponse(servico.buscar(id));
    }

    @GetMapping("/{id}/credenciais")
    @PreAuthorize(Autorizacoes.CREDENCIAIS)
    @Operation(
            summary = "Revela as credenciais do portal de rastreamento",
            description = "Operação auditada: o acesso é registrado em log com o usuário solicitante (RN-20).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credenciais reveladas"),
        @ApiResponse(responseCode = "403", description = "Perfil sem permissão para ver credenciais"),
        @ApiResponse(responseCode = "404", description = "Fornecedor inexistente ou sem credenciais cadastradas"),
    })
    public CredencialReveladaResponse revelarCredenciais(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        CredencialRevelada credencial = servico.revelarCredenciais(id, jwt.getClaimAsString("email"));
        return new CredencialReveladaResponse(credencial.login(), credencial.senha());
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(
            summary = "Cadastra um fornecedor",
            description = "Envie apenas o bloco de dados correspondente ao tipo informado.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Fornecedor criado"),
        @ApiResponse(responseCode = "400", description = "Dados incompatíveis com o tipo"),
        @ApiResponse(responseCode = "409", description = "Nome já cadastrado para este tipo"),
    })
    public ResponseEntity<FornecedorResponse> criar(@Valid @RequestBody FornecedorRequest requisicao) {
        Long id = servico.criar(paraDados(requisicao)).getId();
        FornecedorResponse criado = mapper.paraResponse(servico.buscar(id));
        return ResponseEntity.created(URI.create("/api/v1/fornecedores/" + id)).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(
            summary = "Atualiza um fornecedor",
            description = "O tipo não pode ser alterado. Omitir credenciais preserva as atuais.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fornecedor atualizado"),
        @ApiResponse(responseCode = "400", description = "Dados incompatíveis com o tipo"),
        @ApiResponse(responseCode = "404", description = "Fornecedor inexistente"),
        @ApiResponse(responseCode = "409", description = "Nome já cadastrado para este tipo"),
    })
    public FornecedorResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody FornecedorRequest requisicao) {
        servico.atualizar(id, paraDados(requisicao));
        return mapper.paraResponse(servico.buscar(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Exclui logicamente um fornecedor e descarta suas credenciais")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Fornecedor excluído"),
        @ApiResponse(responseCode = "404", description = "Fornecedor inexistente"),
    })
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private DadosDoFornecedor paraDados(FornecedorRequest requisicao) {
        return new DadosDoFornecedor(
                requisicao.tipo(),
                requisicao.nome(),
                requisicao.cidade(),
                requisicao.uf(),
                requisicao.endereco(),
                requisicao.telefone(),
                requisicao.email(),
                requisicao.responsavel(),
                requisicao.funcionamento(),
                requisicao.formaFaturamento(),
                requisicao.formaPagamento(),
                requisicao.credenciadoEm(),
                requisicao.ativo(),
                requisicao.observacoes(),
                requisicao.obrasIds(),
                paraPosto(requisicao.posto()),
                paraLavaJato(requisicao.lavaJato()),
                paraRastreador(requisicao.rastreador()),
                paraGrafica(requisicao.grafica()));
    }

    private DadosDePosto paraPosto(FornecedorRequest.DadosDePostoDto dto) {
        if (dto == null) {
            return null;
        }
        return new DadosDePosto(
                dto.diasAutorizados() == null ? Set.of() : dto.diasAutorizados(), dto.acessoFaturas());
    }

    private DadosDeLavaJato paraLavaJato(FornecedorRequest.DadosDeLavaJatoDto dto) {
        if (dto == null) {
            return null;
        }
        return new DadosDeLavaJato(
                dto.servicosPorSemana(), dto.precoPasseio(), dto.precoSuv(), dto.precoQuatroXQuatro());
    }

    private DadosDeRastreador paraRastreador(FornecedorRequest.DadosDeRastreadorDto dto) {
        if (dto == null) {
            return null;
        }
        return new DadosDeRastreador(
                dto.mensalidade(),
                dto.custoInstalacao(),
                dto.custoDesinstalacao(),
                dto.equipadora(),
                dto.portalUrl(),
                dto.portalLogin(),
                dto.portalSenha());
    }

    private DadosDeGrafica paraGrafica(FornecedorRequest.DadosDeGraficaDto dto) {
        if (dto == null) {
            return null;
        }
        return new DadosDeGrafica(
                dto.tamanhoAdesivo(), dto.precoAdesivo(), dto.tamanhoIma(), dto.precoIma());
    }
}
