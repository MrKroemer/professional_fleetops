package br.com.proyfebrasil.fleetops.operacao.domain;

/** Situação da conferência de uma competência. */
public enum StatusDoFechamento {

    ABERTO("Aberto"),
    CONFERIDO("Conferido");

    private final String descricao;

    StatusDoFechamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
