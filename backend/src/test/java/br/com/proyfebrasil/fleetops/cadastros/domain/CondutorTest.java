package br.com.proyfebrasil.fleetops.cadastros.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RN-16 — validade da CNH do condutor")
class CondutorTest {

    private static final String CPF = "52998224725";
    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);

    private Condutor condutorCom(LocalDate validadeDaCnh) {
        Condutor condutor = new Condutor("Ana Souza", CPF);
        condutor.alterarCnh("12345678900", "AB", validadeDaCnh);
        return condutor;
    }

    @Nested
    @DisplayName("vencimento")
    class Vencimento {

        @Test
        @DisplayName("RN16_deveConsiderarVencidaACnhComValidadeAnteriorAHoje")
        void vencida() {
            Condutor condutor = condutorCom(HOJE.minusDays(1));

            assertThat(condutor.cnhVencidaEm(HOJE)).isTrue();
            assertThat(condutor.diasParaVencimentoDaCnh(HOJE)).isEqualTo(-1L);
        }

        @Test
        @DisplayName("RN16_naoDeveConsiderarVencidaACnhQueVenceHoje")
        void venceHoje() {
            // O último dia de validade ainda é válido: a CNH vence ao fim dele.
            Condutor condutor = condutorCom(HOJE);

            assertThat(condutor.cnhVencidaEm(HOJE)).isFalse();
            assertThat(condutor.diasParaVencimentoDaCnh(HOJE)).isZero();
        }

        @Test
        @DisplayName("RN16_naoDeveConsiderarVencidaACnhSemValidadeCadastrada")
        void semValidade() {
            // Cadastro incompleto é pendência de dados, não impedimento operacional.
            Condutor condutor = condutorCom(null);

            assertThat(condutor.cnhVencidaEm(HOJE)).isFalse();
            assertThat(condutor.diasParaVencimentoDaCnh(HOJE)).isNull();
            assertThat(condutor.cnhEmAlertaEm(HOJE)).isFalse();
        }
    }

    @Nested
    @DisplayName("alerta")
    class Alerta {

        @Test
        @DisplayName("RN16_deveAlertarAPartirDe60DiasDoVencimento")
        void alertaEm60Dias() {
            assertThat(condutorCom(HOJE.plusDays(61)).cnhEmAlertaEm(HOJE)).isFalse();
            assertThat(condutorCom(HOJE.plusDays(60)).cnhEmAlertaEm(HOJE)).isTrue();
            assertThat(condutorCom(HOJE.plusDays(30)).cnhEmAlertaEm(HOJE)).isTrue();
            assertThat(condutorCom(HOJE.plusDays(1)).cnhEmAlertaEm(HOJE)).isTrue();
        }

        @Test
        @DisplayName("RN16_deveManterOAlertaParaCnhJaVencida")
        void alertaPersisteAposVencer() {
            assertThat(condutorCom(HOJE.minusDays(10)).cnhEmAlertaEm(HOJE)).isTrue();
        }

        @Test
        @DisplayName("as antecedências previstas são 60 e 30 dias")
        void antecedencias() {
            assertThat(Condutor.ANTECEDENCIAS_DE_ALERTA_CNH).containsExactly(60, 30);
        }
    }

    @Nested
    @DisplayName("vínculo com contrato")
    class Vinculo {

        @Test
        @DisplayName("RN16_deveBloquearVinculoDeCondutorComCnhVencida")
        void bloqueiaCnhVencida() {
            assertThat(condutorCom(HOJE.minusDays(1)).podeSerVinculadoAContrato(HOJE)).isFalse();
        }

        @Test
        @DisplayName("RN16_devePermitirVinculoDeCondutorAtivoComCnhValida")
        void permiteCnhValida() {
            assertThat(condutorCom(HOJE.plusYears(1)).podeSerVinculadoAContrato(HOJE)).isTrue();
        }

        @Test
        @DisplayName("RN16_deveBloquearVinculoDeCondutorInativoAindaQueComCnhValida")
        void bloqueiaInativo() {
            Condutor condutor = condutorCom(HOJE.plusYears(1));
            condutor.alterarStatus(StatusCondutor.INATIVO);

            assertThat(condutor.podeSerVinculadoAContrato(HOJE)).isFalse();
        }
    }

    @Nested
    @DisplayName("dados cadastrais")
    class Dados {

        @Test
        @DisplayName("normaliza a categoria da CNH para caixa alta")
        void normalizaCategoria() {
            Condutor condutor = new Condutor("Ana", CPF);
            condutor.alterarCnh("123", " ab ", HOJE);

            assertThat(condutor.getCnhCategoria()).isEqualTo("AB");
        }

        @Test
        @DisplayName("rejeita categoria de CNH com letras inválidas")
        void rejeitaCategoriaInvalida() {
            Condutor condutor = new Condutor("Ana", CPF);

            assertThatThrownBy(() -> condutor.alterarCnh("123", "XZ", HOJE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Categoria de CNH");
        }

        @Test
        @DisplayName("categoria vazia é aceita como ausência de dado")
        void categoriaVaziaViraNula() {
            Condutor condutor = new Condutor("Ana", CPF);
            condutor.alterarCnh("123", "  ", HOJE);

            assertThat(condutor.getCnhCategoria()).isNull();
        }

        @Test
        @DisplayName("rejeita CPF inválido na criação")
        void rejeitaCpfInvalido() {
            assertThatThrownBy(() -> new Condutor("Ana", "11111111111"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
