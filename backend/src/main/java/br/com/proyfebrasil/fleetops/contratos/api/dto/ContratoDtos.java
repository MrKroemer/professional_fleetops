package br.com.proyfebrasil.fleetops.contratos.api.dto;

import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs do ciclo de vida do contrato (Fase 2).
 *
 * <p>Reunidos em um arquivo por serem um contrato único de tela: a página do contrato
 * consome todos eles juntos, e espalhá-los em nove arquivos obrigaria a abrir nove abas
 * para entender uma única resposta.
 */
public final class ContratoDtos {

    private ContratoDtos() {
    }

    // ---------------------------------------------------------------- linha do tempo

    @Schema(description = "Um marco da linha do tempo do contrato")
    public record MarcoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate data,
            LocalDate fim,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rotulo,
            String detalhe,
            String motivo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emCurso,
            @Schema(description = "Identificador do registro de origem") Long referenciaId) {
    }

    @Schema(description = "Ciclo de vida completo de um contrato")
    public record LinhaDoTempoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long contratoId,
            String codigoInterno,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StatusContrato status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusDescricao,
            LocalDate dataRetirada,
            LocalDate dataEncerramento,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String obra,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String locadora,
            String veiculoAtual,
            String condutorAtual,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantidadeDeSubstituicoes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantidadeDeTrocasDeCondutor,
            List<MarcoResponse> marcos) {
    }

    // ---------------------------------------------------------------- listagem

    @Schema(description = "Uma linha da lista de contratos")
    public record ContratoResumoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            String codigoInterno,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String obraCodigo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String obraNome,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String locadora,
            String placa,
            String modelo,
            String condutor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StatusContrato status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String statusDescricao,
            LocalDate dataRetirada,
            LocalDate dataEncerramento,
            Integer pacoteKmContratado,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantidadeDeSubstituicoes,
            int quantidadeDeTrocasDeCondutor) {
    }

    // ---------------------------------------------------------------- consulta temporal

    @Schema(description = "Quem dirigia o quê em uma data (RN-18)")
    public record SituacaoNaDataResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate data,
            String placa,
            String modelo,
            String condutor,
            @Schema(description = "Falso quando o contrato ainda não existia ou já se encerrara")
            boolean vigente) {
    }

    // ---------------------------------------------------------------- trocas

    @Schema(description = "Troca de condutor (RN-18)")
    public record TrocaDeCondutorRequest(
            @NotNull(message = "Informe o condutor") Long condutorId,
            @NotNull(message = "Informe a data da troca") LocalDate aPartirDe,
            @Size(max = 300) String motivo) {
    }

    @Schema(description = "Substituição de veículo (RN-01)")
    public record SubstituicaoRequest(
            @NotNull(message = "Informe o veículo") Long veiculoId,
            @NotNull(message = "Informe a data da substituição") LocalDate aPartirDe,
            @Size(max = 300) String motivo) {
    }

    // ---------------------------------------------------------------- eventos

    @Schema(description = "Abertura ou atualização de um evento de retirada/devolução")
    public record EventoRequest(
            @NotNull(message = "Informe o tipo do evento") String tipo,
            @NotNull(message = "Informe o veículo") Long veiculoId,
            Long condutorId,
            @NotNull(message = "Informe a data do evento") LocalDate dataDoEvento,
            @PositiveOrZero(message = "A quilometragem não pode ser negativa") Integer km,
            @Size(max = 180) String localDoEvento,
            String checklistDaLocadora,
            String observacoes) {
    }

    @Schema(description = "Uma foto do book fotográfico")
    public record FotoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String item,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long anexoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nomeDoArquivo,
            String observacao) {
    }

    @Schema(description = "Um ângulo obrigatório ainda sem foto (RN-12)")
    public record ItemPendenteResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String item,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String descricao) {
    }

    @Schema(description = "Evento de retirada ou devolução")
    public record EventoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long contratoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tipoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String situacao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String situacaoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dataDoEvento,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long veiculoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelo,
            String condutor,
            Integer km,
            String localDoEvento,
            String checklistDaLocadora,
            Instant regrasAceitasEm,
            Instant concluidoEm,
            String observacoes,
            Long crlvAnexoId,
            String crlvNomeDoArquivo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FotoResponse> fotos,
            @Schema(description = "O que ainda falta para concluir (RN-12)", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ItemPendenteResponse> itensFaltantes,
            boolean completo) {
    }

    // ---------------------------------------------------------------- encerramento

    @Schema(description = "Encerramento do contrato (RN-17)")
    public record EncerramentoRequest(
            @NotNull(message = "Informe o status de encerramento") String status,
            LocalDate dataDeEncerramento) {
    }

    @Schema(description = "Verificação das condições da RN-17")
    public record VerificacaoDeDevolucaoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean liberaDevolucao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean eventoDeDevolucaoConcluido,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int avariasAbertas,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean fechamentoDoPeriodoFinal,
            List<String> pendencias) {
    }

    // ---------------------------------------------------------------- fumaça preta

    @Schema(description = "Registro de teste de fumaça preta (FOR.MA.01, RN-09)")
    public record TesteDeFumacaRequest(
            @NotNull(message = "Informe o veículo") Long veiculoId,
            Long contratoId,
            Long condutorId,
            Long obraId,
            @NotNull(message = "Informe a data do teste") LocalDate dataDoTeste,
            @NotNull(message = "Informe o padrão observado na escala de Ringelmann") Integer padrao,
            @PositiveOrZero(message = "A altitude não pode ser negativa") Integer altitudeEmMetros,
            String observacoes) {
    }

    @Schema(description = "Resultado de um teste de fumaça preta")
    public record TesteDeFumacaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long veiculoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dataDoTeste,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int padrao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String padraoDescricao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int opacidadePercentual,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int altitudeEmMetros,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean conforme,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String justificativa,
            String observacoes) {
    }

    @Schema(description = "Situação de um veículo quanto à RN-09")
    public record SituacaoDaFumacaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exigido,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean liberado,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean pendente,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reprovado,
            TesteDeFumacaResponse ultimoTeste) {
    }
}
