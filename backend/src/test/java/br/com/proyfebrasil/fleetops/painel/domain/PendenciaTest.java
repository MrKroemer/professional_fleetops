package br.com.proyfebrasil.fleetops.painel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-23 — pendências e severidade")
class PendenciaTest {

    @Test
    @DisplayName("RN23_aOrdemDeclaradaDaSeveridadeEAOrdemDeExibicao")
    void ordemDaSeveridade() {
        // A central ordena por esta enumeração: o que exige ação hoje vem antes do
        // que apenas convém resolver. Inverter a ordem de declaração quebraria isso
        // silenciosamente, então o teste a fixa.
        assertThat(Severidade.values())
                .containsExactly(Severidade.CRITICA, Severidade.ATENCAO, Severidade.INFORMATIVA);
    }

    @Test
    @DisplayName("cada severidade explica o que significa, para a interface não inventar o texto")
    void severidadeDescreveSeuSignificado() {
        for (Severidade severidade : Severidade.values()) {
            assertThat(severidade.getDescricao()).isNotBlank();
            assertThat(severidade.getSignificado()).isNotBlank();
        }
    }

    @Test
    @DisplayName("RN23_todaPendenciaApontaARegraQueAOrigina")
    void tipoApontaARegra() {
        // Sem a regra, o usuário vê um aviso sem saber de onde ele vem nem por que importa.
        for (TipoDePendencia tipo : TipoDePendencia.values()) {
            assertThat(tipo.getDescricao()).isNotBlank();
            assertThat(tipo.getRegra()).matches("RN-\\d{2}");
        }
    }

    @Test
    @DisplayName("ordenar por severidade agrupa as críticas no topo")
    void ordenacaoAgrupaCriticas() {
        List<Pendencia> desordenadas = Arrays.asList(
                pendencia(Severidade.INFORMATIVA, "Cadastro incompleto"),
                pendencia(Severidade.CRITICA, "CNH vencida"),
                pendencia(Severidade.ATENCAO, "CNH vencendo"));

        desordenadas.sort(Comparator.comparingInt(p -> p.severidade().ordinal()));

        assertThat(desordenadas)
                .extracting(Pendencia::severidade)
                .containsExactly(Severidade.CRITICA, Severidade.ATENCAO, Severidade.INFORMATIVA);
    }

    @Test
    @DisplayName("recusa pendência sem tipo, severidade ou título")
    void exigeCamposEssenciais() {
        assertThatThrownBy(() -> new Pendencia(null, Severidade.CRITICA, "t", "d", null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Pendencia(TipoDePendencia.CNH_VENCIDA, null, "t", "d", null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new Pendencia(TipoDePendencia.CNH_VENCIDA, Severidade.CRITICA, null, "d", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a referência é opcional: nem toda pendência aponta um registro único")
    void referenciaOpcional() {
        Pendencia agregada = new Pendencia(
                TipoDePendencia.VEICULO_SEM_GRUPO_TARIFARIO,
                Severidade.INFORMATIVA,
                "40 veículos sem grupo tarifário",
                "detalhe",
                "/cadastros/veiculos",
                null);

        assertThat(agregada.referencia()).isNull();
        assertThat(agregada.recurso()).isEqualTo("/cadastros/veiculos");
    }

    private static Pendencia pendencia(Severidade severidade, String titulo) {
        return new Pendencia(TipoDePendencia.CNH_VENCIDA, severidade, titulo, "detalhe", null, 1L);
    }
}
