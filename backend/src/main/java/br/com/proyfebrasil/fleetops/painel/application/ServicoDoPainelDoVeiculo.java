package br.com.proyfebrasil.fleetops.painel.application;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos;
import br.com.proyfebrasil.fleetops.cadastros.domain.GrupoTarifario;
import br.com.proyfebrasil.fleetops.cadastros.domain.TabelaPreco;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.cadastros.infra.TabelaPrecoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Painel de um veículo específico.
 *
 * <p>Junta o cadastro do veículo com a grade tarifária que se aplica a ele — o cruzamento
 * que hoje obriga o gestor a abrir a planilha de preços, achar a locadora, achar o grupo
 * e ler a linha. Aqui o dado chega pronto: quanto custa este veículo em cada pacote de
 * quilometragem e quanto custa o KM que passar da franquia.
 */
@Service
public class ServicoDoPainelDoVeiculo {

    private final ServicoDeVeiculos veiculos;
    private final TabelaPrecoRepository tabelas;
    private final Clock relogio;

    public ServicoDoPainelDoVeiculo(
            ServicoDeVeiculos veiculos, TabelaPrecoRepository tabelas, Clock relogio) {
        this.veiculos = veiculos;
        this.tabelas = tabelas;
        this.relogio = relogio;
    }

    /** Uma opção de pacote com seu valor mensal e o custo do KM que exceder a franquia. */
    public record OpcaoDePacote(int pacoteKm, BigDecimal valorMensal, BigDecimal valorKmExcedente) {
    }

    /**
     * Grade tarifária aplicável ao veículo.
     *
     * @param grupo           código do grupo na tabela da locadora
     * @param veiculosDoGrupo modelos que compartilham o grupo — e portanto o preço
     * @param pacotes         opções de franquia, da menor para a maior
     */
    public record GradeDoVeiculo(
            int anoVigencia,
            String grupo,
            String veiculosDoGrupo,
            List<OpcaoDePacote> pacotes,
            BigDecimal menorValorMensal) {
    }

    /**
     * Painel completo de um veículo.
     *
     * @param grade ausente quando o veículo não tem grupo tarifário informado ou a
     *     locadora não tem vigência do ano — os dois casos aparecem na central de
     *     pendências, e a interface explica qual deles ocorreu
     */
    public record PainelDoVeiculo(Veiculo veiculo, Optional<GradeDoVeiculo> grade, String motivoSemGrade) {
    }

    @Transactional(readOnly = true)
    public PainelDoVeiculo apurar(Long veiculoId) {
        Veiculo veiculo = veiculos.buscar(veiculoId);
        int ano = LocalDate.now(relogio).getYear();

        if (veiculo.getGrupoTarifario() == null || veiculo.getGrupoTarifario().isBlank()) {
            return new PainelDoVeiculo(
                    veiculo,
                    Optional.empty(),
                    "Este veículo não tem grupo tarifário informado, então não há como "
                            + "localizar o preço dele na tabela da locadora.");
        }

        Optional<TabelaPreco> vigencia =
                tabelas.buscarVigencia(veiculo.getLocadora().getId(), ano);
        if (vigencia.isEmpty()) {
            return new PainelDoVeiculo(
                    veiculo,
                    Optional.empty(),
                    "A locadora %s não tem tabela de preços cadastrada para %d."
                            .formatted(veiculo.getLocadora().getNome(), ano));
        }

        TabelaPreco tabela = vigencia.get();
        Optional<GrupoTarifario> grupo = tabela.buscarGrupo(veiculo.getGrupoTarifario());
        if (grupo.isEmpty()) {
            return new PainelDoVeiculo(
                    veiculo,
                    Optional.empty(),
                    "O grupo \"%s\" não existe na tabela de %s para %d."
                            .formatted(veiculo.getGrupoTarifario(), veiculo.getLocadora().getNome(), ano));
        }

        List<OpcaoDePacote> pacotes = grupo.get().getPacotes().stream()
                .map(pacote -> new OpcaoDePacote(
                        pacote.getPacoteKm(),
                        pacote.getValorMensal(),
                        tabela.valorKmExcedente(veiculo.getCategoria(), pacote.getPacoteKm()).orElse(null)))
                .toList();

        BigDecimal menor = pacotes.stream()
                .map(OpcaoDePacote::valorMensal)
                .min(BigDecimal::compareTo)
                .orElse(null);

        return new PainelDoVeiculo(
                veiculo,
                Optional.of(new GradeDoVeiculo(
                        tabela.getAnoVigencia(),
                        grupo.get().getCodigo(),
                        grupo.get().getVeiculosDoGrupo(),
                        pacotes,
                        menor)),
                null);
    }
}
