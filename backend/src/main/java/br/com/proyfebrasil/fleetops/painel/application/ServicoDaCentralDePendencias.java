package br.com.proyfebrasil.fleetops.painel.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Placa;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import br.com.proyfebrasil.fleetops.painel.domain.Pendencia;
import br.com.proyfebrasil.fleetops.painel.domain.Severidade;
import br.com.proyfebrasil.fleetops.painel.domain.TipoDePendencia;
import br.com.proyfebrasil.fleetops.painel.infra.PendenciaRepository;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFaturas;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeFechamentoMensal;
import br.com.proyfebrasil.fleetops.operacao.domain.FaturaDaLocadora;
import br.com.proyfebrasil.fleetops.painel.infra.PendenciaRepository.Afetado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central de pendências (RN-23).
 *
 * <p>Apura, a cada consulta, o que está pendente na base. O cálculo é derivado — nada é
 * armazenado como "pendência resolvida", porque isso criaria uma segunda verdade que
 * precisaria ser mantida em sincronia com o cadastro. Resolver o dado faz a pendência
 * desaparecer sozinha, que é o comportamento que o gestor espera.
 *
 * <p>Com a Fase 3, entraram as duas categorias que a operação mensal sustenta: faturas
 * divergentes (RN-13) e contratos acima da franquia de KM (RN-06). A Fase 4 acrescenta
 * checklists atrasados, multas com prazo de indicação vencendo e avarias abertas há mais
 * de 30 dias.
 */
@Service
public class ServicoDaCentralDePendencias {

    /** Por quantas competências para trás o excedente de KM continua sendo pendência. */
    private static final int MESES_DE_ALERTA_DE_EXCEDENTE = 3;

    private final PendenciaRepository pendencias;
    private final ServicoDeFaturas faturas;
    private final ServicoDeFechamentoMensal fechamentos;
    private final Clock relogio;

    public ServicoDaCentralDePendencias(
            PendenciaRepository pendencias,
            ServicoDeFaturas faturas,
            ServicoDeFechamentoMensal fechamentos,
            Clock relogio) {
        this.pendencias = pendencias;
        this.faturas = faturas;
        this.fechamentos = fechamentos;
        this.relogio = relogio;
    }

    /** Pendências ordenadas por severidade e, dentro dela, por tipo. */
    @Transactional(readOnly = true)
    public List<Pendencia> apurar() {
        LocalDate hoje = LocalDate.now(relogio);
        List<Pendencia> resultado = new ArrayList<>();

        for (Afetado condutor : pendencias.condutoresComCnhVencida(hoje)) {
            resultado.add(new Pendencia(
                    TipoDePendencia.CNH_VENCIDA,
                    Severidade.CRITICA,
                    "CNH de %s está vencida".formatted(condutor.getRotulo()),
                    "Venceu em %s. O condutor não pode ser vinculado a um novo contrato até regularizar."
                            .formatted(formatar(condutor.getComplemento())),
                    "/cadastros/condutores",
                    condutor.getId()));
        }

        LocalDate limite = hoje.plusDays(Condutor.ANTECEDENCIAS_DE_ALERTA_CNH[0]);
        for (Afetado condutor : pendencias.condutoresComCnhVencendo(hoje, limite)) {
            resultado.add(new Pendencia(
                    TipoDePendencia.CNH_VENCENDO,
                    Severidade.ATENCAO,
                    "CNH de %s vence em breve".formatted(condutor.getRotulo()),
                    "Validade em %s. Solicite a renovação antes que o vínculo seja bloqueado."
                            .formatted(formatar(condutor.getComplemento())),
                    "/cadastros/condutores",
                    condutor.getId()));
        }

        for (Afetado locadora : pendencias.locadorasSemVigencia(hoje.getYear())) {
            resultado.add(new Pendencia(
                    TipoDePendencia.LOCADORA_SEM_VIGENCIA,
                    Severidade.CRITICA,
                    "%s está sem tabela de preços de %d".formatted(locadora.getRotulo(), hoje.getYear()),
                    "Há veículos desta locadora na frota. Sem a vigência do ano não é possível "
                            + "calcular KM excedente nem conferir a fatura mensal.",
                    "/cadastros/tabelas-preco",
                    locadora.getId()));
        }

        for (Afetado obra : pendencias.obrasAtivasSemFornecedorDoTipo(TipoFornecedor.POSTO)) {
            resultado.add(new Pendencia(
                    TipoDePendencia.OBRA_SEM_POSTO,
                    Severidade.ATENCAO,
                    "Obra %s sem posto credenciado".formatted(obra.getComplemento()),
                    "%s está ativa e não tem posto credenciado. Todo abastecimento na obra "
                            .formatted(obra.getRotulo())
                            + "entrará como não conformidade.",
                    "/cadastros/fornecedores",
                    obra.getId()));
        }

        for (Afetado obra : pendencias.obrasAtivasSemFornecedorDoTipo(TipoFornecedor.LAVA_JATO)) {
            resultado.add(new Pendencia(
                    TipoDePendencia.OBRA_SEM_LAVA_JATO,
                    Severidade.INFORMATIVA,
                    "Obra %s sem lava-jato credenciado".formatted(obra.getComplemento()),
                    "%s está ativa e não tem lava-jato credenciado; o controle de frequência "
                            .formatted(obra.getRotulo())
                            + "semanal fica sem referência de preço.",
                    "/cadastros/fornecedores",
                    obra.getId()));
        }

        List<Afetado> semGrupo = pendencias.veiculosSemGrupoTarifario();
        if (!semGrupo.isEmpty()) {
            // Agrupado em um único item: 40 linhas idênticas afogariam a central, e a
            // ação é a mesma para todas — abrir a lista de veículos e completar o campo.
            resultado.add(new Pendencia(
                    TipoDePendencia.VEICULO_SEM_GRUPO_TARIFARIO,
                    Severidade.INFORMATIVA,
                    "%d veículo(s) sem grupo tarifário".formatted(semGrupo.size()),
                    "Ex.: %s. Sem o grupo, o fechamento mensal não sabe qual valor contratado aplicar."
                            .formatted(amostra(semGrupo)),
                    "/cadastros/veiculos",
                    null));
        }

        // RN-13 — a própria RN-23 lista "faturas divergentes" entre o que a central
        // consolida. Uma fatura ajustada sai da fila; as demais, com divergência, ficam.
        for (FaturaDaLocadora fatura : faturas.comDivergenciaEmAberto()) {
            BigDecimal divergencia = fatura.getDivergencia();
            resultado.add(new Pendencia(
                    TipoDePendencia.FATURA_DIVERGENTE,
                    // Cobrança a mais é dinheiro saindo; a menos é erro de nota que a
                    // locadora vai corrigir depois, e por isso pesa menos.
                    divergencia.signum() > 0 ? Severidade.CRITICA : Severidade.ATENCAO,
                    "Fatura de %s com divergência de R$ %s".formatted(
                            fatura.getCompetencia(), divergencia.abs()),
                    "%s · %s. %s".formatted(
                            fatura.getContrato().getObra().getNome(),
                            fatura.getStatus().getDescricao(),
                            divergencia.signum() > 0
                                    ? "A locadora cobrou mais do que o contratado."
                                    : "A locadora cobrou menos do que o contratado."),
                    "/operacao",
                    fatura.getId()));
        }

        // RN-06 — contratos acima da franquia nas últimas competências apuradas.
        //
        // A janela é de três meses, e não de um. Os lançamentos chegam com atraso, e um
        // mês calmo não significa que o estouro do mês retrasado foi resolvido: com
        // janela de um mês, o alerta de março desapareceria em abril sem que ninguém
        // tivesse feito nada a respeito. Três meses é o horizonte em que a tratativa com
        // a locadora ainda acontece; além disso, o assunto é relatório, não pendência.
        YearMonth ultima = fechamentos.ultimaCompetenciaApurada();
        for (int recuo = 0; recuo < MESES_DE_ALERTA_DE_EXCEDENTE; recuo++) {
            YearMonth competencia = ultima.minusMonths(recuo);
            for (var fechamento : fechamentos.excedentesDaCompetencia(competencia)) {
                resultado.add(new Pendencia(
                        TipoDePendencia.KM_ACIMA_DA_FRANQUIA,
                        Severidade.ATENCAO,
                        "%s rodou %d km acima da franquia em %s".formatted(
                                fechamento.placa() == null ? "Contrato" : Placa.formatar(fechamento.placa()),
                                fechamento.kmExcedente(), competencia),
                        "%s · franquia de %s km. %s".formatted(
                                fechamento.obra(),
                                fechamento.pacoteContratado(),
                                fechamento.vigenciaIndisponivel()
                                        ? "Sem tabela de preços do ano, o custo não pôde ser estimado."
                                        : "Custo estimado de R$ %s.".formatted(fechamento.custoDoExcedente())),
                        "/operacao",
                        fechamento.contratoId()));
            }
        }

        for (Afetado veiculo : pendencias.veiculosEmContratosSobrepostos()) {
            resultado.add(new Pendencia(
                    TipoDePendencia.VEICULO_EM_DOIS_CONTRATOS,
                    Severidade.ATENCAO,
                    "Veículo %s aparece em dois contratos ao mesmo tempo".formatted(veiculo.getRotulo()),
                    "Períodos sobrepostos nos %s. Ou a data de uma substituição está errada, "
                            .formatted(veiculo.getComplemento())
                            + "ou uma devolução não foi registrada — confira qual das duas versões vale.",
                    "/cadastros/veiculos",
                    veiculo.getId()));
        }

        for (Afetado locadora : pendencias.locadorasComPortalSemCredencial()) {
            resultado.add(new Pendencia(
                    TipoDePendencia.LOCADORA_SEM_CREDENCIAL,
                    Severidade.INFORMATIVA,
                    "%s tem portal cadastrado e nenhuma credencial".formatted(locadora.getRotulo()),
                    "O acesso ao portal continuará dependendo de quem souber a senha de cor.",
                    "/cadastros/locadoras",
                    locadora.getId()));
        }

        resultado.sort(Comparator
                .comparingInt((Pendencia p) -> p.severidade().ordinal())
                .thenComparing(p -> p.tipo().ordinal())
                .thenComparing(Pendencia::titulo));
        return resultado;
    }

    /** Quantidade de pendências por severidade, para os contadores do painel. */
    @Transactional(readOnly = true)
    public Map<Severidade, Long> contarPorSeveridade() {
        Map<Severidade, Long> contagem = new EnumMap<>(Severidade.class);
        for (Severidade severidade : Severidade.values()) {
            contagem.put(severidade, 0L);
        }
        for (Pendencia pendencia : apurar()) {
            contagem.merge(pendencia.severidade(), 1L, Long::sum);
        }
        return contagem;
    }

    /** Formata a data ISO vinda da consulta para o padrão brasileiro. */
    private static String formatar(String dataIso) {
        if (dataIso == null || dataIso.length() < 10) {
            return "data não informada";
        }
        return "%s/%s/%s".formatted(dataIso.substring(8, 10), dataIso.substring(5, 7), dataIso.substring(0, 4));
    }

    private static String amostra(List<Afetado> afetados) {
        return afetados.stream().limit(3).map(Afetado::getRotulo).reduce((a, b) -> a + ", " + b).orElse("—");
    }
}
