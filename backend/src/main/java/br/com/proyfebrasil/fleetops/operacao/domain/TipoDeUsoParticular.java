package br.com.proyfebrasil.fleetops.operacao.domain;

/** Modalidades de uso particular previstas na Seção 3.3. */
public enum TipoDeUsoParticular {

    FOLGA_RECORRENTE("Folga recorrente"),
    USO_PONTUAL("Uso particular pontual");

    private final String descricao;

    TipoDeUsoParticular(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
