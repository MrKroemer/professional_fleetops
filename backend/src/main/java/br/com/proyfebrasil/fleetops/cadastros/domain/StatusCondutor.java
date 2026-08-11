package br.com.proyfebrasil.fleetops.cadastros.domain;

/** Situação do condutor no quadro da empresa. */
public enum StatusCondutor {

    ATIVO("Ativo"),
    INATIVO("Inativo");

    private final String descricao;

    StatusCondutor(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
