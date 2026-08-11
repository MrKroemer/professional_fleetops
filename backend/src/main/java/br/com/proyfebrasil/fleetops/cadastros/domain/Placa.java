package br.com.proyfebrasil.fleetops.cadastros.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Placa de veículo (RN-02).
 *
 * <p>Aceita os dois formatos em circulação no Brasil e os normaliza para uma única
 * representação — caixa alta, sem espaços, hífens ou pontos:
 *
 * <ul>
 *   <li><strong>Antigo</strong> — {@code AAA9999}, ex.: {@code RML8I33};</li>
 *   <li><strong>Mercosul</strong> — {@code AAA9A99}, ex.: {@code RML8I33} com letra
 *       na quinta posição.</li>
 * </ul>
 *
 * <p>Normalizar na entrada é o que torna a unicidade confiável: as planilhas atuais
 * têm a mesma placa grafada como {@code ABC-1234}, {@code abc1234} e {@code ABC 1234},
 * o que hoje produz duplicatas silenciosas.
 */
public final class Placa {

    /**
     * Um único padrão cobre os dois formatos: as três primeiras posições são letras,
     * a quarta é dígito, a quinta é letra (Mercosul) ou dígito (antigo), e as duas
     * últimas são dígitos.
     */
    private static final Pattern PADRAO = Pattern.compile("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$");

    private static final Pattern SEPARADORES = Pattern.compile("[\\s.\\-]");

    /** Comprimento da placa já normalizada. */
    public static final int TAMANHO = 7;

    private Placa() {
    }

    /**
     * Normaliza a placa para caixa alta sem separadores.
     *
     * @throws IllegalArgumentException se o valor for nulo, vazio ou não corresponder
     *     a nenhum dos formatos aceitos
     */
    public static String normalizar(String bruta) {
        if (bruta == null || bruta.isBlank()) {
            throw new IllegalArgumentException("A placa é obrigatória.");
        }
        String normalizada = SEPARADORES.matcher(bruta).replaceAll("").toUpperCase(Locale.ROOT);
        if (!PADRAO.matcher(normalizada).matches()) {
            throw new IllegalArgumentException(
                    "Placa inválida: \"%s\". Use o formato Mercosul (ABC1D23) ou o antigo (ABC1234)."
                            .formatted(bruta));
        }
        return normalizada;
    }

    /** Indica se o valor corresponde a uma placa válida, sem lançar exceção. */
    public static boolean eValida(String bruta) {
        if (bruta == null || bruta.isBlank()) {
            return false;
        }
        return PADRAO.matcher(SEPARADORES.matcher(bruta).replaceAll("").toUpperCase(Locale.ROOT)).matches();
    }

    /** Indica se a placa normalizada segue o padrão Mercosul (letra na quinta posição). */
    public static boolean eMercosul(String normalizada) {
        return normalizada != null
                && normalizada.length() == TAMANHO
                && Character.isLetter(normalizada.charAt(4));
    }

    /** Formata a placa para exibição, ex.: {@code ABC-1D23}. */
    public static String formatar(String normalizada) {
        if (normalizada == null || normalizada.length() != TAMANHO) {
            return normalizada;
        }
        return normalizada.substring(0, 3) + "-" + normalizada.substring(3);
    }
}
