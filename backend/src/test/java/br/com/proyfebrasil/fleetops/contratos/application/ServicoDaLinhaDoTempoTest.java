package br.com.proyfebrasil.fleetops.contratos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.CategoriaVeiculo;
import br.com.proyfebrasil.fleetops.cadastros.domain.Combustivel;
import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.domain.EventoDeContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.StatusContrato;
import br.com.proyfebrasil.fleetops.contratos.domain.TipoDeEvento;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.contratos.infra.EventoDeContratoRepository;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.time.LocalDate;
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

@DisplayName("Linha do tempo do contrato (Fase 2)")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoDaLinhaDoTempoTest {

    private static final Long ID = 7L;
    private static final LocalDate RETIRADA = LocalDate.of(2025, 1, 10);
    private static final LocalDate TROCA = LocalDate.of(2025, 6, 1);

    @Mock
    private ContratoRepository contratos;

    @Mock
    private EventoDeContratoRepository eventos;

    private ServicoDaLinhaDoTempo servico;
    private ContratoDeLocacao contrato;
    private Locadora locadora;
    private Condutor ana;
    private Condutor bruno;

    @BeforeEach
    void preparar() {
        servico = new ServicoDaLinhaDoTempo(contratos, eventos);
        locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        contrato = new ContratoDeLocacao(new Obra("24.019", "SKER", "Uibaí", "BA"), locadora, RETIRADA);
        ReflectionTestUtils.setField(contrato, "id", ID);
        contrato.alterarDadosDaLocacao("24.019", "Uibaí - BA", 3000, null);

        ana = condutor("Ana Souza", "52998224725");
        bruno = condutor("Bruno Lima", "11144477735");

        contrato.colocarVeiculo(veiculo("ABC1D23", "Polo"), RETIRADA, "Retirada inicial");
        contrato.colocarCondutor(ana, RETIRADA, null);
        contrato.colocarVeiculo(veiculo("XYZ9K87", "Tracker"), TROCA, "Avaria");
        contrato.colocarCondutor(bruno, TROCA, "Férias");

        when(contratos.buscarPorId(ID)).thenReturn(Optional.of(contrato));
        when(eventos.doContrato(ID)).thenReturn(List.of());
    }

    private Veiculo veiculo(String placa, String modelo) {
        return new Veiculo(placa, modelo, CategoriaVeiculo.PASSEIO, Combustivel.FLEX, locadora);
    }

    private static Condutor condutor(String nome, String cpf) {
        Condutor condutor = new Condutor(nome, cpf);
        condutor.alterarCnh("123", "AD", LocalDate.of(2030, 1, 1));
        return condutor;
    }

    @Test
    @DisplayName("funde veículos, condutores e eventos em uma lista só")
    void fundeAsTresHistorias() {
        var evento = new EventoDeContrato(contrato, TipoDeEvento.RETIRADA, veiculo("ABC1D23", "Polo"), RETIRADA);
        when(eventos.doContrato(ID)).thenReturn(List.of(evento));

        var linha = servico.montar(ID);

        // Duas placas, dois condutores e um evento — o gestor pergunta "o que aconteceu
        // com este contrato", não "quais foram as substituições".
        assertThat(linha.marcos()).hasSize(5);
        assertThat(linha.marcos()).extracting("tipo")
                .contains("VEICULO_INICIAL", "SUBSTITUICAO_VEICULO",
                        "CONDUTOR_INICIAL", "TROCA_CONDUTOR", "EVENTO_RETIRADA");
    }

    @Test
    @DisplayName("o primeiro período é rotulado como retirada, e não como substituição")
    void distingueOPrimeiroPeriodo() {
        var linha = servico.montar(ID);

        var primeiroVeiculo = linha.marcos().stream()
                .filter(marco -> marco.tipo().equals("VEICULO_INICIAL")).findFirst().orElseThrow();
        // Chamar o veículo original de "substituição" contaria uma troca que não houve.
        assertThat(primeiroVeiculo.rotulo()).isEqualTo("ABC-1D23");
        assertThat(primeiroVeiculo.data()).isEqualTo(RETIRADA);
        assertThat(primeiroVeiculo.fim()).isEqualTo(TROCA.minusDays(1));
        assertThat(linha.quantidadeDeSubstituicoes()).isEqualTo(1);
        assertThat(linha.quantidadeDeTrocasDeCondutor()).isEqualTo(1);
    }

    @Test
    @DisplayName("os marcos saem do mais recente para o mais antigo")
    void ordenaDoRecenteParaOAntigo() {
        var datas = servico.montar(ID).marcos().stream().map(marco -> marco.data()).toList();

        assertThat(datas).isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(datas.get(datas.size() - 1)).isEqualTo(RETIRADA);
    }

    @Test
    @DisplayName("o resumo mostra o estado de hoje, não o do início")
    void resumoTrazOEstadoAtual() {
        var linha = servico.montar(ID);

        assertThat(linha.veiculoAtual()).isEqualTo("XYZ-9K87");
        assertThat(linha.condutorAtual()).isEqualTo("Bruno Lima");
        assertThat(linha.status()).isEqualTo(StatusContrato.ATIVO);
    }

    @Test
    @DisplayName("RN18_aConsultaPorDataResponderPeloHistorico_naoPeloPonteiro")
    void consultaPorData() {
        var emMarco = servico.situacaoEm(ID, LocalDate.of(2025, 3, 15));

        // O ponteiro do contrato já aponta para o Tracker e o Bruno; a resposta correta
        // para março é a anterior aos dois.
        assertThat(emMarco.placa()).isEqualTo("ABC-1D23");
        assertThat(emMarco.condutor()).isEqualTo("Ana Souza");
        assertThat(emMarco.vigente()).isTrue();
    }

    @Test
    @DisplayName("data anterior ao contrato devolve não vigente, em vez de campos vazios sem explicação")
    void consultaAntesDoContrato() {
        var antes = servico.situacaoEm(ID, LocalDate.of(2024, 12, 1));

        assertThat(antes.vigente()).isFalse();
        assertThat(antes.placa()).isNull();
        assertThat(antes.condutor()).isNull();
    }

    @Test
    @DisplayName("contrato inexistente resulta em 404, não em página vazia")
    void contratoInexistente() {
        when(contratos.buscarPorId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.montar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> servico.situacaoEm(99L, RETIRADA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
