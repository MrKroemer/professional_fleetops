package br.com.proyfebrasil.fleetops.conformidade.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("RN-09 — teste de fumaça preta na escala de Ringelmann")
class TesteDeFumacaPretaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);

    private static Veiculo veiculo(Combustivel combustivel) {
        return new Veiculo("ABC1D23", "S10", CategoriaVeiculo.QUATRO_X_QUATRO, combustivel,
                new Locadora("Unidas", TipoLocadora.NACIONAL));
    }

    private static TesteDeFumacaPreta teste(PadraoRingelmann padrao, int altitude) {
        return new TesteDeFumacaPreta(veiculo(Combustivel.DIESEL), HOJE, padrao, altitude);
    }

    @Nested
    @DisplayName("critério da Seção 3.4")
    class Criterio {

        @ParameterizedTest(name = "Padrão {0} a {1} m → aprovado={2}")
        @DisplayName("RN09_reprovaAcimaDoPadrao2AteQuinhentosMetros_eAcimaDoPadrao3AcimaDisso")
        @CsvSource({
            // Até 500 m o limite é o Padrão 2.
            "1,   0, true",
            "2,   0, true",
            "3,   0, false",
            "5,   0, false",
            // Exatamente 500 m ainda é "até 500 m": o corte é `> 500`, não `>= 500`.
            "2, 500, true",
            "3, 500, false",
            // Acima de 500 m o limite afrouxa para o Padrão 3.
            "3, 501, true",
            "3, 900, true",
            "4, 900, false",
        })
        void aplicaOLimitePorAltitude(int padrao, int altitude, boolean aprovado) {
            var resultado = teste(PadraoRingelmann.porCodigo(padrao), altitude);
            assertThat(resultado.isConforme()).isEqualTo(aprovado);
        }

        @Test
        @DisplayName("a justificativa explica o limite aplicado, não só o resultado")
        void justificativaCitaOLimite() {
            // Quem recebe "reprovado" precisa saber contra qual régua — o mesmo Padrão 3
            // aprova a 900 m e reprova ao nível do mar.
            assertThat(teste(PadraoRingelmann.PADRAO_3, 100).justificativa())
                    .contains("Padrão 3", "ultrapassa", "Padrão 2", "até 500 m");
            assertThat(teste(PadraoRingelmann.PADRAO_3, 900).justificativa())
                    .contains("Padrão 3", "dentro do limite", "acima de 500 m");
        }
    }

    @Nested
    @DisplayName("escala")
    class Escala {

        @Test
        @DisplayName("os cinco padrões vão de 20% a 100%, em passos de 20")
        void opacidadePorPadrao() {
            assertThat(PadraoRingelmann.values())
                    .extracting(PadraoRingelmann::getCodigo, PadraoRingelmann::getOpacidadePercentual)
                    .containsExactly(
                            org.assertj.core.api.Assertions.tuple(1, 20),
                            org.assertj.core.api.Assertions.tuple(2, 40),
                            org.assertj.core.api.Assertions.tuple(3, 60),
                            org.assertj.core.api.Assertions.tuple(4, 80),
                            org.assertj.core.api.Assertions.tuple(5, 100));
        }

        @Test
        @DisplayName("um código fora da escala é recusado, não silenciosamente ajustado")
        void codigoForaDaEscala() {
            assertThatThrownBy(() -> PadraoRingelmann.porCodigo(6))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Padrão 1 ao 5");
            assertThatThrownBy(() -> PadraoRingelmann.porCodigo(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("o conversor grava o número da cartela, não o ordinal")
        void conversorUsaOCodigo() {
            var conversor = new ConversorDePadraoRingelmann();
            // O ordinal de PADRAO_1 é 0; gravar isso quebraria a coluna gerada, que
            // compara o número contra 2 e 3.
            assertThat(conversor.convertToDatabaseColumn(PadraoRingelmann.PADRAO_1)).isEqualTo((short) 1);
            assertThat(conversor.convertToEntityAttribute((short) 4)).isEqualTo(PadraoRingelmann.PADRAO_4);
            assertThat(conversor.convertToDatabaseColumn(null)).isNull();
            assertThat(conversor.convertToEntityAttribute(null)).isNull();
        }
    }

    @Test
    @DisplayName("altitude negativa é recusada")
    void altitudeNegativa() {
        assertThatThrownBy(() -> teste(PadraoRingelmann.PADRAO_1, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("altitude");
    }
}
