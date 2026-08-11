package br.com.proyfebrasil.fleetops.cadastros.domain;

/** Combustível do veículo. Diesel implica teste de fumaça preta na retirada (RN-09). */
public enum Combustivel {

    FLEX("Flex"),
    GASOLINA("Gasolina"),
    ETANOL("Etanol"),
    DIESEL("Diesel"),
    HIBRIDO("Híbrido"),
    ELETRICO("Elétrico");

    private final String descricao;

    Combustivel(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Indica se o veículo exige teste de fumaça preta na retirada (RN-09). */
    public boolean exigeTesteDeFumacaPreta() {
        return this == DIESEL;
    }
}
