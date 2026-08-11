package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;

/**
 * Serviços operacionais recorrentes (Seção 3.3).
 *
 * <p>Só o lava-jato tem limite de frequência: a RN-05 fala de um serviço por semana, e
 * nada equivalente existe para borracharia ou para-brisas — trocar dois pneus na mesma
 * semana é azar, não irregularidade. A regra viaja no próprio enum para que o serviço não
 * precise de um `switch` que alguém esqueceria de atualizar ao acrescentar um tipo.
 */
public enum TipoDeServico {

    LAVA_JATO("Lava-jato", TipoFornecedor.LAVA_JATO, true),
    BORRACHARIA("Borracharia", TipoFornecedor.BORRACHARIA, false),
    PARA_BRISAS("Para-brisas", TipoFornecedor.PARA_BRISAS, false);

    private final String descricao;
    private final TipoFornecedor fornecedorEsperado;
    private final boolean limitadoPorSemana;

    TipoDeServico(String descricao, TipoFornecedor fornecedorEsperado, boolean limitadoPorSemana) {
        this.descricao = descricao;
        this.fornecedorEsperado = fornecedorEsperado;
        this.limitadoPorSemana = limitadoPorSemana;
    }

    public String getDescricao() {
        return descricao;
    }

    public TipoFornecedor getFornecedorEsperado() {
        return fornecedorEsperado;
    }

    /** Indica se a RN-05 se aplica: no máximo um por semana. */
    public boolean isLimitadoPorSemana() {
        return limitadoPorSemana;
    }
}
