package br.com.proyfebrasil.fleetops.conformidade.api;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeCondutores;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeObras;
import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeVeiculos;
import br.com.proyfebrasil.fleetops.conformidade.application.ServicoDeFumacaPreta;
import br.com.proyfebrasil.fleetops.conformidade.application.ServicoDeFumacaPreta.DadosDoTeste;
import br.com.proyfebrasil.fleetops.conformidade.domain.PadraoRingelmann;
import br.com.proyfebrasil.fleetops.contratos.api.CicloDeVidaMapper;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.SituacaoDaFumacaResponse;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.TesteDeFumacaRequest;
import br.com.proyfebrasil.fleetops.contratos.api.dto.ContratoDtos.TesteDeFumacaResponse;
import br.com.proyfebrasil.fleetops.contratos.application.ServicoDeContratos;
import br.com.proyfebrasil.fleetops.shared.exception.ErroComum;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.security.Autorizacoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teste de fumaça preta — FOR.MA.01 (RN-09).
 *
 * <p>Vive no módulo de conformidade, como manda a Seção 5, embora seja consumido pela
 * tela de retirada: é uma medição do veículo, não um passo do contrato. O mesmo veículo
 * pode ser testado fora de qualquer retirada, e o histórico segue o veículo.
 */
@RestController
@RequestMapping("/api/v1/fumaca-preta")
@Tag(name = "Fumaça preta", description = "Teste de opacidade na escala de Ringelmann (FOR.MA.01)")
public class FumacaPretaController {

    private final ServicoDeFumacaPreta fumacaPreta;
    private final ServicoDeVeiculos veiculos;
    private final ServicoDeCondutores condutores;
    private final ServicoDeObras obras;
    private final ServicoDeContratos contratos;
    private final CicloDeVidaMapper mapeador;

    public FumacaPretaController(
            ServicoDeFumacaPreta fumacaPreta,
            ServicoDeVeiculos veiculos,
            ServicoDeCondutores condutores,
            ServicoDeObras obras,
            ServicoDeContratos contratos,
            CicloDeVidaMapper mapeador) {
        this.fumacaPreta = fumacaPreta;
        this.veiculos = veiculos;
        this.condutores = condutores;
        this.obras = obras;
        this.contratos = contratos;
        this.mapeador = mapeador;
    }

    @GetMapping("/veiculos/{veiculoId}")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Situação do veículo quanto à RN-09")
    public SituacaoDaFumacaResponse situacao(@PathVariable Long veiculoId) {
        return mapeador.paraResposta(fumacaPreta.situacaoDe(veiculos.buscar(veiculoId)));
    }

    @GetMapping("/veiculos/{veiculoId}/historico")
    @PreAuthorize(Autorizacoes.LEITURA)
    @Operation(summary = "Todos os testes já feitos no veículo")
    public List<TesteDeFumacaResponse> historico(@PathVariable Long veiculoId) {
        return fumacaPreta.historicoDe(veiculoId).stream().map(mapeador::paraResposta).toList();
    }

    @PostMapping
    @PreAuthorize(Autorizacoes.OPERACAO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra um teste de fumaça preta")
    public TesteDeFumacaResponse registrar(@Valid @RequestBody TesteDeFumacaRequest requisicao) {
        var teste = fumacaPreta.registrar(new DadosDoTeste(
                veiculos.buscar(requisicao.veiculoId()),
                requisicao.contratoId() == null ? null : contratos.buscar(requisicao.contratoId()),
                requisicao.condutorId() == null ? null : condutores.buscar(requisicao.condutorId()),
                requisicao.obraId() == null ? null : obras.buscar(requisicao.obraId()),
                requisicao.dataDoTeste(),
                padraoDe(requisicao.padrao()),
                requisicao.altitudeEmMetros() == null ? 0 : requisicao.altitudeEmMetros(),
                requisicao.observacoes()));
        return mapeador.paraResposta(teste);
    }

    /**
     * Converte o número da cartela em padrão, devolvendo 400 para valores fora da escala.
     *
     * <p>Sem isto, um {@code 7} viraria {@link IllegalArgumentException} e o manipulador
     * global o classificaria como falha interna — 500 para um erro de quem digitou.
     */
    private static PadraoRingelmann padraoDe(Integer codigo) {
        try {
            return PadraoRingelmann.porCodigo(codigo);
        } catch (IllegalArgumentException erro) {
            throw new NegocioException(ErroComum.VALIDACAO, erro.getMessage());
        }
    }
}
