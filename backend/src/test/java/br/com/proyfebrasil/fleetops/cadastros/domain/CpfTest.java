package br.com.proyfebrasil.fleetops.cadastros.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CPF do condutor")
class CpfTest {

    /** CPFs sinteticamente válidos, usados apenas em teste. */
    private static final String VALIDO = "52998224725";
    private static final String OUTRO_VALIDO = "16899535009";

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25", "529 982 247 25"})
    @DisplayName("remove a formatação e aceita CPFs válidos")
    void normaliza(String entrada) {
        assertThat(Cpf.normalizar(entrada)).isEqualTo(VALIDO);
        assertThat(Cpf.eValido(entrada)).isTrue();
    }

    @Test
    @DisplayName("aceita mais de um CPF válido")
    void aceitaOutros() {
        assertThat(Cpf.eValido(OUTRO_VALIDO)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "52998224724",   // dígito verificador incorreto
        "11111111111",   // sequência repetida
        "00000000000",
        "1234567890",    // dígitos de menos
        "123456789012",  // dígitos demais
    })
    @DisplayName("rejeita CPFs inválidos")
    void rejeita(String entrada) {
        assertThat(Cpf.eValido(entrada)).isFalse();
        assertThatThrownBy(() -> Cpf.normalizar(entrada)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita CPF nulo ou vazio")
    void rejeitaNulo() {
        assertThat(Cpf.eValido(null)).isFalse();
        assertThatThrownBy(() -> Cpf.normalizar(null)).hasMessageContaining("obrigatório");
        assertThatThrownBy(() -> Cpf.normalizar("  ")).hasMessageContaining("obrigatório");
    }

    @Test
    @DisplayName("formata o CPF para exibição")
    void formata() {
        assertThat(Cpf.formatar(VALIDO)).isEqualTo("529.982.247-25");
        assertThat(Cpf.formatar("123")).isEqualTo("123");
        assertThat(Cpf.formatar(null)).isNull();
    }
}
