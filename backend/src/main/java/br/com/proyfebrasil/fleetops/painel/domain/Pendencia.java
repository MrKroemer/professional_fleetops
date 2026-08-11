package br.com.proyfebrasil.fleetops.painel.domain;

import java.util.Objects;

/**
 * Uma pendência apurada da base (RN-23).
 *
 * @param tipo       categoria da pendência
 * @param severidade urgência, que define a ordem na central
 * @param titulo     o que está pendente, em uma linha
 * @param detalhe    por que isso importa e o que fazer
 * @param recurso    rota da interface que resolve a pendência, ex.: {@code /cadastros/condutores}
 * @param referencia identificador do registro afetado, quando houver
 */
public record Pendencia(
        TipoDePendencia tipo,
        Severidade severidade,
        String titulo,
        String detalhe,
        String recurso,
        Long referencia) {

    public Pendencia {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(severidade, "severidade é obrigatória");
        Objects.requireNonNull(titulo, "título é obrigatório");
    }
}
