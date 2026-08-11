package br.com.proyfebrasil.fleetops.operacao.api;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeFornecedores;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.AbastecimentoRequest;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.AbastecimentoResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.ConferenciaRequest;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.ConformidadeResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.FaturaRequest;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.FaturaResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.FechamentoResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.RegistroDeKmRequest;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.RegistroDeKmResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.ServicoRequest;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.ServicoResponse;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.UsoParticularRequest;
import br.com.proyfebrasil.fleetops.operacao.api.dto.OperacaoDtos.UsoParticularResponse;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas.DadosDaFatura;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas.DadosDoUsoParticular;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFechamentoMensal;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeAbastecimento;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeKm;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeServico;
import br.com.proyfebrasil.fleetops.operacao.domain.StatusDeConferencia;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeUsoParticular;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operação mensal: quilometragem, abastecimentos, serviços, fechamento, faturas e uso
 * particular (Fase 3).
 *
 * <p>Leitura para todos os perfis; escrita para quem opera a frota (RN-19). O perfil de
 * consulta precisa ver o fechamento — é a conferência que ele faz — mas não lança.
 */
@RestController
@RequestMapping("/api/v1/operacao")
@Tag(name = "Operação mensal", description = "KM, abastecimentos, serviços, fechamento e faturas")
public class OperacaoController {

    private final ServicoDeLancamentos lancamentos;
    private final ServicoDeFechamentoMensal fechamentos;
    private final ServicoDeFaturas faturas;
    private final ServicoDeCondutores condutores;
    private final ServicoDeFornecedores fornecedores;
    private final OperacaoMapper mapeador;
    private final Clock relogio;

    public OperacaoController(
            ServicoDeLancamentos lancamentos,
            ServicoDeFechamentoMensal fechamentos,
            ServicoDeFaturas faturas,
            ServicoDeCondutores condutores,
            ServicoDeFornecedores fornecedores,
            OperacaoMapper mapeador,
            Clock relogio) {
        this.lancamentos = lancamentos;
        this.fechamentos = fechamentos;
        this.faturas = faturas;
        this.condutores = condutores;
        this.fornecedores = fornecedores;
        this.mapeador = mapeador;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ quilometragem

    @GetMapping("/contratos/{contratoId}/km")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Registros de quilometragem do período")
    public List<RegistroDeKmResponse> listarKm(
            @PathVariable Long contratoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return lancamentos.kmDoPeriodo(contratoId, inicio, fim).stream()
                .map(mapeador::paraResposta).toList();
    }

    @PostMapping("/contratos/{contratoId}/km")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lança um registro de quilometragem (RN-03)")
    public RegistroDeKmResponse lancarKm(
            @PathVariable Long contratoId, @Valid @RequestBody RegistroDeKmRequest requisicao) {
        return mapeador.paraResposta(lancamentos.lancarKm(contratoId, dadosDeKm(requisicao)));
    }

    @PutMapping("/km/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Corrige um registro de quilometragem")
    public RegistroDeKmResponse atualizarKm(
            @PathVariable Long id, @Valid @RequestBody RegistroDeKmRequest requisicao) {
        return mapeador.paraResposta(lancamentos.atualizarKm(id, dadosDeKm(requisicao)));
    }

    @DeleteMapping("/km/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui logicamente um registro de quilometragem")
    public void excluirKm(@PathVariable Long id) {
        lancamentos.excluirKm(id, relogio.instant());
    }

    // ------------------------------------------------------------------ abastecimentos

    @GetMapping("/contratos/{contratoId}/abastecimentos")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Abastecimentos do período")
    public List<AbastecimentoResponse> listarAbastecimentos(
            @PathVariable Long contratoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return lancamentos.abastecimentosDoPeriodo(contratoId, inicio, fim).stream()
                .map(mapeador::paraResposta).toList();
    }

    /**
     * Avalia a conformidade antes do envio (RN-04).
     *
     * <p>Existe para que a tela avise ao escolher posto e data, em vez de o usuário
     * descobrir no erro que aquele dia não é autorizado. O backend reavalia na gravação:
     * isto informa, não decide.
     */
    @GetMapping("/contratos/{contratoId}/abastecimentos/conformidade")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Verifica se um abastecimento cabe nas condições autorizadas (RN-04)")
    public ConformidadeResponse avaliarConformidade(
            @PathVariable Long contratoId,
            @RequestParam(required = false) Long postoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        var posto = postoId == null ? null : fornecedores.buscar(postoId);
        return mapeador.paraResposta(lancamentos.avaliarAbastecimento(contratoId, posto, data));
    }

    @PostMapping("/contratos/{contratoId}/abastecimentos")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lança um abastecimento (RN-04)")
    public AbastecimentoResponse lancarAbastecimento(
            @PathVariable Long contratoId, @Valid @RequestBody AbastecimentoRequest requisicao) {
        return mapeador.paraResposta(
                lancamentos.lancarAbastecimento(contratoId, dadosDeAbastecimento(requisicao)));
    }

    @PutMapping("/abastecimentos/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Corrige um abastecimento")
    public AbastecimentoResponse atualizarAbastecimento(
            @PathVariable Long id, @Valid @RequestBody AbastecimentoRequest requisicao) {
        return mapeador.paraResposta(
                lancamentos.atualizarAbastecimento(id, dadosDeAbastecimento(requisicao)));
    }

    @DeleteMapping("/abastecimentos/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui logicamente um abastecimento")
    public void excluirAbastecimento(@PathVariable Long id) {
        lancamentos.excluirAbastecimento(id, relogio.instant());
    }

    // ------------------------------------------------------------------ serviços

    @GetMapping("/contratos/{contratoId}/servicos")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Serviços do período")
    public List<ServicoResponse> listarServicos(
            @PathVariable Long contratoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return lancamentos.servicosDoPeriodo(contratoId, inicio, fim).stream()
                .map(mapeador::paraResposta).toList();
    }

    @PostMapping("/contratos/{contratoId}/servicos")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lança um serviço de lava-jato, borracharia ou para-brisas (RN-05)")
    public ServicoResponse lancarServico(
            @PathVariable Long contratoId, @Valid @RequestBody ServicoRequest requisicao) {
        return mapeador.paraResposta(lancamentos.lancarServico(contratoId, dadosDeServico(requisicao)));
    }

    @PutMapping("/servicos/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Corrige um serviço")
    public ServicoResponse atualizarServico(
            @PathVariable Long id, @Valid @RequestBody ServicoRequest requisicao) {
        return mapeador.paraResposta(lancamentos.atualizarServico(id, dadosDeServico(requisicao)));
    }

    @DeleteMapping("/servicos/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui logicamente um serviço")
    public void excluirServico(@PathVariable Long id) {
        lancamentos.excluirServico(id, relogio.instant());
    }

    // ------------------------------------------------------------------ fechamento

    @GetMapping("/contratos/{contratoId}/fechamento")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Fechamento mensal, calculado a partir dos lançamentos (RN-06, RN-21)")
    public FechamentoResponse fechamento(
            @PathVariable Long contratoId, @RequestParam String competencia) {
        return mapeador.paraResposta(fechamentos.apurar(contratoId, competenciaDe(competencia)));
    }

    @GetMapping("/excedentes")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Contratos que estouraram a franquia de KM na competência (RN-06)")
    public List<FechamentoResponse> excedentes(@RequestParam String competencia) {
        return fechamentos.excedentesDaCompetencia(competenciaDe(competencia)).stream()
                .map(mapeador::paraResposta).toList();
    }

    @PostMapping("/contratos/{contratoId}/fechamento/conferencia")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Marca a competência como conferida")
    public FechamentoResponse conferir(
            @PathVariable Long contratoId,
            @RequestParam String competencia,
            @RequestBody(required = false) ConferenciaRequest requisicao,
            @AuthenticationPrincipal Jwt usuario) {
        YearMonth mes = competenciaDe(competencia);
        fechamentos.conferir(
                contratoId, mes,
                usuario == null ? null : usuario.getSubject(),
                requisicao == null ? null : requisicao.observacoes());
        return mapeador.paraResposta(fechamentos.apurar(contratoId, mes));
    }

    @PostMapping("/contratos/{contratoId}/fechamento/reabertura")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Reabre a competência para novos lançamentos")
    public FechamentoResponse reabrir(
            @PathVariable Long contratoId, @RequestParam String competencia) {
        YearMonth mes = competenciaDe(competencia);
        fechamentos.reabrir(contratoId, mes);
        return mapeador.paraResposta(fechamentos.apurar(contratoId, mes));
    }

    // ------------------------------------------------------------------ faturas

    @GetMapping("/contratos/{contratoId}/faturas")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Faturas do contrato")
    public List<FaturaResponse> listarFaturas(@PathVariable Long contratoId) {
        return faturas.doContrato(contratoId).stream().map(mapeador::paraResposta).toList();
    }

    @GetMapping("/faturas/divergentes")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Faturas com divergência ainda sem tratativa concluída (RN-13)")
    public List<FaturaResponse> faturasDivergentes() {
        return faturas.comDivergenciaEmAberto().stream().map(mapeador::paraResposta).toList();
    }

    @PostMapping("/contratos/{contratoId}/faturas")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lança a fatura de uma competência (RN-13)")
    public FaturaResponse lancarFatura(
            @PathVariable Long contratoId, @Valid @RequestBody FaturaRequest requisicao) {
        return mapeador.paraResposta(faturas.lancar(contratoId, dadosDaFatura(requisicao)));
    }

    @PutMapping("/faturas/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Atualiza a fatura e sua conferência (RN-13)")
    public FaturaResponse atualizarFatura(
            @PathVariable Long id, @Valid @RequestBody FaturaRequest requisicao) {
        return mapeador.paraResposta(faturas.atualizar(id, dadosDaFatura(requisicao)));
    }

    @DeleteMapping("/faturas/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui logicamente uma fatura")
    public void excluirFatura(@PathVariable Long id) {
        faturas.excluirFatura(id);
    }

    // ------------------------------------------------------------------ uso particular

    @GetMapping("/contratos/{contratoId}/uso-particular")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Autorizações de uso particular do contrato (RN-10)")
    public List<UsoParticularResponse> listarUsoParticular(@PathVariable Long contratoId) {
        return faturas.usosDoContrato(contratoId).stream().map(mapeador::paraResposta).toList();
    }

    @PostMapping("/contratos/{contratoId}/uso-particular")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Autoriza um uso particular (RN-10)")
    public UsoParticularResponse autorizarUsoParticular(
            @PathVariable Long contratoId, @Valid @RequestBody UsoParticularRequest requisicao) {
        return mapeador.paraResposta(
                faturas.autorizarUsoParticular(contratoId, dadosDoUso(requisicao)));
    }

    @PutMapping("/uso-particular/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @Operation(summary = "Atualiza uma autorização de uso particular")
    public UsoParticularResponse atualizarUsoParticular(
            @PathVariable Long id, @Valid @RequestBody UsoParticularRequest requisicao) {
        return mapeador.paraResposta(faturas.atualizarUsoParticular(id, dadosDoUso(requisicao)));
    }

    @DeleteMapping("/uso-particular/{id}")
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui logicamente uma autorização de uso particular")
    public void excluirUsoParticular(@PathVariable Long id) {
        faturas.excluirUsoParticular(id);
    }

    // ------------------------------------------------------------------

    private DadosDeKm dadosDeKm(RegistroDeKmRequest requisicao) {
        return new DadosDeKm(
                requisicao.condutorId() == null ? null : condutores.buscar(requisicao.condutorId()),
                requisicao.data(),
                requisicao.kmInicial(),
                requisicao.kmFinal(),
                requisicao.origem(),
                requisicao.destino(),
                requisicao.observacao());
    }

    private DadosDeAbastecimento dadosDeAbastecimento(AbastecimentoRequest requisicao) {
        return new DadosDeAbastecimento(
                requisicao.postoId() == null ? null : fornecedores.buscar(requisicao.postoId()),
                requisicao.data(),
                requisicao.valor(),
                requisicao.litros(),
                requisicao.km(),
                requisicao.observacao(),
                requisicao.justificativa());
    }

    private DadosDeServico dadosDeServico(ServicoRequest requisicao) {
        return new DadosDeServico(
                enumDe(TipoDeServico::valueOf, requisicao.tipo(), "tipo de serviço", TipoDeServico.values()),
                requisicao.fornecedorId() == null ? null : fornecedores.buscar(requisicao.fornecedorId()),
                requisicao.data(),
                requisicao.valor(),
                requisicao.descricao(),
                requisicao.justificativa());
    }

    private DadosDaFatura dadosDaFatura(FaturaRequest requisicao) {
        return new DadosDaFatura(
                competenciaDe(requisicao.competencia()),
                requisicao.valorContratado(),
                requisicao.valorFaturado(),
                requisicao.extrasAprovados(),
                requisicao.numeroDaNota(),
                requisicao.vencimento(),
                requisicao.status() == null
                        ? null
                        : enumDe(StatusDeConferencia::valueOf, requisicao.status(),
                                "status de conferência", StatusDeConferencia.values()),
                requisicao.observacoes());
    }

    private DadosDoUsoParticular dadosDoUso(UsoParticularRequest requisicao) {
        return new DadosDoUsoParticular(
                condutores.buscar(requisicao.condutorId()),
                enumDe(TipoDeUsoParticular::valueOf, requisicao.tipo(),
                        "tipo de uso particular", TipoDeUsoParticular.values()),
                requisicao.inicio(),
                requisicao.fim(),
                requisicao.kmAutorizado(),
                requisicao.kmPercorrido(),
                requisicao.aceitarRegras(),
                requisicao.observacoes());
    }

    /**
     * Converte texto em competência.
     *
     * <p>Aceita {@code AAAA-MM}. Um formato inválido é erro de quem chamou, e sem este
     * tratamento viraria 500 — o {@code DateTimeException} não é falha do servidor.
     */
    private static YearMonth competenciaDe(String valor) {
        try {
            return YearMonth.parse(valor);
        } catch (DateTimeException erro) {
            throw new NegocioException(
                    ErroComum.VALIDACAO,
                    "Competência inválida: %s. Use o formato AAAA-MM, por exemplo 2026-03.".formatted(valor));
        }
    }

    /** Converte texto em enum devolvendo 400 com os valores aceitos, e não 500. */
    private static <E extends Enum<E>> E enumDe(
            Function<String, E> conversor, String valor, String campo, E[] aceitos) {
        try {
            return conversor.apply(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException erro) {
            throw new NegocioException(
                    ErroComum.VALIDACAO,
                    "%s inválido: %s. Aceitos: %s."
                            .formatted(campo, valor,
                                    String.join(", ", List.of(aceitos).stream().map(Enum::name).toList())));
        }
    }
}
