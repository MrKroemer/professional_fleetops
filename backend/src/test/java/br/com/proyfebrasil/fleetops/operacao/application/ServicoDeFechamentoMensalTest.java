package br.com.proyfebrasil.fleetops.operacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeTabelasDePreco;
import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.domain.Abastecimento;
import br.com.proyfebrasil.fleetops.operacao.domain.RegistroDeKm;
import br.com.proyfebrasil.fleetops.operacao.domain.ServicoOperacional;
import br.com.proyfebrasil.fleetops.operacao.domain.StatusDoFechamento;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import br.com.proyfebrasil.fleetops.operacao.infra.AbastecimentoRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.FechamentoRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.RegistroDeKmRepository;
import br.com.proyfebrasil.fleetops.operacao.infra.ServicoOperacionalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("RN-06 e RN-21 — fechamento mensal derivado")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoDeFechamentoMensalTest {

    private static final Long CONTRATO = 7L;
    private static final YearMonth MARCO = YearMonth.of(2026, 3);

    @Mock private ContratoRepository contratos;
    @Mock private RegistroDeKmRepository registros;
    @Mock private AbastecimentoRepository abastecimentos;
    @Mock private ServicoOperacionalRepository servicos;
    @Mock private FechamentoRepository fechamentos;
    @Mock private ServicoDeTabelasDePreco tabelas;

    private ServicoDeFechamentoMensal servico;
    private ContratoDeLocacao contrato;
    private Locadora locadora;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(
                LocalDate.of(2026, 4, 5).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDeFechamentoMensal(
                contratos, registros, abastecimentos, servicos, fechamentos, tabelas, relogio);

        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        ReflectionTestUtils.setField(locadora, "id", 2L);
        contrato = new ContratoDeLocacao(
                new Obra("24.019", "SKER", "Uibaí", "BA"), locadora, LocalDate.of(2025, 1, 10));
        ReflectionTestUtils.setField(contrato, "id", CONTRATO);
        contrato.alterarDadosDaLocacao("24.019", "Uibaí - BA", 3000, new BigDecimal("2606.08"));
        contrato.colocarVeiculo(
                new Veiculo("ABC1D23", "Polo", CategoriaVeiculo.PASSEIO, Combustivel.FLEX, locadora),
                LocalDate.of(2025, 1, 10), null);

        when(contratos.buscarPorId(CONTRATO)).thenReturn(Optional.of(contrato));
        when(fechamentos.daCompetencia(anyLong(), any())).thenReturn(Optional.empty());
        when(registros.doPeriodo(anyLong(), any(), any())).thenReturn(List.of());
        when(abastecimentos.doPeriodo(anyLong(), any(), any())).thenReturn(List.of());
        when(servicos.doPeriodo(anyLong(), any(), any())).thenReturn(List.of());
        when(tabelas.valorKmExcedente(anyLong(), any(), any(), anyInt()))
                .thenReturn(Optional.of(new BigDecimal("0.89")));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private RegistroDeKm km(int dia, int inicial, int fim) {
        return new RegistroDeKm(contrato, MARCO.atDay(dia), inicial, fim);
    }

    private Abastecimento abastecimento(int dia, String valor) {
        return new Abastecimento(contrato, MARCO.atDay(dia), new BigDecimal(valor));
    }

    private ServicoOperacional servico(int dia, TipoDeServico tipo, String valor) {
        return new ServicoOperacional(contrato, tipo, MARCO.atDay(dia), new BigDecimal(valor));
    }

    @Test
    @DisplayName("RN06_kmPercorridaSaiDosExtremosDoHodometro_naoDaSomaDosTrechos")
    void percorridaPelosExtremos() {
        // Falta o registro do dia 15: somar os trechos daria 400 km e esconderia o buraco.
        // Pelos extremos, os 600 km que a locadora vai cobrar entram na conta.
        when(registros.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(km(1, 10_000, 10_200), km(20, 10_400, 10_600)));

        var fechamento = servico.apurar(CONTRATO, MARCO);

        assertThat(fechamento.kmInicial()).isEqualTo(10_000);
        assertThat(fechamento.kmFinal()).isEqualTo(10_600);
        assertThat(fechamento.kmPercorrido()).isEqualTo(600);
    }

    @Test
    @DisplayName("RN06_dentroDoPacoteNaoHaExcedente")
    void dentroDoPacote() {
        when(registros.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(km(1, 10_000, 12_500)));

        var fechamento = servico.apurar(CONTRATO, MARCO);

        assertThat(fechamento.kmPercorrido()).isEqualTo(2500);
        assertThat(fechamento.kmExcedente()).isZero();
        assertThat(fechamento.estourouOPacote()).isFalse();
        assertThat(fechamento.custoDoExcedente()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("RN06_acimaDoPacoteCalculaOExcedenteEOCustoPelaVigencia")
    void excedenteComCusto() {
        when(registros.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(km(1, 10_000, 13_450)));

        var fechamento = servico.apurar(CONTRATO, MARCO);

        // 3.450 rodados − 3.000 contratados = 450 km × R$ 0,89 = R$ 400,50
        assertThat(fechamento.kmPercorrido()).isEqualTo(3450);
        assertThat(fechamento.kmExcedente()).isEqualTo(450);
        assertThat(fechamento.valorDoKmExcedente()).isEqualByComparingTo("0.89");
        assertThat(fechamento.custoDoExcedente()).isEqualByComparingTo("400.50");
        assertThat(fechamento.estourouOPacote()).isTrue();
    }

    @Test
    @DisplayName("sem tabela de preços do ano, o excedente é conhecido mas o custo não é estimado")
    void semVigencia() {
        when(registros.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(km(1, 10_000, 13_450)));
        when(tabelas.valorKmExcedente(anyLong(), any(), any(), anyInt())).thenReturn(Optional.empty());

        var fechamento = servico.apurar(CONTRATO, MARCO);

        // Estimar zero seria afirmar que o excedente não custa nada.
        assertThat(fechamento.kmExcedente()).isEqualTo(450);
        assertThat(fechamento.vigenciaIndisponivel()).isTrue();
        assertThat(fechamento.valorDoKmExcedente()).isNull();
        assertThat(fechamento.custoDoExcedente()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ACEITE — o fechamento bate com a conferência manual")
    void conferenciaManual() {
        when(registros.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(km(2, 10_000, 11_200), km(18, 11_200, 13_450)));
        when(abastecimentos.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(abastecimento(3, "298.40"), abastecimento(12, "310.00"),
                        abastecimento(25, "287.55")));
        when(servicos.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(servico(7, TipoDeServico.LAVA_JATO, "60.00"),
                        servico(21, TipoDeServico.LAVA_JATO, "60.00"),
                        servico(14, TipoDeServico.BORRACHARIA, "250.00"),
                        servico(28, TipoDeServico.PARA_BRISAS, "480.00")));

        var f = servico.apurar(CONTRATO, MARCO);

        // Conferência à mão, item a item:
        //   KM ........... 13.450 − 10.000 = 3.450 km; 3.450 − 3.000 = 450 excedentes
        //   Excedente .... 450 × 0,89 ........................ R$   400,50
        //   Combustível .. 298,40 + 310,00 + 287,55 .......... R$   895,95
        //   Lava-jato .... 60,00 + 60,00 ..................... R$   120,00
        //   Borracharia ..................................... R$   250,00
        //   Para-brisas ..................................... R$   480,00
        //                                            total .. R$ 2.146,45
        assertThat(f.kmPercorrido()).isEqualTo(3450);
        assertThat(f.kmExcedente()).isEqualTo(450);
        assertThat(f.custoDoExcedente()).isEqualByComparingTo("400.50");
        assertThat(f.consumoTotal()).isEqualByComparingTo("895.95");
        assertThat(f.quantidadeDeAbastecimentos()).isEqualTo(3);
        assertThat(f.custoDeLavaJato()).isEqualByComparingTo("120.00");
        assertThat(f.custoDeBorracharia()).isEqualByComparingTo("250.00");
        assertThat(f.custoDeParaBrisas()).isEqualByComparingTo("480.00");
        assertThat(f.custoTotal()).isEqualByComparingTo("2146.45");
    }

    @Test
    @DisplayName("mês sem lançamento nenhum não quebra — apura zerado")
    void mesVazio() {
        var fechamento = servico.apurar(CONTRATO, MARCO);

        assertThat(fechamento.kmInicial()).isNull();
        assertThat(fechamento.kmPercorrido()).isZero();
        assertThat(fechamento.custoTotal()).isEqualByComparingTo("0");
        assertThat(fechamento.status()).isEqualTo(StatusDoFechamento.ABERTO);
    }

    @Test
    @DisplayName("os lançamentos não conformes são contados para a conferência")
    void contaNaoConformes() {
        var irregular = abastecimento(5, "300.00");
        irregular.registrarConformidade(true, "Posto fora da obra em viagem.");
        when(abastecimentos.doPeriodo(anyLong(), any(), any()))
                .thenReturn(List.of(abastecimento(3, "298.40"), irregular));

        assertThat(servico.apurar(CONTRATO, MARCO).lancamentosNaoConformes()).isEqualTo(1);
    }

    @Test
    @DisplayName("RN21_conferirNaoCongelaNumero_apenasRegistraQuemOlhou")
    void conferenciaNaoCongela() {
        when(fechamentos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var conferido = servico.conferir(CONTRATO, MARCO, "gestor@proyfebrasil.com.br", "Conferido com a planilha.");

        // A entidade não tem campo de total algum — não há o que congelar. Um lançamento
        // atrasado muda os números, e a conferência passa a estar desatualizada, o que é
        // visível; o total gravado ficaria errado, o que não seria.
        assertThat(conferido.estaConferido()).isTrue();
        assertThat(conferido.getConferidoPor()).isEqualTo("gestor@proyfebrasil.com.br");
        assertThat(conferido.getConferidoEm()).isNotNull();
    }
}
