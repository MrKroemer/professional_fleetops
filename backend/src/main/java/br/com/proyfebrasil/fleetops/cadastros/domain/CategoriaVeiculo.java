package br.com.proyfebrasil.fleetops.cadastros.domain;

/**
 * Categoria tarifária do veículo.
 *
 * <p>É a chave do preço de KM excedente nas tabelas das locadoras (RN-06): a mesma
 * quilometragem excedida custa R$ 0,60/km em um passeio e R$ 3,20/km em um 4x4.
 */
public enum CategoriaVeiculo {

    PASSEIO("Passeio"),
    SUV("SUV"),
    QUATRO_X_QUATRO("4x4"),
    UTILITARIO("Utilitário");

    private final String descricao;

    CategoriaVeiculo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
