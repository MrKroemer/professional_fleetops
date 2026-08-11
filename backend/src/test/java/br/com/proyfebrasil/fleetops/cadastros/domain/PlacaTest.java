package br.com.proyfebrasil.fleetops.cadastros.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RN-02 — placa normalizada e validada")
class PlacaTest {

    @ParameterizedTest
    @CsvSource({
        "ABC1234, ABC1234",
        "abc1234, ABC1234",
        "ABC-1234, ABC1234",
        "'  abc-1234  ', ABC1234",
        "ABC1D23, ABC1D23",
        "abc1d23, ABC1D23",
        "ABC-1D23, ABC1D23",
        "ABC.1D23, ABC1D23",
        "'ABC 1D23', ABC1D23",
    })
    @DisplayName("RN02_deveNormalizarPlacasDosDoisFormatosParaCaixaAltaSemSeparadores")
    void normaliza(String entrada, String esperada) {
        assertThat(Placa.normalizar(entrada)).isEqualTo(esperada);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "AB1234",      // letras de menos
        "ABCD123",     // letra na quarta posição
        "ABC12345",    // dígitos demais
        "1BC1234",     // dígito na primeira posição
        "ABC1DD3",     // duas letras no miolo
        "ABC1D2E",     // letra na última posição
        "  ",
    })
    @DisplayName("RN02_deveRejeitarPlacasForaDosFormatosAceitos")
    void rejeitaInvalidas(String entrada) {
        assertThat(Placa.eValida(entrada)).isFalse();
        assertThatThrownBy(() -> Placa.normalizar(entrada)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RN02_deveRejeitarPlacaNula")
    void rejeitaNula() {
        assertThat(Placa.eValida(null)).isFalse();
        assertThatThrownBy(() -> Placa.normalizar(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatória");
    }

    @Test
    @DisplayName("distingue o padrão Mercosul do antigo")
    void identificaMercosul() {
        assertThat(Placa.eMercosul("ABC1D23")).isTrue();
        assertThat(Placa.eMercosul("ABC1234")).isFalse();
    }

    @Test
    @DisplayName("formata a placa para exibição")
    void formata() {
        assertThat(Placa.formatar("ABC1D23")).isEqualTo("ABC-1D23");
        assertThat(Placa.formatar("ABC1234")).isEqualTo("ABC-1234");
        assertThat(Placa.formatar(null)).isNull();
    }

    @Test
    @DisplayName("a mensagem de erro mostra o valor recebido e os formatos aceitos")
    void mensagemDeErroOrienta() {
        assertThatThrownBy(() -> Placa.normalizar("XPTO"))
                .hasMessageContaining("XPTO")
                .hasMessageContaining("Mercosul")
                .hasMessageContaining("ABC1234");
    }
}
