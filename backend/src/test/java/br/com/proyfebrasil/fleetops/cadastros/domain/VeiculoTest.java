package br.com.proyfebrasil.fleetops.cadastros.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Veículo — invariantes de domínio")
class VeiculoTest {

    private static final Locadora LOCADORA = new Locadora("Unidas", TipoLocadora.NACIONAL);

    private Veiculo veiculoCom(Combustivel combustivel) {
        return new Veiculo("abc-1d23", "S10", CategoriaVeiculo.QUATRO_X_QUATRO, combustivel, LOCADORA);
    }

    @Test
    @DisplayName("RN02_deveArmazenarAPlacaNormalizada")
    void placaNormalizada() {
        assertThat(veiculoCom(Combustivel.DIESEL).getPlaca()).isEqualTo("ABC1D23");
        assertThat(veiculoCom(Combustivel.DIESEL).getPlacaFormatada()).isEqualTo("ABC-1D23");
    }

    @Test
    @DisplayName("RN09_deveExigirTesteDeFumacaPretaApenasParaVeiculosADiesel")
    void fumacaPreta() {
        assertThat(veiculoCom(Combustivel.DIESEL).exigeTesteDeFumacaPreta()).isTrue();
        assertThat(veiculoCom(Combustivel.FLEX).exigeTesteDeFumacaPreta()).isFalse();
        assertThat(veiculoCom(Combustivel.GASOLINA).exigeTesteDeFumacaPreta()).isFalse();
    }

    @Test
    @DisplayName("remover o rastreador limpa o fornecedor, evitando cadastro contraditório")
    void rastreadorRemovidoLimpaFornecedor() {
        Veiculo veiculo = veiculoCom(Combustivel.DIESEL);
        veiculo.definirRastreador(true, "Recife GPS");
        assertThat(veiculo.getFornecedorRastreador()).isEqualTo("Recife GPS");

        veiculo.definirRastreador(false, "Recife GPS");

        assertThat(veiculo.isPossuiRastreador()).isFalse();
        assertThat(veiculo.getFornecedorRastreador()).isNull();
    }

    @Test
    @DisplayName("nasce disponível")
    void nasceDisponivel() {
        assertThat(veiculoCom(Combustivel.FLEX).getStatus()).isEqualTo(StatusVeiculo.DISPONIVEL);
    }

    @Test
    @DisplayName("rejeita ano de fabricação fora da faixa aceita")
    void rejeitaAnoInvalido() {
        Veiculo veiculo = veiculoCom(Combustivel.FLEX);

        assertThatThrownBy(() -> veiculo.alterarIdentificacao("ABC1D23", "S10", "Chevrolet", 1900))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ano de fabricação");
    }

    @Test
    @DisplayName("exige locadora, categoria e combustível")
    void exigeObrigatorios() {
        assertThatThrownBy(() ->
                        new Veiculo("ABC1D23", "S10", CategoriaVeiculo.SUV, Combustivel.FLEX, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("locadora");
        assertThatThrownBy(() -> new Veiculo("ABC1D23", "  ", CategoriaVeiculo.SUV, Combustivel.FLEX, LOCADORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelo");
    }
}
