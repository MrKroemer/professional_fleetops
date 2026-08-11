package br.com.proyfebrasil.fleetops.contratos.domain;

import br.com.proyfebrasil.fleetops.shared.exception.CodigoErro;
import org.springframework.http.HttpStatus;

/**
 * Erros de negócio do ciclo de vida do contrato (Fase 2).
 *
 * <p>Como no restante do sistema, o código carrega o número da regra que o originou —
 * uma resposta de erro é rastreável até a especificação sem documentação intermediária.
 */
public enum ErroContrato implements CodigoErro {

    SUBSTITUICAO_FORA_DE_ORDEM(
            "RN-001-SUBSTITUICAO_FORA_DE_ORDEM",
            "Data de substituição incompatível com o histórico",
            HttpStatus.CONFLICT),
    CONTRATO_ENCERRADO(
            "RN-001-CONTRATO_ENCERRADO",
            "O contrato já foi encerrado",
            HttpStatus.CONFLICT),
    TROCA_DE_CONDUTOR_FORA_DE_ORDEM(
            "RN-018-TROCA_FORA_DE_ORDEM",
            "Data de troca de condutor incompatível com o histórico",
            HttpStatus.CONFLICT),
    FUMACA_PRETA_PENDENTE(
            "RN-009-FUMACA_PRETA_PENDENTE",
            "Teste de fumaça preta não realizado",
            HttpStatus.CONFLICT),
    FUMACA_PRETA_REPROVADA(
            "RN-009-FUMACA_PRETA_REPROVADA",
            "Teste de fumaça preta reprovado",
            HttpStatus.CONFLICT),
    BOOK_INCOMPLETO(
            "RN-012-BOOK_INCOMPLETO",
            "Book fotográfico incompleto",
            HttpStatus.CONFLICT),
    CRLV_AUSENTE(
            "RN-012-CRLV_AUSENTE",
            "CRLV não anexado",
            HttpStatus.CONFLICT),
    EVENTO_JA_CONCLUIDO(
            "RN-012-EVENTO_JA_CONCLUIDO",
            "Evento já concluído",
            HttpStatus.CONFLICT),
    DEVOLUCAO_SEM_EVENTO(
            "RN-017-DEVOLUCAO_SEM_EVENTO",
            "Devolução exige evento de devolução concluído",
            HttpStatus.CONFLICT),
    DEVOLUCAO_COM_AVARIA_ABERTA(
            "RN-017-DEVOLUCAO_COM_AVARIA_ABERTA",
            "Avaria aberta impede a devolução à locadora",
            HttpStatus.CONFLICT),
    CONDUTOR_COM_CNH_VENCIDA(
            "RN-016-CONDUTOR_COM_CNH_VENCIDA",
            "CNH vencida impede o vínculo ao contrato",
            HttpStatus.CONFLICT),
    CONDUTOR_INATIVO(
            "CTR-001-CONDUTOR_INATIVO",
            "Condutor inativo não assume contrato",
            HttpStatus.CONFLICT);

    private final String codigo;
    private final String titulo;
    private final HttpStatus status;

    ErroContrato(String codigo, String titulo, HttpStatus status) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.status = status;
    }

    @Override
    public String codigo() {
        return codigo;
    }

    @Override
    public String titulo() {
        return titulo;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
