package br.com.proyfebrasil.fleetops.painel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.painel.infra.BuscaGlobalRepository;
import br.com.proyfebrasil.fleetops.painel.infra.BuscaGlobalRepository.Achado;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@DisplayName("Busca global da barra superior (Seção 6.1)")
@ExtendWith(MockitoExtension.class)
class ServicoDeBuscaGlobalTest {

    @Mock
    private BuscaGlobalRepository repositorio;

    private ServicoDeBuscaGlobal servico;

    @BeforeEach
    void preparar() {
        servico = new ServicoDeBuscaGlobal(repositorio);
    }

    /** Um achado simples, já que a interface é uma projeção do Spring Data. */
    private static Achado achado(Long id, String rotulo, String detalhe) {
        return new Achado() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getRotulo() {
                return rotulo;
            }

            @Override
            public String getDetalhe() {
                return detalhe;
            }
        };
    }

    @Test
    @DisplayName("um caractere não consulta o banco — o resultado seria a base inteira")
    void naoConsultaComTermoCurto() {
        var vazio = servico.buscar("a");

        assertThat(vazio.total()).isZero();
        assertThat(vazio.veiculos()).isEmpty();
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("termo nulo ou só espaços não consulta o banco")
    void naoConsultaComTermoAusente() {
        assertThat(servico.buscar(null).total()).isZero();
        assertThat(servico.buscar("   ").total()).isZero();
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("a placa é procurada sem separadores e devolvida formatada")
    void normalizaAPlaca() {
        when(repositorio.veiculos(anyString(), anyString(), any()))
                .thenReturn(List.of(achado(3L, "ABC1D23", "VW Nivus · Localiza")));
        when(repositorio.condutores(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.obras(anyString(), any())).thenReturn(List.of());

        var resultados = servico.buscar("abc-1d23");

        var curingaDePlaca = ArgumentCaptor.forClass(String.class);
        verify(repositorio).veiculos(anyString(), curingaDePlaca.capture(), any(Pageable.class));
        // Quem digita o traço da placa antiga precisa achar o registro gravado sem ele.
        assertThat(curingaDePlaca.getValue()).isEqualTo("%abc1d23%");

        var veiculo = resultados.veiculos().get(0);
        assertThat(veiculo.rotulo()).isEqualTo("ABC-1D23");
        assertThat(veiculo.rota()).isEqualTo("/cadastros/veiculos/3");
        assertThat(veiculo.tipo()).isEqualTo("VEICULO");
    }

    @Test
    @DisplayName("um termo sem dígitos não faz o filtro de CPF casar com todo mundo")
    void termoSemDigitosNaoCasaCpfInteiro() {
        when(repositorio.veiculos(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.condutores(anyString(), anyString(), any()))
                .thenReturn(List.of(achado(9L, "Ana Souza", "CNH válida")));
        when(repositorio.obras(anyString(), any())).thenReturn(List.of());

        servico.buscar("Ana");

        var curingaDeCpf = ArgumentCaptor.forClass(String.class);
        verify(repositorio).condutores(anyString(), curingaDeCpf.capture(), any(Pageable.class));
        // "%%" casaria com qualquer CPF e traria a tabela inteira por baixo do nome.
        assertThat(curingaDeCpf.getValue()).isNotEqualTo("%%");
    }

    @Test
    @DisplayName("uma placa não vira busca por CPF pelos dígitos que ela contém")
    void placaNaoViraBuscaPorCpf() {
        when(repositorio.veiculos(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.condutores(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.obras(anyString(), any())).thenReturn(List.of());

        servico.buscar("TEL2B29");

        var curingaDeCpf = ArgumentCaptor.forClass(String.class);
        verify(repositorio).condutores(anyString(), curingaDeCpf.capture(), any(Pageable.class));
        // Extrair os dígitos daria "%229%", trazendo qualquer condutor cujo CPF os contenha.
        assertThat(curingaDeCpf.getValue()).isEqualTo("% %");
    }

    @Test
    @DisplayName("um CPF digitado com pontuação é procurado só pelos dígitos")
    void cpfComPontuacaoViraDigitos() {
        when(repositorio.veiculos(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.condutores(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.obras(anyString(), any())).thenReturn(List.of());

        servico.buscar("123.456.789-00");

        var curingaDeCpf = ArgumentCaptor.forClass(String.class);
        verify(repositorio).condutores(anyString(), curingaDeCpf.capture(), any(Pageable.class));
        assertThat(curingaDeCpf.getValue()).isEqualTo("%12345678900%");
    }

    @Test
    @DisplayName("o total soma os três tipos e cada um leva a sua rota")
    void somaOsTresTipos() {
        when(repositorio.veiculos(anyString(), anyString(), any()))
                .thenReturn(List.of(achado(1L, "ABC1D23", null)));
        when(repositorio.condutores(anyString(), anyString(), any()))
                .thenReturn(List.of(achado(2L, "Ana Souza", null), achado(3L, "Ana Lima", null)));
        when(repositorio.obras(anyString(), any())).thenReturn(List.of(achado(4L, "Uibaí", null)));

        var resultados = servico.buscar("ana");

        assertThat(resultados.total()).isEqualTo(4);
        assertThat(resultados.condutores()).extracting(ServicoDeBuscaGlobal.Resultado::rota)
                .containsOnly("/cadastros/condutores");
        assertThat(resultados.obras()).extracting(ServicoDeBuscaGlobal.Resultado::rota)
                .containsOnly("/cadastros/obras");
    }

    @Test
    @DisplayName("nada encontrado devolve listas vazias, não nulo")
    void semResultados() {
        when(repositorio.veiculos(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.condutores(anyString(), anyString(), any())).thenReturn(List.of());
        when(repositorio.obras(anyString(), any())).thenReturn(List.of());

        var resultados = servico.buscar("zzzz");

        assertThat(resultados.total()).isZero();
        assertThat(resultados.veiculos()).isEmpty();
        assertThat(resultados.condutores()).isEmpty();
        assertThat(resultados.obras()).isEmpty();
    }
}
