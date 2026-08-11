package br.com.proyfebrasil.fleetops.conformidade.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.conformidade.domain.PadraoRingelmann;
import br.com.proyfebrasil.fleetops.conformidade.domain.TesteDeFumacaPreta;
import br.com.proyfebrasil.fleetops.conformidade.infra.TesteDeFumacaPretaRepository;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.exception.RecursoNaoEncontradoException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de fumaça preta (FOR.MA.01, RN-09).
 *
 * <p>Além de registrar, este serviço responde a pergunta que a retirada faz: <em>este
 * veículo pode entrar em operação?</em> A resposta considera o teste <strong>mais
 * recente</strong>, não o melhor — um veículo aprovado em janeiro e reprovado em março
 * está reprovado, e é a última medição que descreve o estado do motor hoje.
 */
@Service
public class ServicoDeFumacaPreta {

    private final TesteDeFumacaPretaRepository testes;

    public ServicoDeFumacaPreta(TesteDeFumacaPretaRepository testes) {
        this.testes = testes;
    }

    /** Dados de um teste. */
    public record DadosDoTeste(
            Veiculo veiculo,
            ContratoDeLocacao contrato,
            Condutor condutor,
            Obra obra,
            LocalDate dataDoTeste,
            PadraoRingelmann padrao,
            int altitudeEmMetros,
            String observacoes) {
    }

    /**
     * Situação do veículo quanto à RN-09.
     *
     * @param exigido se o veículo é a diesel e portanto precisa do teste
     * @param teste a última medição, quando existe
     */
    public record SituacaoDaFumaca(boolean exigido, Optional<TesteDeFumacaPreta> teste) {

        /** Indica se o veículo está liberado para operar quanto a esta regra. */
        public boolean liberado() {
            return !exigido || teste.map(TesteDeFumacaPreta::isConforme).orElse(false);
        }

        /** Indica se falta realizar o teste — distinto de tê-lo realizado e reprovado. */
        public boolean pendente() {
            return exigido && teste.isEmpty();
        }

        /** Indica que o teste existe e reprovou. */
        public boolean reprovado() {
            return exigido && teste.map(t -> !t.isConforme()).orElse(false);
        }
    }

    @Transactional
    public TesteDeFumacaPreta registrar(DadosDoTeste dados) {
        var teste = new TesteDeFumacaPreta(
                dados.veiculo(), dados.dataDoTeste(), dados.padrao(), dados.altitudeEmMetros());
        teste.vincular(dados.contrato(), dados.condutor(), dados.obra());
        teste.alterarObservacoes(dados.observacoes());
        return testes.save(teste);
    }

    /**
     * Situação do veículo quanto à RN-09.
     *
     * <p>Veículo que não é a diesel devolve "não exigido" — e não "aprovado". A distinção
     * importa na tela: um Polo a gasolina não deve aparecer com selo de teste aprovado que
     * ninguém fez.
     */
    @Transactional(readOnly = true)
    public SituacaoDaFumaca situacaoDe(Veiculo veiculo) {
        boolean exigido = veiculo.exigeTesteDeFumacaPreta();
        if (!exigido) {
            return new SituacaoDaFumaca(false, Optional.empty());
        }
        return new SituacaoDaFumaca(true, testes.maisRecenteDoVeiculo(veiculo.getId()));
    }

    @Transactional(readOnly = true)
    public List<TesteDeFumacaPreta> historicoDe(Long veiculoId) {
        return testes.historicoDoVeiculo(veiculoId);
    }

    @Transactional(readOnly = true)
    public TesteDeFumacaPreta buscar(Long id) {
        return testes.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Teste de fumaça preta", id));
    }
}
