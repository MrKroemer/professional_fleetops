package br.com.proyfebrasil.fleetops.cadastros.domain;

import java.util.regex.Pattern;

/**
 * CPF do condutor.
 *
 * <p>Armazenado apenas com dígitos; a máscara é responsabilidade da exibição. Os dígitos
 * verificadores são conferidos porque o CPF identifica o condutor em indicações de multa
 * (RN-08), onde um número inválido só apareceria como problema no prazo final do órgão
 * de trânsito — tarde demais.
 */
public final class Cpf {

    private static final Pattern NAO_DIGITOS = Pattern.compile("\\D");
    private static final int TAMANHO = 11;

    private Cpf() {
    }

    /**
     * Remove a formatação e valida os dígitos verificadores.
     *
     * @throws IllegalArgumentException se o CPF for nulo, tiver tamanho incorreto,
     *     for uma sequência repetida ou tiver dígitos verificadores inválidos
     */
    public static String normalizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("O CPF é obrigatório.");
        }
        String digitos = NAO_DIGITOS.matcher(bruto).replaceAll("");
        if (digitos.length() != TAMANHO) {
            throw new IllegalArgumentException("CPF inválido: deve conter 11 dígitos.");
        }
        if (!verificadoresConferem(digitos)) {
            throw new IllegalArgumentException("CPF inválido: os dígitos verificadores não conferem.");
        }
        return digitos;
    }

    /** Indica se o valor é um CPF válido, sem lançar exceção. */
    public static boolean eValido(String bruto) {
        if (bruto == null) {
            return false;
        }
        String digitos = NAO_DIGITOS.matcher(bruto).replaceAll("");
        return digitos.length() == TAMANHO && verificadoresConferem(digitos);
    }

    /** Formata o CPF para exibição, ex.: {@code 123.456.789-09}. */
    public static String formatar(String digitos) {
        if (digitos == null || digitos.length() != TAMANHO) {
            return digitos;
        }
        return "%s.%s.%s-%s".formatted(
                digitos.substring(0, 3),
                digitos.substring(3, 6),
                digitos.substring(6, 9),
                digitos.substring(9));
    }

    private static boolean verificadoresConferem(String digitos) {
        // Sequências repetidas (000..., 111...) passam no cálculo, mas não são CPFs reais.
        if (digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(digitos, 9) == charParaInt(digitos, 9)
                && digitoVerificador(digitos, 10) == charParaInt(digitos, 10);
    }

    private static int digitoVerificador(String digitos, int posicao) {
        int soma = 0;
        int peso = posicao + 1;
        for (int i = 0; i < posicao; i++) {
            soma += charParaInt(digitos, i) * peso--;
        }
        int resto = soma % TAMANHO;
        return resto < 2 ? 0 : TAMANHO - resto;
    }

    private static int charParaInt(String texto, int indice) {
        return texto.charAt(indice) - '0';
    }
}
