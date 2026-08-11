package br.com.proyfebrasil.fleetops.operacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-10 — uso particular")
class UsoParticularTest {

    private static final LocalDate SEXTA = LocalDate.of(2026, 3, 13);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 3, 15);

    private UsoParticular uso;

    @BeforeEach
    void preparar() {
        var contrato = new ContratoDeLocacao(
                new Obra("24.019", "SKER", "Uibaí", "BA"),
                new Locadora("Unidas", TipoLocadora.NACIONAL),
                LocalDate.of(2025, 1, 10));
        uso = new UsoParticular(
                contrato, new Condutor("Ana Souza", "52998224725"),
                TipoDeUsoParticular.USO_PONTUAL, SEXTA, DOMINGO);
    }

    @Test
    @DisplayName("RN10_oTetoEDeMilKmPorPeriodo")
    void tetoDeKm() {
        assertThat(UsoParticular.LIMITE_DE_KM).isEqualTo(1000);
        assertThat(uso.getKmAutorizado()).isEqualTo(1000);

        assertThatCode(() -> uso.autorizarKm(600)).doesNotThrowAnyException();
        assertThat(uso.getKmAutorizado()).isEqualTo(600);

        assertThatThrownBy(() -> uso.autorizarKm(1500))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("1000 km")
                .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                .isEqualTo("RN-010-USO_PARTICULAR_ACIMA_DO_LIMITE");
    }

    @Test
    @DisplayName("RN10_conducaoDepoisDasVinteHorasEProibida")
    void horarioLimite() {
        assertThat(UsoParticular.HORARIO_LIMITE).isEqualTo(LocalTime.of(20, 0));

        // Às 20:00 em ponto ainda é permitido; a regra diz "após 20:00".
        assertThat(UsoParticular.horarioProibido(LocalTime.of(20, 0))).isFalse();
        assertThat(UsoParticular.horarioProibido(LocalTime.of(19, 59))).isFalse();
        assertThat(UsoParticular.horarioProibido(LocalTime.of(20, 1))).isTrue();
        assertThat(UsoParticular.horarioProibido(LocalTime.of(23, 30))).isTrue();
        assertThat(UsoParticular.horarioProibido(null)).isFalse();
    }

    @Test
    @DisplayName("RN10_semAceiteAautorizacaoNaoVale")
    void aceiteEcondicaoDeValidade() {
        // A RN-10 responsabiliza o condutor por multas e custos do período —
        // responsabilização que só se sustenta se ele declarou conhecer as regras.
        assertThat(uso.estaValida()).isFalse();

        uso.registrarAceite(Instant.parse("2026-03-12T18:00:00Z"));

        assertThat(uso.estaValida()).isTrue();
        assertThat(uso.getAceiteEm()).isEqualTo(Instant.parse("2026-03-12T18:00:00Z"));
    }

    @Test
    @DisplayName("o excedente só é apurável depois do período, com o rodado informado")
    void excedente() {
        uso.autorizarKm(500);

        assertThat(uso.excedeuOAutorizado()).isFalse();
        assertThat(uso.kmExcedido()).isZero();

        uso.registrarKmPercorrido(480);
        assertThat(uso.excedeuOAutorizado()).isFalse();

        uso.registrarKmPercorrido(620);
        assertThat(uso.excedeuOAutorizado()).isTrue();
        assertThat(uso.kmExcedido()).isEqualTo(120);
    }

    @Test
    @DisplayName("o período cobre as datas de início e fim, inclusive")
    void cobertura() {
        assertThat(uso.cobre(SEXTA)).isTrue();
        assertThat(uso.cobre(DOMINGO)).isTrue();
        assertThat(uso.cobre(SEXTA.minusDays(1))).isFalse();
        assertThat(uso.cobre(DOMINGO.plusDays(1))).isFalse();
        assertThat(uso.cobre(null)).isFalse();
    }

    @Test
    @DisplayName("fim anterior ao início é recusado")
    void periodoInvertido() {
        var contrato = new ContratoDeLocacao(
                new Obra("24.019", "SKER", "Uibaí", "BA"),
                new Locadora("Unidas", TipoLocadora.NACIONAL), SEXTA);
        var condutor = new Condutor("Ana Souza", "52998224725");

        assertThatThrownBy(() -> new UsoParticular(
                        contrato, condutor, TipoDeUsoParticular.USO_PONTUAL, DOMINGO, SEXTA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior ao início");
    }
}
