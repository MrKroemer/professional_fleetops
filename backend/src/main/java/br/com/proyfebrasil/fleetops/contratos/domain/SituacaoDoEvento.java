package br.com.proyfebrasil.fleetops.contratos.domain;

/**
 * Situação de um evento de retirada ou devolução.
 *
 * <p>O estado intermediário existe por causa da RN-12, que proíbe "conclusão parcial
 * silenciosa": o preenchimento de um book de oito fotos acontece no pátio, pelo celular,
 * e pode ser interrompido. Sem este estado, ou o sistema perderia o que já foi enviado,
 * ou daria por concluído algo que não está.
 */
public enum SituacaoDoEvento {

    EM_PREENCHIMENTO("Em preenchimento"),
    CONCLUIDO("Concluído");

    private final String descricao;

    SituacaoDoEvento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
