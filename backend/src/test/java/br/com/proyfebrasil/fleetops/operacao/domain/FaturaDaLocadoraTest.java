package br.com.proyfebrasil.fleetops.operacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-13 — fatura mensal e divergência")
class FaturaDaLocadoraTest {

    private static final YearMonth MARCO = YearMonth.of(2026, 3);

    private FaturaDaLocadora fatura;

    @BeforeEach
    void preparar() {
        var contrato = new ContratoDeLocacao(
                new Obra("24.019", "SKER", "Uibaí", "BA"),
                new Locadora("Unidas", TipoLocadora.NACIONAL),
                LocalDate.of(2025, 1, 10));
        fatura = new FaturaDaLocadora(contrato, MARCO);
    }

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("RN13_divergenciaEfaturadoMenosContratadoMaisExtras")
    void formulaDaDivergencia() {
        fatura.alterarValores(reais("2606.08"), reais("3006.08"), reais("0.00"));
        assertThat(fatura.getDivergencia()).isEqualByComparingTo("400.00");

        // Extras aprovados fazem parte do esperado: com eles, não há divergência.
        fatura.alterarValores(reais("2606.08"), reais("3006.08"), reais("400.00"));
        assertThat(fatura.getDivergencia()).isEqualByComparingTo("0.00");

        // Faturado a menos também é divergência — negativa, e igualmente digna de nota.
        fatura.alterarValores(reais("2606.08"), reais("2000.00"), reais("0.00"));
        assertThat(fatura.getDivergencia()).isEqualByComparingTo("-606.08");
    }

    @Test
    @DisplayName("RN13_naoPodeSerMarcadaComoOkComDivergencia")
    void okComDivergencia() {
        fatura.alterarValores(reais("2606.08"), reais("3006.08"), BigDecimal.ZERO);

        // Sem isto, uma fatura com R$ 400 a mais sairia da lista de pendências levando o
        // prejuízo junto.
        assertThatThrownBy(() -> fatura.alterarConferencia(StatusDeConferencia.OK, "conferido"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("400.00")
                .extracting(erro -> ((NegocioException) erro).codigoErro().codigo())
                .isEqualTo("RN-013-FATURA_DIVERGENTE_SEM_OBSERVACAO");
    }

    @Test
    @DisplayName("RN13_comDivergenciaExigeObservacao")
    void divergenciaExigeObservacao() {
        fatura.alterarValores(reais("2606.08"), reais("3006.08"), BigDecimal.ZERO);

        assertThatThrownBy(() -> fatura.alterarConferencia(StatusDeConferencia.EM_CONTESTACAO, "  "))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("exige observação");

        assertThatCode(() -> fatura.alterarConferencia(
                        StatusDeConferencia.EM_CONTESTACAO, "Cobrança de KM excedente sem demonstrativo."))
                .doesNotThrowAnyException();
        assertThat(fatura.getStatus()).isEqualTo(StatusDeConferencia.EM_CONTESTACAO);
    }

    @Test
    @DisplayName("RN13_pendenteComDivergenciaEhValido_porqueEOEstadoDeQuemAcabouDeLancar")
    void pendenteComDivergencia() {
        fatura.alterarValores(reais("2606.08"), reais("3006.08"), BigDecimal.ZERO);

        // A divergência é o que o sistema acaba de revelar; cobrar a explicação da
        // tratativa antes de o usuário ver o número inverteria a ordem dos fatos.
        assertThatCode(() -> fatura.alterarConferencia(StatusDeConferencia.PENDENTE, null))
                .doesNotThrowAnyException();
        assertThat(fatura.getStatus()).isEqualTo(StatusDeConferencia.PENDENTE);
        assertThat(fatura.exigeTratativa()).isTrue();
    }

    @Test
    @DisplayName("sem divergência, a fatura pode ser conferida sem observação")
    void semDivergencia() {
        fatura.alterarValores(reais("2606.08"), reais("2606.08"), BigDecimal.ZERO);

        fatura.alterarConferencia(StatusDeConferencia.OK, null);

        assertThat(fatura.getStatus()).isEqualTo(StatusDeConferencia.OK);
        assertThat(fatura.exigeTratativa()).isFalse();
    }

    @Test
    @DisplayName("uma fatura ajustada deixa de exigir tratativa, mesmo divergente")
    void ajustadaSaiDaFila() {
        fatura.alterarValores(reais("2606.08"), reais("3006.08"), BigDecimal.ZERO);
        fatura.alterarConferencia(StatusDeConferencia.AJUSTADA, "Locadora emitiu nota de crédito.");

        // A divergência permanece no registro — é o histórico do que foi cobrado a mais —,
        // mas a tratativa terminou.
        assertThat(fatura.getDivergencia()).isEqualByComparingTo("400.00");
        assertThat(fatura.exigeTratativa()).isFalse();
    }

    @Test
    @DisplayName("valores negativos são recusados")
    void valoresNegativos() {
        assertThatThrownBy(() -> fatura.alterarValores(reais("-1"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor contratado");
    }

    @Test
    @DisplayName("a competência é sempre o mês, sem dia solto")
    void competenciaEhOMes() {
        assertThat(fatura.getCompetencia()).isEqualTo(MARCO);
    }
}
