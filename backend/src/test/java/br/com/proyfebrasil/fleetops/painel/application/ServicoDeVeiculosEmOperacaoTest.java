package br.com.proyfebrasil.fleetops.painel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Veículos em operação — o modelo de leitura dos cards")
class ServicoDeVeiculosEmOperacaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);
    private static final String CPF = "52998224725";

    @Mock
    private ContratoRepository contratos;

    private ServicoDeVeiculosEmOperacao servico;
    private Locadora locadora;
    private Obra obra;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDeVeiculosEmOperacao(contratos, relogio);
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        obra = new Obra("24.019", "SKER Ventos", "Uibaí", "BA");
    }

    private ContratoDeLocacao contratoCom(Condutor condutor, Combustivel combustivel) {
        ContratoDeLocacao contrato = new ContratoDeLocacao(obra, locadora, LocalDate.of(2025, 1, 10));
        contrato.alterarDadosDaLocacao("24.019", "Uibaí - BA", 3000, null);
        // Um contrato pode nascer sem condutor definido — o carro fica na obra até alguém
        // assumir —, e nesse caso não há período de condutor a abrir.
        if (condutor != null) {
            contrato.colocarCondutor(condutor, LocalDate.of(2025, 1, 10), null);
        }
        Veiculo veiculo =
                new Veiculo("ABC1D23", "S10", CategoriaVeiculo.QUATRO_X_QUATRO, combustivel, locadora);
        veiculo.definirRastreador(true, "Recife GPS");
        contrato.colocarVeiculo(veiculo, LocalDate.of(2025, 1, 10), null);
        return contrato;
    }

    private Condutor condutorComCnh(LocalDate validade) {
        Condutor condutor = new Condutor("Ana Souza", CPF);
        condutor.alterarDadosBasicos("Ana Souza", CPF, "Engenheira", null, null);
        condutor.alterarCnh("123", "AB", validade);
        return condutor;
    }

    @Test
    @DisplayName("reúne veículo, obra e condutor em um único item")
    void reuneOsTresEixos() {
        when(contratos.ativosComVeiculo())
                .thenReturn(List.of(contratoCom(condutorComCnh(HOJE.plusYears(1)), Combustivel.DIESEL)));

        var itens = servico.listar();

        assertThat(itens).singleElement().satisfies(item -> {
            assertThat(item.placaFormatada()).isEqualTo("ABC-1D23");
            assertThat(item.condutorNome()).isEqualTo("Ana Souza");
            assertThat(item.condutorCargo()).isEqualTo("Engenheira");
            assertThat(item.obraCodigo()).isEqualTo("24.019");
            assertThat(item.obraNome()).isEqualTo("SKER Ventos");
            assertThat(item.pacoteKmContratado()).isEqualTo(3000);
            assertThat(item.possuiRastreador()).isTrue();
            // Diesel exige teste de fumaça preta na retirada (RN-09).
            assertThat(item.exigeTesteFumacaPreta()).isTrue();
        });
    }

    @Test
    @DisplayName("RN16_aSituacaoDaCnhVemAvaliadaNoProprioItem")
    void avaliaCnh() {
        when(contratos.ativosComVeiculo())
                .thenReturn(List.of(contratoCom(condutorComCnh(HOJE.minusDays(30)), Combustivel.FLEX)));

        var item = servico.listar().get(0);

        assertThat(item.cnhVencida()).isTrue();
        assertThat(item.cnhEmAlerta()).isTrue();
        assertThat(item.diasParaVencerCnh()).isEqualTo(-30L);
    }

    @Test
    @DisplayName("RN16_cnhVencendoDentroDaFaixaEntraEmAlertaSemEstarVencida")
    void cnhEmAlerta() {
        when(contratos.ativosComVeiculo())
                .thenReturn(List.of(contratoCom(condutorComCnh(HOJE.plusDays(20)), Combustivel.FLEX)));

        var item = servico.listar().get(0);

        assertThat(item.cnhVencida()).isFalse();
        assertThat(item.cnhEmAlerta()).isTrue();
        assertThat(item.diasParaVencerCnh()).isEqualTo(20L);
    }

    @Test
    @DisplayName("contrato sem condutor não quebra o card")
    void semCondutor() {
        when(contratos.ativosComVeiculo()).thenReturn(List.of(contratoCom(null, Combustivel.FLEX)));

        var item = servico.listar().get(0);

        assertThat(item.condutorNome()).isNull();
        assertThat(item.cnhValidade()).isNull();
        assertThat(item.cnhVencida()).isFalse();
        assertThat(item.cnhEmAlerta()).isFalse();
    }

    @Test
    @DisplayName("sem contratos ativos, a lista sai vazia")
    void semContratos() {
        when(contratos.ativosComVeiculo()).thenReturn(List.of());

        assertThat(servico.listar()).isEmpty();
    }
}
