package br.com.proyfebrasil.fleetops.contratos.domain;

/** Os dois momentos em que veículo e contrato se encontram ou se separam (Seção 3.2). */
public enum TipoDeEvento {

    RETIRADA("Retirada"),
    DEVOLUCAO("Devolução");

    private final String descricao;

    TipoDeEvento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
