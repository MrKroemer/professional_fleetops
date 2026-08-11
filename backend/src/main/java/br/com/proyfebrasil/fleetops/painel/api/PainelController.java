package br.com.proyfebrasil.fleetops.painel.api;

import br.com.proyfebrasil.fleetops.cadastros.api.CadastroMapper;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.AnalisesResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.BuscaGlobalResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.ComparacaoDeVigenciasResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.CurvaDeLocadoraResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.GradeDoVeiculoResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.LinhaDaMatrizResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.MatrizDaFrotaResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.OpcaoDePacoteResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.PainelDoVeiculoResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.PontoDaCurvaResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.ReajusteDoGrupoResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.ResultadoDaBuscaResponse;
import br.com.proyfebrasil.fleetops.painel.api.AnaliseDtos.VeiculoEmOperacaoResponse;
import br.com.proyfebrasil.fleetops.painel.api.PainelDtos.CentralDePendenciasResponse;
import br.com.proyfebrasil.fleetops.painel.api.PainelDtos.CustoDaLocadoraResponse;
import br.com.proyfebrasil.fleetops.painel.api.PainelDtos.FatiaResponse;
import br.com.proyfebrasil.fleetops.painel.api.PainelDtos.IndicadoresResponse;
import br.com.proyfebrasil.fleetops.painel.api.PainelDtos.PendenciaResponse;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDaCentralDePendencias;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDeAnalises;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDeBuscaGlobal;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDeVeiculosEmOperacao;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDoPainel;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDoPainelDoVeiculo;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDoPainel.Fatia;
import br.com.proyfebrasil.fleetops.painel.application.ServicoDoPainel.Indicadores;
import br.com.proyfebrasil.fleetops.painel.domain.Pendencia;
import br.com.proyfebrasil.fleetops.painel.domain.Severidade;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Painel inicial: indicadores consolidados e central de pendências (RN-23). */
@RestController
@RequestMapping("/api/v1/painel")
@Tag(name = "Painel", description = "Indicadores da frota e central de pendências")
@SecurityRequirement(name = "bearerAuth")
public class PainelController {

    private final ServicoDoPainel painel;
    private final ServicoDaCentralDePendencias central;
    private final ServicoDeAnalises analises;
    private final ServicoDoPainelDoVeiculo painelDoVeiculo;
    private final ServicoDeVeiculosEmOperacao emOperacao;
    private final ServicoDeBuscaGlobal buscaGlobal;
    private final CadastroMapper mapper;
    private final Clock relogio;

    public PainelController(
            ServicoDoPainel painel,
            ServicoDaCentralDePendencias central,
            ServicoDeAnalises analises,
            ServicoDoPainelDoVeiculo painelDoVeiculo,
            ServicoDeVeiculosEmOperacao emOperacao,
            ServicoDeBuscaGlobal buscaGlobal,
            CadastroMapper mapper,
            Clock relogio) {
        this.painel = painel;
        this.central = central;
        this.analises = analises;
        this.painelDoVeiculo = painelDoVeiculo;
        this.emOperacao = emOperacao;
        this.buscaGlobal = buscaGlobal;
        this.mapper = mapper;
        this.relogio = relogio;
    }

    @GetMapping("/indicadores")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Indicadores consolidados da frota",
            description = "Todos derivam de agregações sobre os cadastros. O custo mensal é uma "
                    + "estimativa de referência, não uma fatura.")
    @ApiResponse(responseCode = "200", description = "Indicadores apurados")
    public IndicadoresResponse indicadores() {
        Indicadores dados = painel.apurar();
        return new IndicadoresResponse(
                dados.veiculosNaFrota(),
                dados.veiculosEmUso(),
                dados.veiculosADiesel(),
                dados.veiculosComRastreador(),
                dados.obras(),
                dados.obrasAtivas(),
                dados.condutores(),
                dados.condutoresAtivos(),
                dados.fornecedoresAtivos(),
                dados.anoDeReferencia(),
                dados.custoMensalEstimado(),
                paraFatias(dados.veiculosPorCategoria()),
                paraFatias(dados.veiculosPorLocadora()),
                paraFatias(dados.veiculosPorStatus()),
                paraFatias(dados.obrasPorUf()),
                paraFatias(dados.fornecedoresPorTipo()),
                dados.custoPorLocadora().stream()
                        .map(custo -> new CustoDaLocadoraResponse(
                                custo.locadora(), custo.veiculos(), custo.custoMensalEstimado()))
                        .toList());
    }

    @GetMapping("/pendencias")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Central de pendências",
            description = "Lacunas reais dos cadastros, ordenadas por severidade (RN-23).")
    @ApiResponse(responseCode = "200", description = "Pendências apuradas")
    public CentralDePendenciasResponse pendencias() {
        List<Pendencia> itens = central.apurar();
        Map<Severidade, Long> contagem = central.contarPorSeveridade();
        return new CentralDePendenciasResponse(
                contagem.getOrDefault(Severidade.CRITICA, 0L),
                contagem.getOrDefault(Severidade.ATENCAO, 0L),
                contagem.getOrDefault(Severidade.INFORMATIVA, 0L),
                itens.stream()
                        .map(item -> new PendenciaResponse(
                                item.tipo(),
                                item.tipo().getDescricao(),
                                item.tipo().getRegra(),
                                item.severidade(),
                                item.severidade().getDescricao(),
                                item.titulo(),
                                item.detalhe(),
                                item.recurso(),
                                item.referencia()))
                        .toList());
    }

    @GetMapping("/analises")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Cruzamentos analíticos da frota e dos preços",
            description = "Frota por categoria e locadora, curva de preço por pacote de KM e "
                    + "variação entre as duas vigências mais recentes. Tudo derivado dos cadastros.")
    @ApiResponse(responseCode = "200", description = "Análises apuradas")
    public AnalisesResponse analises() {
        int ano = LocalDate.now(relogio).getYear();
        var matriz = analises.matrizDaFrota();
        var vigencias = analises.compararVigencias();

        return new AnalisesResponse(
                ano,
                new MatrizDaFrotaResponse(
                        matriz.locadoras(),
                        matriz.linhas().stream()
                                .map(linha -> new LinhaDaMatrizResponse(
                                        linha.chave(), linha.rotulo(), linha.total(), linha.porLocadora()))
                                .toList()),
                analises.curvasDePreco(ano).stream()
                        .map(curva -> new CurvaDeLocadoraResponse(
                                curva.locadora(),
                                curva.pontos().stream()
                                        .map(ponto -> new PontoDaCurvaResponse(
                                                ponto.pacoteKm(), ponto.valorMedio()))
                                        .toList()))
                        .toList(),
                new ComparacaoDeVigenciasResponse(
                        vigencias.anoAnterior(),
                        vigencias.anoAtual(),
                        vigencias.reajustes().stream()
                                .map(reajuste -> new ReajusteDoGrupoResponse(
                                        reajuste.locadora(),
                                        reajuste.grupo(),
                                        reajuste.categoria(),
                                        reajuste.categoriaDescricao(),
                                        reajuste.pacoteKm(),
                                        reajuste.valorAnterior(),
                                        reajuste.valorAtual(),
                                        reajuste.variacaoPercentual()))
                                .toList(),
                        vigencias.variacaoMedia()));
    }

    @GetMapping("/busca")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Busca global por placa, condutor e obra",
            description = "As três formas pelas quais o gestor se refere a um veículo. Termos com "
                    + "menos de dois caracteres devolvem lista vazia, sem consultar o banco.")
    @ApiResponse(responseCode = "200", description = "Resultados agrupados por tipo")
    public BuscaGlobalResponse busca(@RequestParam String termo) {
        var resultados = buscaGlobal.buscar(termo);
        return new BuscaGlobalResponse(
                paraResultados(resultados.veiculos()),
                paraResultados(resultados.condutores()),
                paraResultados(resultados.obras()),
                resultados.total());
    }

    @GetMapping("/veiculos-em-operacao")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Veículos com contrato ativo",
            description = "Cada item traz o veículo, a obra e o condutor, com a situação da CNH "
                    + "já avaliada (RN-16). Alimenta os cards do painel.")
    @ApiResponse(responseCode = "200", description = "Veículos em operação")
    public List<VeiculoEmOperacaoResponse> veiculosEmOperacao() {
        return emOperacao.listar().stream()
                .map(item -> new VeiculoEmOperacaoResponse(
                        item.veiculoId(),
                        item.placa(),
                        item.placaFormatada(),
                        item.modelo(),
                        item.categoria(),
                        item.categoriaDescricao(),
                        item.exigeTesteFumacaPreta(),
                        item.possuiRastreador(),
                        item.possuiAdesivo(),
                        item.locadora(),
                        item.grupoTarifario(),
                        item.contratoId(),
                        item.obraCodigo(),
                        item.obraNome(),
                        item.localRetirada(),
                        item.pacoteKmContratado(),
                        item.dataRetirada(),
                        item.condutorId(),
                        item.condutorNome(),
                        item.condutorCargo(),
                        item.cnhValidade(),
                        item.diasParaVencerCnh(),
                        item.cnhVencida(),
                        item.cnhEmAlerta()))
                .toList();
    }

    @GetMapping("/veiculos/{id}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(
            summary = "Painel de um veículo",
            description = "O cadastro do veículo cruzado com a grade tarifária que se aplica a ele: "
                    + "valor mensal por pacote de KM e custo do KM excedente (RN-06).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Painel apurado"),
        @ApiResponse(responseCode = "404", description = "Veículo inexistente"),
    })
    public PainelDoVeiculoResponse painelDoVeiculo(@PathVariable Long id) {
        var dados = painelDoVeiculo.apurar(id);
        return new PainelDoVeiculoResponse(
                mapper.paraResponse(dados.veiculo()),
                dados.grade()
                        .map(grade -> new GradeDoVeiculoResponse(
                                grade.anoVigencia(),
                                grade.grupo(),
                                grade.veiculosDoGrupo(),
                                grade.pacotes().stream()
                                        .map(pacote -> new OpcaoDePacoteResponse(
                                                pacote.pacoteKm(),
                                                pacote.valorMensal(),
                                                pacote.valorKmExcedente()))
                                        .toList(),
                                grade.menorValorMensal()))
                        .orElse(null),
                dados.motivoSemGrade());
    }

    private static List<ResultadoDaBuscaResponse> paraResultados(
            List<ServicoDeBuscaGlobal.Resultado> resultados) {
        return resultados.stream()
                .map(item -> new ResultadoDaBuscaResponse(
                        item.tipo(), item.id(), item.rotulo(), item.detalhe(), item.rota()))
                .toList();
    }

    private static List<FatiaResponse> paraFatias(List<Fatia> fatias) {
        return fatias.stream()
                .map(fatia -> new FatiaResponse(fatia.chave(), fatia.rotulo(), fatia.quantidade()))
                .toList();
    }
}
