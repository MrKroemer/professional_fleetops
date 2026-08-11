package br.com.proyfebrasil.fleetops.cadastros.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RN-14 e RN-06 — tabela de preços e KM excedente")
class TabelaPrecoTest {

    private TabelaPreco tabela;

    @BeforeEach
    void preparar() {
        tabela = new TabelaPreco(new Locadora("Unidas", TipoLocadora.NACIONAL), 2026);
    }

    @Nested
    @DisplayName("grupos e pacotes")
    class GruposEPacotes {

        @Test
        @DisplayName("RN14_deveDevolverOValorMensalDoGrupoEPacote")
        void valorMensal() {
            GrupoTarifario grupo = tabela.adicionarGrupo("AM", "KWID/Mobi", CategoriaVeiculo.PASSEIO);
            grupo.definirPacote(3000, new BigDecimal("2537.87"));
            grupo.definirPacote(4500, new BigDecimal("2743.23"));

            assertThat(tabela.valorMensal("AM", 3000)).contains(new BigDecimal("2537.87"));
            assertThat(tabela.valorMensal("AM", 4500)).contains(new BigDecimal("2743.23"));
        }

        @Test
        @DisplayName("a busca por código de grupo ignora diferenças de caixa")
        void buscaIgnoraCaixa() {
            tabela.adicionarGrupo("am", "KWID/Mobi", CategoriaVeiculo.PASSEIO)
                    .definirPacote(3000, new BigDecimal("2537.87"));

            assertThat(tabela.valorMensal("AM", 3000)).isPresent();
            assertThat(tabela.valorMensal("Am", 3000)).isPresent();
        }

        @Test
        @DisplayName("grupo ou pacote inexistente devolve vazio, sem lançar exceção")
        void ausenteDevolveVazio() {
            tabela.adicionarGrupo("AM", "KWID/Mobi", CategoriaVeiculo.PASSEIO)
                    .definirPacote(3000, new BigDecimal("2537.87"));

            assertThat(tabela.valorMensal("XX", 3000)).isEmpty();
            assertThat(tabela.valorMensal("AM", 9999)).isEmpty();
        }

        @Test
        @DisplayName("RN14_deveRecusarDoisGruposComOMesmoCodigoNaMesmaVigencia")
        void recusaGrupoDuplicado() {
            tabela.adicionarGrupo("AM", "KWID/Mobi", CategoriaVeiculo.PASSEIO);

            assertThatThrownBy(() -> tabela.adicionarGrupo("am", "Outro", CategoriaVeiculo.SUV))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AM");
        }

        @Test
        @DisplayName("redefinir um pacote substitui o valor anterior em vez de duplicá-lo")
        void redefinirPacoteSubstitui() {
            GrupoTarifario grupo = tabela.adicionarGrupo("AM", "KWID/Mobi", CategoriaVeiculo.PASSEIO);
            grupo.definirPacote(3000, new BigDecimal("2400.00"));
            grupo.definirPacote(3000, new BigDecimal("2537.87"));

            assertThat(grupo.getPacotes()).hasSize(1);
            assertThat(tabela.valorMensal("AM", 3000)).contains(new BigDecimal("2537.87"));
        }

        @Test
        @DisplayName("pacotes saem ordenados por quilometragem")
        void pacotesOrdenados() {
            GrupoTarifario grupo = tabela.adicionarGrupo("AM", "KWID/Mobi", CategoriaVeiculo.PASSEIO);
            grupo.definirPacote(6000, new BigDecimal("3118.28"));
            grupo.definirPacote(3000, new BigDecimal("2537.87"));
            grupo.definirPacote(4500, new BigDecimal("2743.23"));

            assertThat(grupo.getPacotes()).extracting(PrecoPacoteKm::getPacoteKm).containsExactly(3000, 4500, 6000);
        }

        @Test
        @DisplayName("recusa pacote com quilometragem ou valor inválidos")
        void recusaPacoteInvalido() {
            GrupoTarifario grupo = tabela.adicionarGrupo("AM", "KWID/Mobi", CategoriaVeiculo.PASSEIO);

            assertThatThrownBy(() -> grupo.definirPacote(0, BigDecimal.TEN))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> grupo.definirPacote(3000, new BigDecimal("-1.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> grupo.definirPacote(3000, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("KM excedente")
    class KmExcedente {

        @Test
        @DisplayName("RN06_deveAplicarOValorGeralDaCategoriaQuandoNaoHaValorPorPacote")
        void valorGeral() {
            // Padrão da Unidas: um valor por categoria, igual para todos os pacotes.
            tabela.definirKmExcedente(CategoriaVeiculo.PASSEIO, null, new BigDecimal("0.60"));
            tabela.definirKmExcedente(CategoriaVeiculo.QUATRO_X_QUATRO, null, new BigDecimal("3.20"));

            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.PASSEIO, 3000)).contains(new BigDecimal("0.60"));
            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.PASSEIO, 6000)).contains(new BigDecimal("0.60"));
            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.QUATRO_X_QUATRO, 4500))
                    .contains(new BigDecimal("3.20"));
        }

        @Test
        @DisplayName("RN06_oValorEspecificoDoPacoteTemPrecedenciaSobreOGeral")
        void especificoVenceGeral() {
            // Padrão da Localiza: o valor muda conforme o pacote contratado.
            tabela.definirKmExcedente(CategoriaVeiculo.PASSEIO, null, new BigDecimal("0.50"));
            tabela.definirKmExcedente(CategoriaVeiculo.PASSEIO, 4000, new BigDecimal("1.00"));

            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.PASSEIO, 4000)).contains(new BigDecimal("1.00"));
            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.PASSEIO, 3000)).contains(new BigDecimal("0.50"));
        }

        @Test
        @DisplayName("categoria sem preço cadastrado devolve vazio")
        void semPrecoDevolveVazio() {
            tabela.definirKmExcedente(CategoriaVeiculo.PASSEIO, null, new BigDecimal("0.60"));

            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.SUV, 3000)).isEmpty();
        }

        @Test
        @DisplayName("redefinir o mesmo par categoria/pacote substitui o valor")
        void redefinirSubstitui() {
            tabela.definirKmExcedente(CategoriaVeiculo.SUV, 3000, new BigDecimal("1.00"));
            tabela.definirKmExcedente(CategoriaVeiculo.SUV, 3000, new BigDecimal("1.20"));

            assertThat(tabela.getPrecosKmExcedente()).hasSize(1);
            assertThat(tabela.valorKmExcedente(CategoriaVeiculo.SUV, 3000)).contains(new BigDecimal("1.20"));
        }

        @Test
        @DisplayName("recusa valor negativo de KM excedente")
        void recusaNegativo() {
            assertThatThrownBy(() ->
                            tabela.definirKmExcedente(CategoriaVeiculo.SUV, null, new BigDecimal("-0.10")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("RN14_deveRecusarAnoDeVigenciaForaDaFaixaAceita")
    void recusaAnoInvalido() {
        Locadora locadora = new Locadora("Localiza", TipoLocadora.NACIONAL);

        assertThatThrownBy(() -> new TabelaPreco(locadora, 1999)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TabelaPreco(locadora, 2101)).isInstanceOf(IllegalArgumentException.class);
    }
}
