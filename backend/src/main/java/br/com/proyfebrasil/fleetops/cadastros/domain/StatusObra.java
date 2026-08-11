package br.com.proyfebrasil.fleetops.cadastros.domain;

/** Situação de uma obra. */
public enum StatusObra {

    ATIVA("Ativa"),
    ENCERRADA("Encerrada");

    private final String descricao;

    StatusObra(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
