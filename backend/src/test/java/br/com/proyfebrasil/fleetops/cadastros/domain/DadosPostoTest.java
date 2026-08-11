package br.com.proyfebrasil.fleetops.cadastros.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-04 (base) — dias autorizados de abastecimento")
class DadosPostoTest {

    @Test
    @DisplayName("RN04_deveAutorizarApenasNosDiasCadastrados")
    void autorizaApenasNosDiasCadastrados() {
        DadosPosto posto = new DadosPosto(Set.of(DiaDaSemana.TER, DiaDaSemana.QUI, DiaDaSemana.SAB), null);

        assertThat(posto.autorizaAbastecimentoEm(DayOfWeek.TUESDAY)).isTrue();
        assertThat(posto.autorizaAbastecimentoEm(DayOfWeek.THURSDAY)).isTrue();
        assertThat(posto.autorizaAbastecimentoEm(DayOfWeek.SATURDAY)).isTrue();
        assertThat(posto.autorizaAbastecimentoEm(DayOfWeek.MONDAY)).isFalse();
        assertThat(posto.autorizaAbastecimentoEm(DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    @DisplayName("RN04_semDiasCadastradosQualquerDiaEAutorizado")
    void semRestricao() {
        // Restrição precisa ser declarada: a ausência de dados não pode bloquear a operação.
        DadosPosto posto = new DadosPosto(Set.of(), null);

        for (DayOfWeek dia : DayOfWeek.values()) {
            assertThat(posto.autorizaAbastecimentoEm(dia)).isTrue();
        }
        assertThat(posto.getDiasAutorizados()).isEmpty();
    }

    @Test
    @DisplayName("os dias são persistidos e relidos na ordem natural da semana")
    void serializacaoEstavel() {
        Set<DiaDaSemana> foraDeOrdem = new LinkedHashSet<>();
        foraDeOrdem.add(DiaDaSemana.SAB);
        foraDeOrdem.add(DiaDaSemana.TER);
        foraDeOrdem.add(DiaDaSemana.QUI);

        assertThat(DiaDaSemana.serializar(foraDeOrdem)).isEqualTo("TER,QUI,SAB");
        assertThat(DiaDaSemana.interpretar("SAB,TER,QUI"))
                .containsExactlyInAnyOrder(DiaDaSemana.TER, DiaDaSemana.QUI, DiaDaSemana.SAB);
    }

    @Test
    @DisplayName("texto vazio ou nulo é interpretado como conjunto vazio")
    void vazio() {
        assertThat(DiaDaSemana.interpretar(null)).isEmpty();
        assertThat(DiaDaSemana.interpretar("")).isEmpty();
        assertThat(DiaDaSemana.interpretar("   ")).isEmpty();
        assertThat(DiaDaSemana.serializar(null)).isEmpty();
    }

    @Test
    @DisplayName("converte os dias do calendário para as siglas do domínio")
    void conversao() {
        assertThat(DiaDaSemana.de(DayOfWeek.MONDAY)).isEqualTo(DiaDaSemana.SEG);
        assertThat(DiaDaSemana.de(DayOfWeek.SUNDAY)).isEqualTo(DiaDaSemana.DOM);
        assertThat(DiaDaSemana.TER.getEquivalente()).isEqualTo(DayOfWeek.TUESDAY);
    }
}
