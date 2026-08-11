package br.com.proyfebrasil.fleetops.painel.domain;

/**
 * Categorias de pendência que a Fase 1 já consegue apurar.
 *
 * <p>A central de pendências da RN-23 é entrega da Fase 4, quando existirem checklists,
 * multas e avarias. As categorias abaixo são as que os cadastros sustentam hoje — e são
 * apuradas de verdade, não simuladas. As demais entram junto com seus módulos.
 */
public enum TipoDePendencia {

    CNH_VENCIDA("CNH vencida", "RN-16"),
    CNH_VENCENDO("CNH vencendo", "RN-16"),
    OBRA_SEM_POSTO("Obra sem posto credenciado", "RN-04"),
    OBRA_SEM_LAVA_JATO("Obra sem lava-jato credenciado", "RN-05"),
    LOCADORA_SEM_VIGENCIA("Locadora sem tabela de preços do ano", "RN-14"),
    VEICULO_SEM_GRUPO_TARIFARIO("Veículo sem grupo tarifário", "RN-06"),
    LOCADORA_SEM_CREDENCIAL("Locadora com portal e sem credencial", "RN-20"),
    FATURA_DIVERGENTE("Fatura com divergência sem tratativa concluída", "RN-13"),
    KM_ACIMA_DA_FRANQUIA("Contrato acima da franquia de KM na competência", "RN-06"),
    VEICULO_EM_DOIS_CONTRATOS("Veículo em dois contratos ao mesmo tempo", "RN-01");

    private final String descricao;
    private final String regra;

    TipoDePendencia(String descricao, String regra) {
        this.descricao = descricao;
        this.regra = regra;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Regra de negócio que origina a pendência, para rastreabilidade na interface. */
    public String getRegra() {
        return regra;
    }
}
