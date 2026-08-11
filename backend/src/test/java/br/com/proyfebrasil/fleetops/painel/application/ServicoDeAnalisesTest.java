package br.com.proyfebrasil.fleetops.painel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository.CelulaDaFrota;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository.PontoDePreco;
import br.com.proyfebrasil.fleetops.painel.infra.AnaliseRepository.ValorDoGrupo;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Análises cruzadas do painel")
class ServicoDeAnalisesTest {

    @Mock
    private AnaliseRepository repositorio;

    private ServicoDeAnalises servico;

    @BeforeEach
    void preparar() {
        servico = new ServicoDeAnalises(repositorio);
        when(repositorio.frotaPorCategoriaELocadora()).thenReturn(List.of());
        when(repositorio.curvaDePrecoPorPacote(anyInt())).thenReturn(List.of());
        when(repositorio.valoresDaVigencia(anyInt())).thenReturn(List.of());
        when(repositorio.anosComVigencia()).thenReturn(List.of());
    }

    private static CelulaDaFrota celula(String categoria, String locadora, long quantidade) {
        return new CelulaDaFrota() {
            @Override
            public String getCategoria() {
                return categoria;
            }

            @Override
            public String getLocadora() {
                return locadora;
            }

            @Override
            public long getQuantidade() {
                return quantidade;
            }
        };
    }

    private static PontoDePreco ponto(String locadora, int pacote, String valor) {
        return new PontoDePreco() {
            @Override
            public String getLocadora() {
                return locadora;
            }

            @Override
            public int getPacoteKm() {
                return pacote;
            }

            @Override
            public BigDecimal getValorMedio() {
                return new BigDecimal(valor);
            }
        };
    }

    private static ValorDoGrupo valor(String locadora, String grupo, int pacote, String montante) {
        return new ValorDoGrupo() {
            @Override
            public String getLocadora() {
                return locadora;
            }

            @Override
            public String getGrupo() {
                return grupo;
            }

            @Override
            public String getCategoria() {
                return "PASSEIO";
            }

            @Override
            public int getPacoteKm() {
                return pacote;
            }

            @Override
            public BigDecimal getValorMensal() {
                return new BigDecimal(montante);
            }
        };
    }

    @Nested
    @DisplayName("matriz categoria × locadora")
    class Matriz {

        @Test
        @DisplayName("cruza as duas dimensões preservando os totais por categoria")
        void cruzaDimensoes() {
            when(repositorio.frotaPorCategoriaELocadora())
                    .thenReturn(List.of(
                            celula("SUV", "Unidas", 28),
                            celula("SUV", "Localiza", 2),
                            celula("PASSEIO", "Unidas", 16)));

            var matriz = servico.matrizDaFrota();

            assertThat(matriz.locadoras()).containsExactly("Unidas", "Localiza");
            assertThat(matriz.linhas()).hasSize(2);
            // A ordem é por total, para que a categoria mais relevante venha primeiro.
            assertThat(matriz.linhas().get(0).chave()).isEqualTo("SUV");
            assertThat(matriz.linhas().get(0).total()).isEqualTo(30);
            assertThat(matriz.linhas().get(0).porLocadora())
                    .containsEntry("Unidas", 28L)
                    .containsEntry("Localiza", 2L);
        }

        @Test
        @DisplayName("dobra as locadoras menores em Outras, respeitando o teto de séries")
        void dobraCauda() {
            // Quatro locadoras excedem o teto de três séries validado pela paleta.
            when(repositorio.frotaPorCategoriaELocadora())
                    .thenReturn(List.of(
                            celula("SUV", "Unidas", 50),
                            celula("SUV", "Localiza", 20),
                            celula("SUV", "SpeedWay", 10),
                            celula("SUV", "Feeling", 3),
                            celula("SUV", "Agregado", 2)));

            var matriz = servico.matrizDaFrota();

            assertThat(matriz.locadoras()).containsExactly("Unidas", "Localiza", "SpeedWay", "Outras");
            assertThat(matriz.linhas().get(0).porLocadora()).containsEntry("Outras", 5L);
            // Nada se perde ao dobrar: o total continua sendo a soma de tudo.
            assertThat(matriz.linhas().get(0).total()).isEqualTo(85);
        }

        @Test
        @DisplayName("sem veículos, a matriz sai vazia em vez de quebrar")
        void semVeiculos() {
            assertThat(servico.matrizDaFrota().linhas()).isEmpty();
        }
    }

    @Nested
    @DisplayName("curva de preço por pacote")
    class Curvas {

        @Test
        @DisplayName("agrupa os pontos por locadora, na ordem dos pacotes")
        void agrupaPorLocadora() {
            when(repositorio.curvaDePrecoPorPacote(2026))
                    .thenReturn(List.of(
                            ponto("Unidas", 3000, "4296.00"),
                            ponto("Unidas", 4500, "4654.00"),
                            ponto("Localiza", 3000, "3884.00"),
                            ponto("Localiza", 4000, "4157.00")));

            var curvas = servico.curvasDePreco(2026);

            assertThat(curvas).hasSize(2);
            assertThat(curvas.get(0).locadora()).isEqualTo("Unidas");
            assertThat(curvas.get(0).pontos()).extracting("pacoteKm").containsExactly(3000, 4500);
        }

        @Test
        @DisplayName("descarta locadora com um ponto só — uma linha de um ponto não é curva")
        void descartaPontoUnico() {
            when(repositorio.curvaDePrecoPorPacote(2026))
                    .thenReturn(List.of(
                            ponto("Unidas", 3000, "4296.00"),
                            ponto("Unidas", 4500, "4654.00"),
                            ponto("SpeedWay", 3000, "3000.00")));

            assertThat(servico.curvasDePreco(2026))
                    .extracting("locadora")
                    .containsExactly("Unidas");
        }
    }

    @Nested
    @DisplayName("comparação de vigências")
    class Vigencias {

        @Test
        @DisplayName("calcula a variação percentual grupo a grupo entre os dois anos mais recentes")
        void calculaVariacao() {
            when(repositorio.anosComVigencia()).thenReturn(List.of(2026, 2025, 2024));
            when(repositorio.valoresDaVigencia(2025))
                    .thenReturn(List.of(valor("Unidas", "AM", 3000, "2471.88")));
            when(repositorio.valoresDaVigencia(2026))
                    .thenReturn(List.of(valor("Unidas", "AM", 3000, "2537.87")));

            var comparacao = servico.compararVigencias();

            assertThat(comparacao.anoAnterior()).isEqualTo(2025);
            assertThat(comparacao.anoAtual()).isEqualTo(2026);
            assertThat(comparacao.reajustes()).singleElement().satisfies(reajuste -> {
                assertThat(reajuste.grupo()).isEqualTo("AM");
                // (2537,87 − 2471,88) / 2471,88 = 2,67%
                assertThat(reajuste.variacaoPercentual()).isEqualByComparingTo("2.67");
            });
            assertThat(comparacao.variacaoMedia()).isEqualByComparingTo("2.67");
        }

        @Test
        @DisplayName("ordena do maior reajuste para o menor: é o que exige renegociação")
        void ordenaPorVariacao() {
            when(repositorio.anosComVigencia()).thenReturn(List.of(2026, 2025));
            when(repositorio.valoresDaVigencia(2025))
                    .thenReturn(List.of(
                            valor("Unidas", "AM", 3000, "1000.00"),
                            valor("Unidas", "B", 3000, "1000.00")));
            when(repositorio.valoresDaVigencia(2026))
                    .thenReturn(List.of(
                            valor("Unidas", "AM", 3000, "1020.00"),
                            valor("Unidas", "B", 3000, "1100.00")));

            assertThat(servico.compararVigencias().reajustes())
                    .extracting("grupo")
                    .containsExactly("B", "AM");
        }

        @Test
        @DisplayName("grupo que não existia na vigência anterior é ignorado, não tratado como alta")
        void ignoraGrupoNovo() {
            when(repositorio.anosComVigencia()).thenReturn(List.of(2026, 2025));
            when(repositorio.valoresDaVigencia(2025)).thenReturn(List.of());
            when(repositorio.valoresDaVigencia(2026))
                    .thenReturn(List.of(valor("Unidas", "NOVO", 3000, "1000.00")));

            assertThat(servico.compararVigencias().reajustes()).isEmpty();
        }

        @Test
        @DisplayName("com uma vigência só não há o que comparar")
        void vigenciaUnica() {
            when(repositorio.anosComVigencia()).thenReturn(List.of(2026));

            var comparacao = servico.compararVigencias();

            assertThat(comparacao.anoAnterior()).isNull();
            assertThat(comparacao.anoAtual()).isEqualTo(2026);
            assertThat(comparacao.reajustes()).isEmpty();
            assertThat(comparacao.variacaoMedia()).isNull();
        }
    }
}
