package br.com.proyfebrasil.fleetops.shared.exception;

import java.util.Map;

/** Recurso inexistente ou já excluído logicamente. */
public class RecursoNaoEncontradoException extends NegocioException {

    public RecursoNaoEncontradoException(String recurso, Object identificador) {
        super(
                ErroComum.RECURSO_NAO_ENCONTRADO,
                "%s não encontrado(a) para o identificador informado.".formatted(recurso),
                Map.of("recurso", recurso, "identificador", String.valueOf(identificador)));
    }
}
