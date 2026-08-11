package br.com.proyfebrasil.fleetops.cadastros.domain;

/**
 * Natureza da locadora.
 *
 * <p>A distinção é operacional: locadoras nacionais têm portal, consultor dedicado e
 * tabela de preços por vigência; as avulsas são contratadas pontualmente, em geral
 * onde as nacionais não operam — Fernando de Noronha é o caso recorrente.
 */
public enum TipoLocadora {

    NACIONAL("Nacional"),
    AVULSA("Avulsa / local");

    private final String descricao;

    TipoLocadora(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
