package br.com.proyfebrasil.fleetops.cadastros.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos.DadosDoVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.cadastros.infra.LocadoraRepository;
import br.com.proyfebrasil.fleetops.cadastros.infra.VeiculoRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RN-02 — normalização e unicidade de placa")
class ServicoDeVeiculosTest {

    @Mock
    private VeiculoRepository veiculos;

    @Mock
    private LocadoraRepository locadoras;

    @InjectMocks
    private ServicoDeVeiculos servico;

    private Locadora locadora;

    @BeforeEach
    void preparar() {
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        ReflectionTestUtils.setField(locadora, "id", 5L);
    }

    private DadosDoVeiculo dadosCom(String placa) {
        return new DadosDoVeiculo(
                placa, "S10", "Chevrolet", 2024,
                CategoriaVeiculo.QUATRO_X_QUATRO, Combustivel.DIESEL, 5L,
                "J", "24019-01", true, "Recife GPS", true, StatusVeiculo.DISPONIVEL, null);
    }

    @Test
    @DisplayName("RN02_deveNormalizarAPlacaAntesDeGravar")
    void normalizaAoCriar() {
        when(locadoras.buscarPorId(5L)).thenReturn(Optional.of(locadora));
        when(veiculos.existeOutroComPlaca("ABC1D23", null)).thenReturn(false);
        when(veiculos.save(any(Veiculo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Veiculo criado = servico.criar(dadosCom("  abc-1d23 "));

        assertThat(criado.getPlaca()).isEqualTo("ABC1D23");
    }

    @Test
    @DisplayName("RN02_deveRecusarPlacaJaCadastradaEmOutroVeiculo")
    void recusaDuplicada() {
        when(veiculos.existeOutroComPlaca("ABC1D23", null)).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(dadosCom("abc1d23")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ABC-1D23")
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.PLACA_DUPLICADA);
        verify(veiculos, never()).save(any());
    }

    @Test
    @DisplayName("RN02_aVerificacaoDeDuplicidadeIgnoraDiferencasDeGrafia")
    void duplicidadeIgnoraGrafia() {
        // "abc-1d23" e "ABC1D23" são a mesma placa; a checagem ocorre sobre a forma normalizada.
        when(veiculos.existeOutroComPlaca("ABC1D23", null)).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(dadosCom("ABC 1D23")))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    @DisplayName("RN02_deveRecusarPlacaEmFormatoInvalidoComErroDeNegocio")
    void recusaFormatoInvalido() {
        assertThatThrownBy(() -> servico.criar(dadosCom("XPTO123")))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.PLACA_INVALIDA);
        verify(veiculos, never()).save(any());
    }

    @Test
    @DisplayName("RN02_aAtualizacaoNaoConflitaComOProprioVeiculo")
    void atualizacaoNaoConflitaConsigo() {
        Veiculo existente = new Veiculo(
                "ABC1D23", "S10", CategoriaVeiculo.QUATRO_X_QUATRO, Combustivel.DIESEL, locadora);
        ReflectionTestUtils.setField(existente, "id", 9L);
        when(veiculos.buscarPorId(9L)).thenReturn(Optional.of(existente));
        when(veiculos.existeOutroComPlaca("ABC1D23", 9L)).thenReturn(false);
        when(locadoras.buscarPorId(5L)).thenReturn(Optional.of(locadora));

        Veiculo atualizado = servico.atualizar(9L, dadosCom("abc-1d23"));

        assertThat(atualizado.getPlaca()).isEqualTo("ABC1D23");
    }

    @Test
    @DisplayName("a busca por placa aceita qualquer grafia")
    void buscaPorPlacaNormaliza() {
        Veiculo existente = new Veiculo(
                "ABC1D23", "S10", CategoriaVeiculo.QUATRO_X_QUATRO, Combustivel.DIESEL, locadora);
        when(veiculos.buscarPorPlaca("ABC1D23")).thenReturn(Optional.of(existente));

        assertThat(servico.buscarPorPlaca("abc-1d23")).isSameAs(existente);
    }

    @Test
    @DisplayName("a exclusão é lógica")
    void exclusaoLogica() {
        Veiculo existente = new Veiculo(
                "ABC1D23", "S10", CategoriaVeiculo.SUV, Combustivel.FLEX, locadora);
        when(veiculos.buscarPorId(9L)).thenReturn(Optional.of(existente));

        servico.excluir(9L);

        assertThat(existente.isExcluida()).isTrue();
        verify(veiculos, never()).delete(any());
    }
}
