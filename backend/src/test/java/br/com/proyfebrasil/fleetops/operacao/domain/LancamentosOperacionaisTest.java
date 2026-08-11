package br.com.proyfebrasil.fleetops.operacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Abastecimento e serviço — invariantes da não conformidade")
class LancamentosOperacionaisTest {

    private static final LocalDate DIA = LocalDate.of(2026, 3, 11);

    private ContratoDeLocacao contrato;

    @BeforeEach
    void preparar() {
        contrato = new ContratoDeLocacao(
                new Obra("24.019", "SKER", "Uibaí", "BA"),
                new Locadora("Unidas", TipoLocadora.NACIONAL),
                LocalDate.of(2025, 1, 10));
    }

    @Test
    @DisplayName("RN04_naoConformeSemJustificativaNaoEUmEstadoValido")
    void exigeJustificativa() {
        var abastecimento = new Abastecimento(contrato, DIA, new BigDecimal("300"));

        assertThatThrownBy(() -> abastecimento.registrarConformidade(true, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");

        var servico = new ServicoOperacional(contrato, TipoDeServico.LAVA_JATO, DIA, new BigDecimal("60"));
        assertThatThrownBy(() -> servico.registrarConformidade(true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("desmarcar a não conformidade apaga a justificativa que a sustentava")
    void desmarcarLimpa() {
        var abastecimento = new Abastecimento(contrato, DIA, new BigDecimal("300"));
        abastecimento.registrarConformidade(true, "Tanque na reserva em viagem.");
        assertThat(abastecimento.getJustificativa()).isNotNull();

        // Uma justificativa órfã afirmaria uma irregularidade que deixou de existir.
        abastecimento.registrarConformidade(false, null);
        assertThat(abastecimento.isNaoConforme()).isFalse();
        assertThat(abastecimento.getJustificativa()).isNull();
    }

    @Test
    @DisplayName("o preço por litro é derivado, e ausente quando não há litragem")
    void precoPorLitro() {
        var abastecimento = new Abastecimento(contrato, DIA, new BigDecimal("300"));

        assertThat(abastecimento.precoPorLitro()).isEmpty();
        abastecimento.alterarDados(null, new BigDecimal("50"), 10_500, null);
        assertThat(abastecimento.precoPorLitro())
                .hasValueSatisfying(preco -> assertThat(preco).isEqualByComparingTo("6.000"));
    }

    @Test
    @DisplayName("valores e litragem inválidos são recusados")
    void valoresInvalidos() {
        var abastecimento = new Abastecimento(contrato, DIA, new BigDecimal("300"));

        assertThatThrownBy(() -> abastecimento.alterarValor(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> abastecimento.alterarDados(null, BigDecimal.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("litros");
        assertThatThrownBy(() -> abastecimento.alterarDados(null, null, -5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RN05_soOLavaJatoTemLimiteSemanal")
    void limiteSemanal() {
        assertThat(TipoDeServico.LAVA_JATO.isLimitadoPorSemana()).isTrue();
        assertThat(TipoDeServico.BORRACHARIA.isLimitadoPorSemana()).isFalse();
        assertThat(TipoDeServico.PARA_BRISAS.isLimitadoPorSemana()).isFalse();
        // O tipo carrega o fornecedor esperado para que a avaliação não precise de switch.
        assertThat(TipoDeServico.LAVA_JATO.getFornecedorEsperado()).isEqualTo(TipoFornecedor.LAVA_JATO);
    }

    @Test
    @DisplayName("o serviço recusa valor negativo")
    void servicoNegativo() {
        var servico = new ServicoOperacional(contrato, TipoDeServico.BORRACHARIA, DIA, new BigDecimal("250"));

        assertThatThrownBy(() -> servico.alterarValor(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        servico.alterarDados(null, "Pneu dianteiro");
        assertThat(servico.getDescricao()).isEqualTo("Pneu dianteiro");
    }

    @Test
    @DisplayName("o fechamento registra quem conferiu e recusa conferir duas vezes")
    void conferencia() {
        var fechamento = new FechamentoMensal(contrato, java.time.YearMonth.of(2026, 3));
        var agora = java.time.Instant.parse("2026-04-05T10:00:00Z");

        assertThat(fechamento.estaConferido()).isFalse();
        fechamento.conferir("gestor@proyfebrasil.com.br", agora, "Conferido com a planilha.");
        assertThat(fechamento.estaConferido()).isTrue();

        assertThatThrownBy(() -> fechamento.conferir("outro", agora, null))
                .isInstanceOf(br.com.proyfebrasil.fleetops.shared.exception.NegocioException.class)
                .hasMessageContaining("já foi conferida");

        fechamento.reabrir();
        assertThat(fechamento.estaConferido()).isFalse();
        assertThat(fechamento.getConferidoEm()).isNull();
    }
}
