package br.com.proyfebrasil.fleetops.operacao.domain;

/**
 * Situação da conferência de uma fatura (RN-13).
 *
 * <p>{@code OK} é o único status que afirma "está tudo certo", e por isso é o único que a
 * RN-13 proíbe quando há divergência. Os outros dois descrevem tratativa em andamento —
 * é onde uma fatura divergente deve ficar enquanto a conversa com a locadora não termina.
 */
public enum StatusDeConferencia {

    PENDENTE("Pendente de conferência"),
    OK("Conferida e correta"),
    EM_CONTESTACAO("Em contestação com a locadora"),
    AJUSTADA("Ajustada após tratativa");

    private final String descricao;

    StatusDeConferencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Indica que a fatura foi dada por correta — o que exige divergência zero. */
    public boolean afirmaQueEstaCorreta() {
        return this == OK;
    }
}
