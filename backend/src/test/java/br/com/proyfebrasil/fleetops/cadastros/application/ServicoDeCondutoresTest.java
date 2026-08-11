package br.com.proyfebrasil.fleetops.cadastros.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores.DadosDoCondutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.StatusCondutor;
import br.com.proyfebrasil.fleetops.cadastros.infra.CondutorRepository;
import br.com.proyfebrasil.fleetops.cadastros.infra.ObraRepository;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RN-16 — alerta e bloqueio por validade de CNH")
class ServicoDeCondutoresTest {

    private static final String CPF = "52998224725";
    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);

    @Mock
    private CondutorRepository condutores;

    @Mock
    private ObraRepository obras;

    private ServicoDeCondutores servico;

    @BeforeEach
    void preparar() {
        // Relógio fixo: sem ele, um teste de vencimento passaria hoje e falharia amanhã.
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDeCondutores(condutores, obras, relogio);
    }

    private Condutor condutorCom(LocalDate validade) {
        Condutor condutor = new Condutor("Ana Souza", CPF);
        ReflectionTestUtils.setField(condutor, "id", 3L);
        condutor.alterarCnh("123", "AB", validade);
        return condutor;
    }

    @Test
    @DisplayName("RN16_deveBloquearVinculoAContratoComCnhVencida")
    void bloqueiaVinculoComCnhVencida() {
        when(condutores.buscarPorId(3L)).thenReturn(Optional.of(condutorCom(HOJE.minusDays(1))));

        assertThatThrownBy(() -> servico.exigirAptidaoParaContrato(3L))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Ana Souza")
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.CNH_VENCIDA);
    }

    @Test
    @DisplayName("RN16_devePermitirVinculoAContratoComCnhValida")
    void permiteVinculoComCnhValida() {
        when(condutores.buscarPorId(3L)).thenReturn(Optional.of(condutorCom(HOJE.plusMonths(6))));

        assertThatCode(() -> servico.exigirAptidaoParaContrato(3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN16_deveCalcularOsDiasRestantesUsandoORelogioDaAplicacao")
    void calculaDiasRestantes() {
        when(condutores.buscarPorId(3L)).thenReturn(Optional.of(condutorCom(HOJE.plusDays(45))));

        var situacao = servico.avaliarCnh(3L);

        assertThat(situacao.diasParaVencer()).isEqualTo(45L);
        assertThat(situacao.vencida()).isFalse();
        assertThat(situacao.emAlerta()).isTrue();
    }

    @Test
    @DisplayName("RN16_deveListarCondutoresComCnhVencendoDentroDaFaixaDeAlerta")
    void listaEmAlerta() {
        Condutor vencendo = condutorCom(HOJE.plusDays(20));
        Condutor vencido = condutorCom(HOJE.minusDays(5));
        when(condutores.comCnhVencendoAte(HOJE.plusDays(60))).thenReturn(List.of(vencido, vencendo));

        var alertas = servico.comCnhEmAlerta();

        assertThat(alertas).hasSize(2);
        assertThat(alertas.get(0).vencida()).isTrue();
        assertThat(alertas.get(1).emAlerta()).isTrue();
        assertThat(alertas.get(1).vencida()).isFalse();
    }

    @Test
    @DisplayName("recusa CPF inválido com código de erro estável")
    void recusaCpfInvalido() {
        DadosDoCondutor dados = new DadosDoCondutor(
                "Ana", "11111111111", null, null, null, null, null, null, null, StatusCondutor.ATIVO, null);

        assertThatThrownBy(() -> servico.criar(dados))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.CPF_INVALIDO);
        verify(condutores, never()).save(any());
    }

    @Test
    @DisplayName("recusa CPF já cadastrado em outro condutor")
    void recusaCpfDuplicado() {
        when(condutores.existeOutroComCpf(CPF, null)).thenReturn(true);
        DadosDoCondutor dados = new DadosDoCondutor(
                "Ana", CPF, null, null, null, null, null, null, null, StatusCondutor.ATIVO, null);

        assertThatThrownBy(() -> servico.criar(dados))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.CPF_DUPLICADO);
    }
}
