package br.com.proyfebrasil.fleetops.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Operações monetárias do sistema. Todo valor em reais trafega como {@link BigDecimal}
 * com escala 2 e arredondamento {@link RoundingMode#HALF_UP}, espelhando o
 * {@code NUMERIC(12,2)} do banco. Ponto flutuante é proibido para dinheiro (Seção 11).
 *
 * <p>Centralizar o arredondamento aqui evita a divergência de centavos que hoje aparece
 * entre as planilhas de fechamento e as faturas das locadoras.
 */
public final class Dinheiro {

    /** Escala fixa dos valores monetários, alinhada ao {@code NUMERIC(12,2)}. */
    public static final int ESCALA = 2;

    /** Arredondamento padrão para valores monetários. */
    public static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_UP;

    /** Zero já normalizado na escala monetária. */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(ESCALA, ARREDONDAMENTO);

    private Dinheiro() {
    }

    /** Normaliza um valor para a escala monetária. {@code null} é tratado como zero. */
    public static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? ZERO : valor.setScale(ESCALA, ARREDONDAMENTO);
    }

    /** Soma valores já normalizando o resultado; {@code null} conta como zero. */
    public static BigDecimal somar(BigDecimal... valores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            total = total.add(valor == null ? BigDecimal.ZERO : valor);
        }
        return normalizar(total);
    }

    /** Soma uma coleção de valores, normalizando o resultado. */
    public static BigDecimal somar(Collection<BigDecimal> valores) {
        Objects.requireNonNull(valores, "valores é obrigatório");
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            total = total.add(valor == null ? BigDecimal.ZERO : valor);
        }
        return normalizar(total);
    }

    /** Subtrai {@code subtraendo} de {@code minuendo}, normalizando o resultado. */
    public static BigDecimal subtrair(BigDecimal minuendo, BigDecimal subtraendo) {
        BigDecimal esquerda = minuendo == null ? BigDecimal.ZERO : minuendo;
        BigDecimal direita = subtraendo == null ? BigDecimal.ZERO : subtraendo;
        return normalizar(esquerda.subtract(direita));
    }

    /**
     * Multiplica um valor unitário por uma quantidade — usado, por exemplo, no custo de
     * KM excedente (RN-06): quantidade de quilômetros vezes o valor unitário da tabela.
     */
    public static BigDecimal multiplicar(BigDecimal valorUnitario, BigDecimal quantidade) {
        if (valorUnitario == null || quantidade == null) {
            return ZERO;
        }
        return normalizar(valorUnitario.multiply(quantidade));
    }

    /** Compara dois valores na escala monetária, ignorando diferenças de escala. */
    public static boolean saoIguais(BigDecimal um, BigDecimal outro) {
        return normalizar(um).compareTo(normalizar(outro)) == 0;
    }

    /** Indica se o valor é diferente de zero na escala monetária — base da RN-13. */
    public static boolean naoEZero(BigDecimal valor) {
        return normalizar(valor).compareTo(ZERO) != 0;
    }

    /** Indica se o valor é negativo na escala monetária. */
    public static boolean eNegativo(BigDecimal valor) {
        return normalizar(valor).compareTo(ZERO) < 0;
    }
}
