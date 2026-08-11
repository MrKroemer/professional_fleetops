package br.com.proyfebrasil.fleetops.painel.domain;

/**
 * Severidade de uma pendência na central do painel (RN-23).
 *
 * <p>A ordem de declaração é a ordem de exibição: o que exige ação hoje aparece antes
 * do que apenas convém resolver. A central ordena por esta enumeração, e não por data,
 * porque uma CNH vencida importa mais que um cadastro incompleto antigo.
 */
public enum Severidade {

    CRITICA("Crítica", "Exige ação imediata; há operação bloqueada ou risco legal."),
    ATENCAO("Atenção", "Vence em breve ou está fora do padrão combinado."),
    INFORMATIVA("Informativa", "Cadastro incompleto que limita cálculos futuros.");

    private final String descricao;
    private final String significado;

    Severidade(String descricao, String significado) {
        this.descricao = descricao;
        this.significado = significado;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getSignificado() {
        return significado;
    }
}
