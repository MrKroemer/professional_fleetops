package br.com.proyfebrasil.fleetops.contratos.domain;

/**
 * Estados do contrato de locação (Seção 3.2).
 *
 * <p>A distinção entre desmobilizado e devolvido é operacional e importa: o veículo
 * devolvido pela obra ainda está com a empresa, enquanto o devolvido à locadora saiu
 * da frota. Um contrato com avaria aberta só pode chegar a {@code DESMOBILIZADO},
 * nunca a {@code DEVOLVIDO} (RN-17).
 */
public enum StatusContrato {

    ATIVO("Ativo", "Veículo em operação na obra."),
    DESMOBILIZADO("Desmobilizado", "Devolvido pela obra, ainda não devolvido à locadora."),
    DEVOLVIDO("Devolvido", "Devolvido à locadora; saiu da frota."),
    INATIVO("Inativo", "Encerrado sem devolução registrada.");

    private final String descricao;
    private final String significado;

    StatusContrato(String descricao, String significado) {
        this.descricao = descricao;
        this.significado = significado;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getSignificado() {
        return significado;
    }

    /** Indica se o contrato ainda ocupa um veículo da frota. */
    public boolean ocupaVeiculo() {
        return this == ATIVO || this == DESMOBILIZADO;
    }
}
