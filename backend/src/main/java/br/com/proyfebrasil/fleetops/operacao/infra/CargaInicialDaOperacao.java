package br.com.proyfebrasil.fleetops.operacao.infra;

import br.com.proyfebrasil.fleetops.cadastros.infra.VeiculoRepository;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.contratos.infra.ContratoRepository;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeAbastecimento;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeKm;
import br.com.proyfebrasil.fleetops.operacao.application.ServicoDeLancamentos.DadosDeServico;
import br.com.proyfebrasil.fleetops.operacao.domain.TipoDeServico;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Carga inicial da operação mensal, extraída dos controles por obra.
 *
 * <p>Condicionada à mesma chave da carga do acervo e executada depois dela
 * ({@code @Order(30)}), porque cada lançamento precisa de um contrato para pendurar.
 * Idempotente pela contagem de registros de quilometragem.
 *
 * <p>Como a carga do acervo, entra pelos <strong>serviços de aplicação</strong>: o objetivo
 * não é encher tabelas, é exercitar as mesmas regras que a API aplica. Um lançamento que a
 * RN-03 recusaria aqui também seria recusado pela tela, e é isso que se quer descobrir na
 * carga, não em produção.
 *
 * <p>Duas acomodações ao que a planilha é:
 *
 * <p><strong>A planilha não registra o posto do abastecimento.</strong> As colunas são só
 * DATA e VALOR. Os lançamentos entram sem posto — e por isso conformes, já que não há como
 * afirmar que foram irregulares. Marcá-los como não conformes seria inventar uma acusação;
 * a lacuna é de cadastro, e aparece como tal no relatório da carga.
 *
 * <p><strong>Duas notas no mesmo dia viram uma.</strong> A RN-04 permite um abastecimento
 * por dia, e a planilha às vezes traz duas colunas com a mesma data. É exatamente o caso
 * que a mensagem de erro da regra descreve — "se forem duas notas do mesmo dia, some os
 * valores em um único lançamento" —, então a carga faz o que ela manda.
 */
@Component
@ConditionalOnProperty(name = "fleetops.carga-inicial.habilitada", havingValue = "true")
@Order(30)
public class CargaInicialDaOperacao implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CargaInicialDaOperacao.class);
    private static final String ARQUIVO = "db/seed/operacao.json";

    private final ContratoRepository contratos;
    private final VeiculoRepository veiculos;
    private final RegistroDeKmRepository registros;
    private final ServicoDeLancamentos lancamentos;
    private final ObjectMapper json;

    public CargaInicialDaOperacao(
            ContratoRepository contratos,
            VeiculoRepository veiculos,
            RegistroDeKmRepository registros,
            ServicoDeLancamentos lancamentos,
            ObjectMapper json) {
        this.contratos = contratos;
        this.veiculos = veiculos;
        this.registros = registros;
        this.lancamentos = lancamentos;
        this.json = json;
    }

    /**
     * Carrega os lançamentos, cada um em sua própria transação.
     *
     * <p><strong>Sem {@code @Transactional} aqui</strong>, e isso é a parte importante. Os
     * métodos do serviço são transacionais; se a carga abrisse uma transação em volta,
     * cada lançamento recusado marcaria a transação inteira como somente-rollback, e o
     * {@code catch} não desfaria isso — a carga terminaria "com sucesso" e gravaria zero.
     * É a mesma armadilha que a carga do acervo encontrou na Fase 1.
     *
     * <p>Com cada chamada abrindo a sua própria transação, uma recusa custa exatamente um
     * lançamento, que é o comportamento que um relatório de rejeições pressupõe.
     */
    @Override
    public void run(String... args) throws IOException {
        if (registros.count() > 0) {
            LOG.info("Operação já carregada; nada a fazer.");
            return;
        }
        var recurso = new ClassPathResource(ARQUIVO);
        if (!recurso.exists()) {
            LOG.warn("{} não encontrado; rode scripts/extrair-operacao.py.", ARQUIVO);
            return;
        }

        JsonNode blocos;
        try (InputStream entrada = recurso.getInputStream()) {
            blocos = json.readTree(entrada);
        }

        var contagem = new Contagem();
        for (JsonNode bloco : blocos) {
            Optional<ContratoDeLocacao> contrato = contratoDaPlaca(texto(bloco, "placa"));
            if (contrato.isEmpty()) {
                contagem.semContrato++;
                continue;
            }
            Long contratoId = contrato.get().getId();
            carregarKm(contratoId, bloco.path("registrosDeKm"), contagem);
            carregarAbastecimentos(contratoId, bloco.path("abastecimentos"), contagem);
            carregarServicos(contratoId, bloco.path("servicos"), contagem);
        }

        LOG.info(
                "Operação carregada: {} registros de KM, {} abastecimentos, "
                        + "{} serviços. Recusados: {} KM fora de ordem, {} abastecimentos, {} serviços. "
                        + "{} blocos sem contrato correspondente.",
                contagem.km, contagem.abastecimentos, contagem.servicos,
                contagem.kmRecusado, contagem.abastecimentoRecusado, contagem.servicoRecusado,
                contagem.semContrato);
        if (contagem.abastecimentos > 0) {
            LOG.info(
                    "Os {} abastecimentos entraram sem posto: as planilhas por obra registram "
                            + "apenas data e valor. Completar o posto é trabalho de cadastro.",
                    contagem.abastecimentos);
        }
    }

    /**
     * Contrato ao qual a placa pertence.
     *
     * <p>Prefere o contrato ativo, e cai para o mais recente encerrado quando não há um.
     * Quarenta das oitenta e oito placas destas planilhas só têm contrato encerrado — são
     * as abas marcadas {@code -Desmob} e {@code -Devolv}, de condutores que saíram. A
     * operação delas aconteceu enquanto o contrato estava ativo, e descartá-la porque ele
     * fechou jogaria fora exatamente o histórico que o sistema existe para guardar.
     */
    private Optional<ContratoDeLocacao> contratoDaPlaca(String placa) {
        return veiculos.buscarPorPlaca(placa)
                .flatMap(veiculo -> contratos.historicoDoVeiculo(veiculo.getId()).stream().findFirst());
    }

    /**
     * Carrega os registros de quilometragem em ordem cronológica.
     *
     * <p>A ordenação importa: a RN-03 compara cada lançamento com os vizinhos, e inserir
     * fora de ordem faria a regra recusar registros perfeitamente válidos só porque
     * chegaram embaralhados.
     */
    private void carregarKm(Long contratoId, JsonNode lista, Contagem contagem) {
        List<JsonNode> ordenados = new ArrayList<>();
        lista.forEach(ordenados::add);
        ordenados.sort(Comparator.comparing(no -> texto(no, "data")));

        for (JsonNode item : ordenados) {
            try {
                lancamentos.lancarKm(contratoId, new DadosDeKm(
                        null,
                        LocalDate.parse(texto(item, "data")),
                        item.path("kmInicial").asInt(),
                        item.path("kmFinal").asInt(),
                        null, null, texto(item, "observacao")));
                contagem.km++;
            } catch (RuntimeException recusado) {
                // O hodômetro da planilha nem sempre encadeia: uma substituição de veículo
                // no meio do mês zera a contagem, e o mês seguinte começa mais baixo.
                contagem.kmRecusado++;
            }
        }
    }

    /** Carrega os abastecimentos, somando as notas repetidas no mesmo dia (RN-04). */
    private void carregarAbastecimentos(Long contratoId, JsonNode lista, Contagem contagem) {
        Map<LocalDate, BigDecimal> porDia = new LinkedHashMap<>();
        for (JsonNode item : lista) {
            LocalDate dia = LocalDate.parse(texto(item, "data"));
            porDia.merge(dia, item.path("valor").decimalValue(), BigDecimal::add);
        }

        porDia.forEach((dia, valor) -> {
            try {
                lancamentos.lancarAbastecimento(contratoId, new DadosDeAbastecimento(
                        null, dia, valor, null, null, "Importado do controle da obra.", null));
                contagem.abastecimentos++;
            } catch (RuntimeException recusado) {
                contagem.abastecimentoRecusado++;
            }
        });
    }

    /**
     * Carrega lava-jatos e borracharias.
     *
     * <p>O segundo lava-jato da semana entra marcado como não conforme, com a justificativa
     * dizendo que veio da planilha — que é a verdade: a irregularidade estava lá antes do
     * sistema existir, e é justamente o tipo de coisa que a RN-05 quer tornar visível.
     */
    private void carregarServicos(Long contratoId, JsonNode lista, Contagem contagem) {
        for (JsonNode item : lista) {
            TipoDeServico tipo = TipoDeServico.valueOf(texto(item, "tipo"));
            try {
                lancamentos.lancarServico(contratoId, new DadosDeServico(
                        tipo, null, LocalDate.parse(texto(item, "data")),
                        item.path("valor").decimalValue(),
                        "Importado do controle da obra.",
                        "Lançamento histórico importado da planilha da obra, fora da frequência prevista."));
                contagem.servicos++;
            } catch (RuntimeException recusado) {
                contagem.servicoRecusado++;
            }
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
    }

    /** Contadores do relatório da carga. */
    private static final class Contagem {
        private int km;
        private int abastecimentos;
        private int servicos;
        private int kmRecusado;
        private int abastecimentoRecusado;
        private int servicoRecusado;
        private int semContrato;
    }
}
