package br.com.proyfebrasil.fleetops.cadastros.domain;

/**
 * Tipo de fornecedor credenciado.
 *
 * <p>Determina quais dados complementares o cadastro exige — dias autorizados para
 * postos, preços por categoria para lava-jatos, custos e credenciais para
 * rastreadores, tamanhos e preços para gráficas.
 */
public enum TipoFornecedor {

    POSTO("Posto de combustível", true),
    LAVA_JATO("Lava-jato", true),
    BORRACHARIA("Borracharia", false),
    PARA_BRISAS("Para-brisas", false),
    RASTREADOR("Rastreador", true),
    GRAFICA("Gráfica", true),
    OFICINA("Oficina", false);

    private final String descricao;
    private final boolean possuiDadosEspecificos;

    TipoFornecedor(String descricao, boolean possuiDadosEspecificos) {
        this.descricao = descricao;
        this.possuiDadosEspecificos = possuiDadosEspecificos;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Indica se o tipo tem uma tabela satélite com campos próprios. */
    public boolean isPossuiDadosEspecificos() {
        return possuiDadosEspecificos;
    }
}
