package br.com.proyfebrasil.fleetops.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Código de erro de negócio estável, exposto no campo {@code codigo} das respostas
 * RFC 7807. É contrato público da API: o frontend pode ramificar sobre ele, portanto
 * um código nunca muda de significado — no máximo é descontinuado.
 *
 * <p>Convenção do identificador: {@code <ORIGEM>-<NNN>-<NOME>}, onde a origem é
 * {@code GEN} para erros transversais ou o número da regra de negócio quando o erro
 * decorre diretamente dela (ex.: {@code RN-004-ABASTECIMENTO_DUPLICADO}).
 */
public interface CodigoErro {

    /** Identificador estável do erro, ex.: {@code RN-004-ABASTECIMENTO_DUPLICADO}. */
    String codigo();

    /** Título curto em pt-BR, exibível ao usuário. */
    String titulo();

    /** Status HTTP correspondente. */
    HttpStatus status();
}
