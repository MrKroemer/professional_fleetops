package br.com.proyfebrasil.fleetops.shared.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Dinheiro — aritmética monetária com escala 2")
class DinheiroTest {

    @Nested
    @DisplayName("normalizar")
    class Normalizar {

        @Test
        @DisplayName("ajusta o valor para duas casas decimais")
        void ajustaEscala() {
            assertThat(Dinheiro.normalizar(new BigDecimal("10.5"))).isEqualByComparingTo("10.50");
            assertThat(Dinheiro.normalizar(new BigDecimal("10.5")).scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("arredonda meio centavo para cima")
        void arredondaHalfUp() {
            assertThat(Dinheiro.normalizar(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
            assertThat(Dinheiro.normalizar(new BigDecimal("10.004"))).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("trata nulo como zero")
        void nuloViraZero() {
            assertThat(Dinheiro.normalizar(null)).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("operações")
    class Operacoes {

        @Test
        @DisplayName("soma valores ignorando nulos")
        void soma() {
            assertThat(Dinheiro.somar(new BigDecimal("10.10"), null, new BigDecimal("5.90")))
                    .isEqualByComparingTo("16.00");
        }

        @Test
        @DisplayName("soma uma coleção de valores")
        void somaColecao() {
            List<BigDecimal> valores =
                    List.of(new BigDecimal("1200.00"), new BigDecimal("89.90"), new BigDecimal("0.10"));
            assertThat(Dinheiro.somar(valores)).isEqualByComparingTo("1290.00");
        }

        @Test
        @DisplayName("subtrai produzindo resultado negativo quando cabível")
        void subtrai() {
            assertThat(Dinheiro.subtrair(new BigDecimal("100.00"), new BigDecimal("150.50")))
                    .isEqualByComparingTo("-50.50");
        }

        @Test
        @DisplayName("multiplica valor unitário por quantidade — base do custo de KM excedente (RN-06)")
        void multiplica() {
            BigDecimal valorPorKm = new BigDecimal("0.89");
            BigDecimal kmExcedente = new BigDecimal("437");
            assertThat(Dinheiro.multiplicar(valorPorKm, kmExcedente)).isEqualByComparingTo("388.93");
        }

        @Test
        @DisplayName("multiplicação com operando nulo resulta em zero")
        void multiplicaComNulo() {
            assertThat(Dinheiro.multiplicar(null, BigDecimal.TEN)).isEqualByComparingTo("0.00");
            assertThat(Dinheiro.multiplicar(BigDecimal.TEN, null)).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("comparações")
    class Comparacoes {

        @Test
        @DisplayName("considera iguais valores com escalas diferentes")
        void igualdadeIgnoraEscala() {
            assertThat(Dinheiro.saoIguais(new BigDecimal("10"), new BigDecimal("10.00"))).isTrue();
            assertThat(Dinheiro.saoIguais(new BigDecimal("10.00"), new BigDecimal("10.01"))).isFalse();
        }

        @Test
        @DisplayName("detecta divergência diferente de zero — gatilho da RN-13")
        void detectaDivergencia() {
            assertThat(Dinheiro.naoEZero(new BigDecimal("0.001"))).isFalse();
            assertThat(Dinheiro.naoEZero(new BigDecimal("0.01"))).isTrue();
            assertThat(Dinheiro.naoEZero(null)).isFalse();
        }

        @Test
        @DisplayName("identifica valores negativos")
        void identificaNegativo() {
            assertThat(Dinheiro.eNegativo(new BigDecimal("-0.01"))).isTrue();
            assertThat(Dinheiro.eNegativo(Dinheiro.ZERO)).isFalse();
        }
    }
}
