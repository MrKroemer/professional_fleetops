package br.com.proyfebrasil.fleetops.operacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-03 — invariantes do registro de quilometragem")
class RegistroDeKmTest {

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
    @DisplayName("RN03_oFinalNaoPodeSerMenorQueOInicial")
    void finalMenorQueInicial() {
        assertThatThrownBy(() -> new RegistroDeKm(contrato, DIA, 10_500, 10_100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ser menor");
    }

    @Test
    @DisplayName("quilometragem negativa é recusada")
    void negativo() {
        assertThatThrownBy(() -> new RegistroDeKm(contrato, DIA, -1, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativa");
    }

    @Test
    @DisplayName("o percorrido é derivado, e um dia parado vale zero")
    void percorrido() {
        assertThat(new RegistroDeKm(contrato, DIA, 10_000, 10_240).kmPercorrido()).isEqualTo(240);
        // Igual não é erro: o carro pode ter ficado na obra o dia inteiro.
        assertThat(new RegistroDeKm(contrato, DIA, 10_000, 10_000).kmPercorrido()).isZero();
    }

    @Test
    @DisplayName("os dados acessórios são opcionais e não bloqueiam o lançamento")
    void acessorios() {
        var registro = new RegistroDeKm(contrato, DIA, 10_000, 10_240);

        assertThat(registro.getCondutor()).isEmpty();
        registro.definirCondutor(new Condutor("Ana Souza", "52998224725"));
        registro.alterarTrajeto("Uibaí", "Salvador", "Viagem de inspeção");
        registro.alterarData(DIA.plusDays(1));

        assertThat(registro.getCondutor()).isPresent();
        assertThat(registro.getOrigem()).isEqualTo("Uibaí");
        assertThat(registro.getDestino()).isEqualTo("Salvador");
        assertThat(registro.getObservacao()).isEqualTo("Viagem de inspeção");
        assertThat(registro.getData()).isEqualTo(DIA.plusDays(1));
    }

    @Test
    @DisplayName("corrigir a quilometragem revalida a invariante")
    void correcaoRevalida() {
        var registro = new RegistroDeKm(contrato, DIA, 10_000, 10_240);

        assertThatThrownBy(() -> registro.alterarQuilometragem(10_500, 10_100))
                .isInstanceOf(IllegalArgumentException.class);
        registro.alterarQuilometragem(10_000, 10_900);
        assertThat(registro.kmPercorrido()).isEqualTo(900);
    }
}
