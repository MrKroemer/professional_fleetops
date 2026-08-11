package br.com.proyfebrasil.fleetops.cadastros.domain;

/** Situação física do veículo perante a locadora. */
public enum StatusVeiculo {

    DISPONIVEL("Disponível"),
    EM_USO("Em uso"),
    EM_MANUTENCAO("Em manutenção"),
    DEVOLVIDO("Devolvido à locadora");

    private final String descricao;

    StatusVeiculo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
