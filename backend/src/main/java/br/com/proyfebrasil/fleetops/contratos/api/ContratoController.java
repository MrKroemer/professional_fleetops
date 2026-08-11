package br.com.proyfebrasil.fleetops.contratos.api;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.ContratoResumoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.EncerramentoRequest;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.EventoRequest;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.EventoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.ItemPendenteResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.LinhaDoTempoResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.SituacaoNaDataResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.SubstituicaoRequest;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.TrocaDeCondutorRequest;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.VerificacaoDeDevolucaoResponse;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDaLinhaDoTempo;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDeContratos;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDeContratos.FiltroDeContratos;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDoCicloDeVida;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDoCicloDeVida.DadosDoEvento;
import br.com.proyfebrasil.fleetops.contratos.domain.ItemDoBook;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.TipoDeEvento;
import br.com.proyfebrasil.fleetops.shared.arquivos.ServicoDeAnexos;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.pagination.PaginaResponse;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ciclo de vida do contrato de locação (Fase 2).
 *
 * <p>Leitura para todos os perfis; escrita apenas para quem opera a frota (RN-19). A
 * distinção não é formal: o perfil {@code CONSULTA} enxerga a linha do tempo inteira, o
 * que é o necessário para conferência, e não consegue registrar retirada nem devolução.
 */
@RestController
@RequestMapping("/api/v1/contratos")
@Tag(name = "Contratos", description = "Ciclo de vida do contrato: retirada, trocas e devolução")
public class ContratoController {

    private final ServicoDeContratos contratos;
    private final ServicoDoCicloDeVida cicloDeVida;
    private final ServicoDaLinhaDoTempo linhaDoTempo;
    private final ServicoDeVeiculos veiculos;
    private final ServicoDeCondutores condutores;
    private final ServicoDeAnexos anexos;
    private final CicloDeVidaMapper mapeador;

    public ContratoController(
            ServicoDeContratos contratos,
            ServicoDoCicloDeVida cicloDeVida,
            ServicoDaLinhaDoTempo linhaDoTempo,
            ServicoDeVeiculos veiculos,
            ServicoDeCondutores condutores,
            ServicoDeAnexos anexos,
            CicloDeVidaMapper mapeador) {
        this.contratos = contratos;
        this.cicloDeVida = cicloDeVida;
        this.linhaDoTempo = linhaDoTempo;
        this.veiculos = veiculos;
        this.condutores = condutores;
        this.anexos = anexos;
        this.mapeador = mapeador;
    }

    // ------------------------------------------------------------------ leitura

    @GetMapping
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Lista contratos com busca, filtros e paginação")
    public PaginaResponse<ContratoResumoResponse> listar(
            @Parameter(description = "Busca por código, obra, placa ou condutor")
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusContrato status,
            @RequestParam(required = false) Long obraId,
            @RequestParam(required = false) Long locadoraId,
            @ParameterObject @PageableDefault(size = 20, sort = "dataRetirada", direction = Sort.Direction.DESC)
                    Pageable paginacao) {
        return PaginaResponse.de(
                contratos.listar(new FiltroDeContratos(termo, status, obraId, locadoraId), paginacao),
                mapeador::paraResumo);
    }

    @GetMapping("/{id}/linha-do-tempo")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Linha do tempo do contrato: veículos, condutores e eventos em ordem")
    public LinhaDoTempoResponse linhaDoTempo(@PathVariable Long id) {
        return linhaDoTempo.montar(id);
    }

    @GetMapping("/{id}/situacao-em")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Quem dirigia o quê em uma data (RN-18)")
    public SituacaoNaDataResponse situacaoEm(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return linhaDoTempo.situacaoEm(id, data);
    }

    @GetMapping("/{id}/eventos")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Eventos de retirada e devolução do contrato")
    public List<EventoResponse> eventos(@PathVariable Long id) {
        return cicloDeVida.eventosDoContrato(id).stream().map(mapeador::paraResposta).toList();
    }

    @GetMapping("/book/itens")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Ângulos do book fotográfico, para a tela montar a grade (RN-12)")
    public List<ItemPendenteResponse> itensDoBook() {
        return mapeador.catalogoDoBook();
    }

    @GetMapping("/{id}/verificacao-de-devolucao")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "O que ainda impede a devolução à locadora (RN-17)")
    public VerificacaoDeDevolucaoResponse verificacaoDeDevolucao(@PathVariable Long id) {
        return mapeador.paraResposta(cicloDeVida.verificarDevolucao(id));
    }

    // ------------------------------------------------------------------ trocas

    @PostMapping("/{id}/substituicoes")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Substitui o veículo do contrato (RN-01)")
    public LinhaDoTempoResponse substituirVeiculo(
            @PathVariable Long id, @Valid @RequestBody SubstituicaoRequest requisicao) {
        contratos.substituirVeiculo(
                id, veiculos.buscar(requisicao.veiculoId()), requisicao.aPartirDe(), requisicao.motivo());
        return linhaDoTempo.montar(id);
    }

    @PostMapping("/{id}/trocas-de-condutor")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Troca o condutor do contrato (RN-18)")
    public LinhaDoTempoResponse trocarCondutor(
            @PathVariable Long id, @Valid @RequestBody TrocaDeCondutorRequest requisicao) {
        cicloDeVida.trocarCondutor(
                id, condutores.buscar(requisicao.condutorId()), requisicao.aPartirDe(), requisicao.motivo());
        return linhaDoTempo.montar(id);
    }

    // ------------------------------------------------------------------ eventos

    @PostMapping("/{id}/eventos")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre uma retirada ou devolução em preenchimento (RN-12)")
    public EventoResponse abrirEvento(@PathVariable Long id, @Valid @RequestBody EventoRequest requisicao) {
        return mapeador.paraResposta(cicloDeVida.abrirEvento(id, dadosDe(requisicao)));
    }

    @PutMapping("/eventos/{eventoId}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Atualiza os dados de um evento em preenchimento")
    public EventoResponse atualizarEvento(
            @PathVariable Long eventoId, @Valid @RequestBody EventoRequest requisicao) {
        return mapeador.paraResposta(cicloDeVida.atualizarEvento(eventoId, dadosDe(requisicao)));
    }

    /**
     * Envia uma foto do book.
     *
     * <p>Uma foto por requisição, e não o book inteiro de uma vez: são oito imagens tiradas
     * no pátio, pelo celular, e o envio unitário deixa cada uma sobreviver por conta
     * própria a uma oscilação de rede.
     */
    @PostMapping(value = "/eventos/{eventoId}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Envia uma foto do book fotográfico (RN-12)")
    public EventoResponse enviarFoto(
            @PathVariable Long eventoId,
            @RequestParam String item,
            @RequestParam(required = false) String observacao,
            @RequestPart("arquivo") MultipartFile arquivo) {
        var anexo = anexos.receber(arquivo);
        return mapeador.paraResposta(
                cicloDeVida.anexarFoto(eventoId, itemDoBook(item), anexo, observacao));
    }

    @DeleteMapping("/eventos/{eventoId}/fotos/{fotoId}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Remove uma foto do book")
    public EventoResponse removerFoto(@PathVariable Long eventoId, @PathVariable Long fotoId) {
        return mapeador.paraResposta(cicloDeVida.removerFoto(eventoId, fotoId));
    }

    @PostMapping(value = "/eventos/{eventoId}/crlv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Anexa o CRLV do veículo (RN-12)")
    public EventoResponse anexarCrlv(
            @PathVariable Long eventoId, @RequestPart("arquivo") MultipartFile arquivo) {
        return mapeador.paraResposta(cicloDeVida.anexarCrlv(eventoId, anexos.receber(arquivo)));
    }

    @PostMapping("/eventos/{eventoId}/aceite-das-regras")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Registra o aceite das regras de uso pelo condutor")
    public EventoResponse registrarAceite(@PathVariable Long eventoId) {
        return mapeador.paraResposta(cicloDeVida.registrarAceiteDasRegras(eventoId));
    }

    @PostMapping("/eventos/{eventoId}/conclusao")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Conclui o evento — exige book completo, CRLV e, na retirada, fumaça preta aprovada")
    public EventoResponse concluirEvento(@PathVariable Long eventoId) {
        return mapeador.paraResposta(cicloDeVida.concluirEvento(eventoId));
    }

    @PostMapping("/eventos/{eventoId}/reabertura")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Reabre um evento concluído para correção")
    public EventoResponse reabrirEvento(@PathVariable Long eventoId) {
        return mapeador.paraResposta(cicloDeVida.reabrirEvento(eventoId));
    }

    // ------------------------------------------------------------------ encerramento

    @PostMapping("/{id}/encerramento")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Encerra o contrato: desmobilização ou devolução à locadora (RN-17)")
    public LinhaDoTempoResponse encerrar(
            @PathVariable Long id, @Valid @RequestBody EncerramentoRequest requisicao) {
        cicloDeVida.encerrar(id, statusDe(requisicao.status()), requisicao.dataDeEncerramento());
        return linhaDoTempo.montar(id);
    }

    // ------------------------------------------------------------------

    private DadosDoEvento dadosDe(EventoRequest requisicao) {
        return new DadosDoEvento(
                tipoDeEvento(requisicao.tipo()),
                veiculos.buscar(requisicao.veiculoId()),
                requisicao.condutorId() == null ? null : condutores.buscar(requisicao.condutorId()),
                requisicao.dataDoEvento(),
                requisicao.km(),
                requisicao.localDoEvento(),
                requisicao.checklistDaLocadora(),
                requisicao.observacoes());
    }

    /**
     * Converte texto em enum devolvendo 400, e não 500.
     *
     * <p>Um valor desconhecido é erro de quem chamou; {@code valueOf} sozinho lançaria
     * {@link IllegalArgumentException}, que o manipulador global classificaria como falha
     * interna. A mensagem lista os valores aceitos, porque quem integra precisa deles.
     */
    private static TipoDeEvento tipoDeEvento(String valor) {
        try {
            return TipoDeEvento.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException erro) {
            throw new NegocioException(
                    ErroComum.VALIDACAO,
                    "Tipo de evento inválido: %s. Aceitos: RETIRADA, DEVOLUCAO.".formatted(valor));
        }
    }

    private static ItemDoBook itemDoBook(String valor) {
        try {
            return ItemDoBook.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException erro) {
            throw new NegocioException(
                    ErroComum.VALIDACAO,
                    "Ângulo do book inválido: %s. Aceitos: %s."
                            .formatted(valor, String.join(", ",
                                    List.of(ItemDoBook.values()).stream().map(Enum::name).toList())));
        }
    }

    private static StatusContrato statusDe(String valor) {
        StatusContrato status;
        try {
            status = StatusContrato.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException erro) {
            throw new NegocioException(
                    ErroComum.VALIDACAO,
                    "Status inválido: %s. Aceitos: DESMOBILIZADO, DEVOLVIDO, INATIVO.".formatted(valor));
        }
        if (status == StatusContrato.ATIVO) {
            throw new NegocioException(
                    ErroComum.VALIDACAO, "Encerrar exige um status diferente de ATIVO.");
        }
        return status;
    }
}
