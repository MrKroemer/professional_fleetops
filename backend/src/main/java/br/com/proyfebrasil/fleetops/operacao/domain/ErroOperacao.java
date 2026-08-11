package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.shared.exception.CodigoErro;
import org.springframework.http.HttpStatus;

/** Erros de negócio da operação mensal (Fase 3). */
public enum ErroOperacao implements CodigoErro {

    KM_RETROCEDE(
            "RN-003-KM_RETROCEDE",
            "Quilometragem menor que a do registro anterior",
            HttpStatus.CONFLICT),
    KM_INVALIDO(
            "RN-003-KM_INVALIDO",
            "Quilometragem final menor que a inicial",
            HttpStatus.BAD_REQUEST),
    ABASTECIMENTO_DUPLICADO(
            "RN-004-ABASTECIMENTO_DUPLICADO",
            "Já há abastecimento lançado neste dia",
            HttpStatus.CONFLICT),
    ABASTECIMENTO_NAO_CONFORME(
            "RN-004-ABASTECIMENTO_NAO_CONFORME",
            "Abastecimento fora das condições autorizadas",
            HttpStatus.UNPROCESSABLE_ENTITY),
    LAVA_JATO_DUPLICADO(
            "RN-005-LAVA_JATO_DUPLICADO",
            "Já há lava-jato lançado nesta semana",
            HttpStatus.CONFLICT),
    JUSTIFICATIVA_OBRIGATORIA(
            "RN-004-JUSTIFICATIVA_OBRIGATORIA",
            "Lançamento não conforme exige justificativa",
            HttpStatus.BAD_REQUEST),
    FATURA_DIVERGENTE_SEM_OBSERVACAO(
            "RN-013-FATURA_DIVERGENTE_SEM_OBSERVACAO",
            "Fatura com divergência exige conferência e observação",
            HttpStatus.UNPROCESSABLE_ENTITY),
    FATURA_DUPLICADA(
            "RN-013-FATURA_DUPLICADA",
            "Já há fatura lançada para esta competência",
            HttpStatus.CONFLICT),
    USO_PARTICULAR_ACIMA_DO_LIMITE(
            "RN-010-USO_PARTICULAR_ACIMA_DO_LIMITE",
            "Uso particular acima do limite de 1.000 km",
            HttpStatus.UNPROCESSABLE_ENTITY),
    USO_PARTICULAR_SEM_ACEITE(
            "RN-010-USO_PARTICULAR_SEM_ACEITE",
            "Uso particular exige aceite das regras pelo condutor",
            HttpStatus.UNPROCESSABLE_ENTITY),
    COMPETENCIA_CONFERIDA(
            "OPR-001-COMPETENCIA_CONFERIDA",
            "Competência já conferida",
            HttpStatus.CONFLICT);

    private final String codigo;
    private final String titulo;
    private final HttpStatus status;

    ErroOperacao(String codigo, String titulo, HttpStatus status) {
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
