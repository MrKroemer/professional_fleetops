package br.com.proyfebrasil.fleetops.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("RN-19 — perfis de acesso")
class PerfilTest {

    @ParameterizedTest
    @EnumSource(Perfil.class)
    @DisplayName("toda autoridade usa o prefixo ROLE_ exigido pelo Spring Security")
    void autoridadeComPrefixo(Perfil perfil) {
        assertThat(perfil.authority()).isEqualTo("ROLE_" + perfil.name());
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    @DisplayName("todo perfil tem descrição em pt-BR para exibição")
    void descricaoPreenchida(Perfil perfil) {
        assertThat(perfil.getDescricao()).isNotBlank();
    }

    @Test
    @DisplayName("converte nomes conhecidos e rejeita desconhecidos")
    void conversao() {
        assertThat(Perfil.de("GESTOR_FROTA")).isEqualTo(Perfil.GESTOR_FROTA);
        assertThatThrownBy(() -> Perfil.de("SUPERVISOR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPERVISOR");
    }

    @Test
    @DisplayName("os três perfis previstos existem, e apenas eles")
    void perfisPrevistos() {
        assertThat(Perfil.values())
                .containsExactly(Perfil.ADMIN, Perfil.GESTOR_FROTA, Perfil.CONSULTA);
    }
}
