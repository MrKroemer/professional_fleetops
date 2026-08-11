package br.com.proyfebrasil.fleetops.painel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.painel.domain.Pendencia;
import br.com.proyfebrasil.fleetops.painel.domain.Severidade;
import br.com.proyfebrasil.fleetops.painel.domain.TipoDePendencia;
import br.com.proyfebrasil.fleetops.painel.infra.PendenciaRepository;
import br.com.proyfebrasil.fleetops.painel.infra.PendenciaRepository.Afetado;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RN-23 — central de pendências")
class ServicoDaCentralDePendenciasTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);

    @Mock
    private PendenciaRepository repositorio;

    @Mock
    private br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas faturas;

    @Mock
    private br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFechamentoMensal fechamentos;

    private ServicoDaCentralDePendencias servico;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servico = new ServicoDaCentralDePendencias(repositorio, faturas, fechamentos, relogio);
        semPendencias();
    }

    /** Por padrão nada está pendente; cada teste liga só a consulta que lhe interessa. */
    private void semPendencias() {
        when(repositorio.condutoresComCnhVencida(any())).thenReturn(List.of());
        when(repositorio.condutoresComCnhVencendo(any(), any())).thenReturn(List.of());
        when(repositorio.locadorasSemVigencia(anyInt())).thenReturn(List.of());
        when(repositorio.obrasAtivasSemFornecedorDoTipo(any())).thenReturn(List.of());
        when(repositorio.veiculosSemGrupoTarifario()).thenReturn(List.of());
        when(repositorio.locadorasComPortalSemCredencial()).thenReturn(List.of());
        when(repositorio.veiculosEmContratosSobrepostos()).thenReturn(List.of());
        when(faturas.comDivergenciaEmAberto()).thenReturn(List.of());
        when(fechamentos.excedentesDaCompetencia(any())).thenReturn(List.of());
        when(fechamentos.ultimaCompetenciaApurada()).thenReturn(java.time.YearMonth.from(HOJE).minusMonths(1));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static Afetado afetado(Long id, String rotulo, String complemento) {
        return new Afetado() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getRotulo() {
                return rotulo;
            }

            @Override
            public String getComplemento() {
                return complemento;
            }
        };
    }

    @Test
    @DisplayName("RN23_semLacunasNaBaseACentralFicaVazia")
    void centralVazia() {
        assertThat(servico.apurar()).isEmpty();
        assertThat(servico.contarPorSeveridade())
                .containsEntry(Severidade.CRITICA, 0L)
                .containsEntry(Severidade.ATENCAO, 0L)
                .containsEntry(Severidade.INFORMATIVA, 0L);
    }

    @Test
    @DisplayName("RN01_veiculoEmDoisContratosAparecerComoPendencia")
    void veiculoEmDoisContratos() {
        when(repositorio.veiculosEmContratosSobrepostos())
                .thenReturn(List.of(afetado(12L, "ABC1D23", "contratos 44 e 91")));

        var pendencias = servico.apurar();

        // O conflito não é motivo para recusar o registro — o acervo real tem casos
        // legítimos, como o dia da transferência contado nas duas pontas. É um dado a
        // ser conferido por gente, que é exatamente o que a central existe para mostrar.
        assertThat(pendencias).singleElement().satisfies(pendencia -> {
            assertThat(pendencia.tipo()).isEqualTo(TipoDePendencia.VEICULO_EM_DOIS_CONTRATOS);
            assertThat(pendencia.severidade()).isEqualTo(Severidade.ATENCAO);
            assertThat(pendencia.titulo()).contains("ABC1D23");
            assertThat(pendencia.detalhe()).contains("contratos 44 e 91");
            assertThat(pendencia.recurso()).isEqualTo("/cadastros/veiculos");
        });
    }

    @Test
    @DisplayName("RN16_e_RN23_cnhVencidaEcriticaEcnhVencendoEatencao")
    void severidadeDaCnh() {
        when(repositorio.condutoresComCnhVencida(HOJE))
                .thenReturn(List.of(afetado(1L, "Ana Souza", "2026-02-01")));
        when(repositorio.condutoresComCnhVencendo(HOJE, HOJE.plusDays(60)))
                .thenReturn(List.of(afetado(2L, "Bruno Lima", "2026-04-10")));

        List<Pendencia> pendencias = servico.apurar();

        assertThat(pendencias).hasSize(2);
        assertThat(pendencias.get(0).severidade()).isEqualTo(Severidade.CRITICA);
        assertThat(pendencias.get(0).tipo()).isEqualTo(TipoDePendencia.CNH_VENCIDA);
        assertThat(pendencias.get(0).titulo()).contains("Ana Souza");
        // A data chega em ISO da consulta e é exibida no padrão brasileiro.
        assertThat(pendencias.get(0).detalhe()).contains("01/02/2026");

        assertThat(pendencias.get(1).severidade()).isEqualTo(Severidade.ATENCAO);
        assertThat(pendencias.get(1).detalhe()).contains("10/04/2026");
    }

    @Test
    @DisplayName("RN23_asPendenciasSaemOrdenadasDaMaisGraveParaAMenosGrave")
    void ordenacaoPorSeveridade() {
        when(repositorio.condutoresComCnhVencendo(any(), any()))
                .thenReturn(List.of(afetado(2L, "Bruno", "2026-04-10")));
        when(repositorio.locadorasComPortalSemCredencial())
                .thenReturn(List.of(afetado(3L, "Unidas", "https://portal")));
        when(repositorio.condutoresComCnhVencida(any()))
                .thenReturn(List.of(afetado(1L, "Ana", "2026-02-01")));

        assertThat(servico.apurar())
                .extracting(Pendencia::severidade)
                .containsExactly(Severidade.CRITICA, Severidade.ATENCAO, Severidade.INFORMATIVA);
    }

    @Test
    @DisplayName("RN14_e_RN23_locadoraSemVigenciaDoAnoEPendenciaCritica")
    void locadoraSemVigencia() {
        when(repositorio.locadorasSemVigencia(2026))
                .thenReturn(List.of(afetado(9L, "SpeedWay", "2026")));

        List<Pendencia> pendencias = servico.apurar();

        assertThat(pendencias).singleElement().satisfies(pendencia -> {
            assertThat(pendencia.tipo()).isEqualTo(TipoDePendencia.LOCADORA_SEM_VIGENCIA);
            assertThat(pendencia.severidade()).isEqualTo(Severidade.CRITICA);
            assertThat(pendencia.titulo()).contains("SpeedWay").contains("2026");
            assertThat(pendencia.recurso()).isEqualTo("/cadastros/tabelas-preco");
        });
    }

    @Test
    @DisplayName("RN04_e_RN23_obraAtivaSemPostoCredenciadoGeraPendencia")
    void obraSemPosto() {
        when(repositorio.obrasAtivasSemFornecedorDoTipo(TipoFornecedor.POSTO))
                .thenReturn(List.of(afetado(4L, "SKER Ventos", "24.019")));

        assertThat(servico.apurar()).singleElement().satisfies(pendencia -> {
            assertThat(pendencia.tipo()).isEqualTo(TipoDePendencia.OBRA_SEM_POSTO);
            assertThat(pendencia.titulo()).contains("24.019");
            assertThat(pendencia.detalhe()).contains("não conformidade");
        });
    }

    @Test
    @DisplayName("RN23_veiculosSemGrupoTarifarioSaoAgrupadosEmUmUnicoItem")
    void veiculosSemGrupoAgrupados() {
        // Quarenta linhas idênticas afogariam a central, e a ação é a mesma para todas.
        when(repositorio.veiculosSemGrupoTarifario())
                .thenReturn(List.of(
                        afetado(1L, "ABC1D23", "Onix"),
                        afetado(2L, "DEF4G56", "Argo"),
                        afetado(3L, "GHI7J89", "Polo"),
                        afetado(4L, "JKL0M12", "T-Cross")));

        assertThat(servico.apurar()).singleElement().satisfies(pendencia -> {
            assertThat(pendencia.titulo()).contains("4 veículo(s)");
            // A amostra ajuda a reconhecer o problema sem abrir a lista inteira.
            assertThat(pendencia.detalhe()).contains("ABC1D23").contains("DEF4G56").contains("GHI7J89");
            assertThat(pendencia.referencia()).isNull();
        });
    }

    @Test
    @DisplayName("RN23_osContadoresPorSeveridadeBatemComAListaApurada")
    void contadoresConferem() {
        when(repositorio.condutoresComCnhVencida(any()))
                .thenReturn(List.of(afetado(1L, "Ana", "2026-02-01"), afetado(2L, "Bia", "2026-01-05")));
        when(repositorio.condutoresComCnhVencendo(any(), any()))
                .thenReturn(List.of(afetado(3L, "Caio", "2026-04-10")));
        when(repositorio.locadorasComPortalSemCredencial())
                .thenReturn(List.of(afetado(4L, "Unidas", "https://portal")));

        var contagem = servico.contarPorSeveridade();

        assertThat(contagem)
                .containsEntry(Severidade.CRITICA, 2L)
                .containsEntry(Severidade.ATENCAO, 1L)
                .containsEntry(Severidade.INFORMATIVA, 1L);
        assertThat(servico.apurar()).hasSize(4);
    }

    @Test
    @DisplayName("data ausente não quebra a mensagem da pendência")
    void dataAusente() {
        when(repositorio.condutoresComCnhVencida(any())).thenReturn(List.of(afetado(1L, "Ana", null)));

        assertThat(servico.apurar()).singleElement().satisfies(pendencia ->
                assertThat(pendencia.detalhe()).contains("data não informada"));
    }
}
