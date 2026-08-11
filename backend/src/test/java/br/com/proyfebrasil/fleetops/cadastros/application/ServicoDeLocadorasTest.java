package br.com.proyfebrasil.fleetops.cadastros.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.proyfebrasil.fleetops.cadastros.application.ServicoDeLocadoras.DadosDaLocadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.CanaisDeAtendimento;
import br.com.proyfebrasil.fleetops.cadastros.domain.ErroCadastro;
import br.com.proyfebrasil.fleetops.cadastros.domain.Locadora;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoLocadora;
import br.com.proyfebrasil.fleetops.cadastros.infra.LocadoraRepository;
import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import br.com.proyfebrasil.fleetops.shared.security.CriptografiaCredenciais;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RN-20 — credenciais de locadora cifradas, mascaradas e reveladas sob auditoria")
class ServicoDeLocadorasTest {

    @Mock
    private LocadoraRepository locadoras;

    private CriptografiaCredenciais criptografia;
    private ServicoDeLocadoras servico;

    @BeforeEach
    void preparar() {
        String chave = Base64.getEncoder().encodeToString("chave-de-teste-aes-256-bits-32b!".getBytes());
        criptografia = new CriptografiaCredenciais(new FleetOpsProperties(
                new FleetOpsProperties.Jwt(
                        "segredo-de-teste-com-mais-de-32-caracteres", Duration.ofMinutes(15), Duration.ofDays(7), false),
                new FleetOpsProperties.Cripto(chave),
                new FleetOpsProperties.Cors(List.of()),
                new FleetOpsProperties.Alertas(5)));
        servico = new ServicoDeLocadoras(locadoras, criptografia);
    }

    private DadosDaLocadora dadosCom(String login, String senha) {
        return new DadosDaLocadora(
                "Unidas",
                TipoLocadora.NACIONAL,
                "Uarles",
                "(81) 99960-6491",
                "uarles@unidas.com.br",
                "https://portalcorporativo.unidas.com.br",
                login,
                senha,
                new CanaisDeAtendimento("Portal", "0800 771 5158", null, null, null, null, null),
                null,
                true);
    }

    @Test
    @DisplayName("RN20_naoDeveArmazenarCredencialEmTextoClaro")
    void cifraAoCriar() {
        when(locadoras.existeOutraComNome("Unidas", null)).thenReturn(false);
        when(locadoras.save(any(Locadora.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Locadora criada = servico.criar(dadosCom("proyfebrasil", "123@abc"));

        assertThat(criada.getPortalSenhaCifrada()).isNotNull().isNotEqualTo("123@abc");
        assertThat(criada.getPortalLoginCifrado()).isNotNull().isNotEqualTo("proyfebrasil");
        assertThat(criptografia.decifrar(criada.getPortalSenhaCifrada())).isEqualTo("123@abc");
        assertThat(criada.possuiCredenciais()).isTrue();
    }

    @Test
    @DisplayName("RN20_omitirACredencialNaAtualizacaoPreservaAAtual")
    void omitirPreserva() {
        Locadora existente = comCredenciais("proyfebrasil", "123@abc");
        String senhaOriginal = existente.getPortalSenhaCifrada();
        when(locadoras.buscarPorId(1L)).thenReturn(Optional.of(existente));
        when(locadoras.existeOutraComNome("Unidas", 1L)).thenReturn(false);

        // Editar o cadastro sem redigitar a senha não pode apagá-la.
        servico.atualizar(1L, dadosCom(null, null));

        assertThat(existente.getPortalSenhaCifrada()).isEqualTo(senhaOriginal);
        assertThat(criptografia.decifrar(existente.getPortalSenhaCifrada())).isEqualTo("123@abc");
    }

    @Test
    @DisplayName("RN20_enviarCredencialVaziaRemoveASenha")
    void vazioRemove() {
        Locadora existente = comCredenciais("proyfebrasil", "123@abc");
        when(locadoras.buscarPorId(1L)).thenReturn(Optional.of(existente));
        when(locadoras.existeOutraComNome("Unidas", 1L)).thenReturn(false);

        servico.atualizar(1L, dadosCom("", ""));

        assertThat(existente.possuiCredenciais()).isFalse();
    }

    @Test
    @DisplayName("RN20_deveRevelarACredencialEmClaroApenasPeloEndpointDedicado")
    void revela() {
        Locadora existente = comCredenciais("proyfebrasil", "123@abc");
        when(locadoras.buscarPorId(1L)).thenReturn(Optional.of(existente));

        var revelada = servico.revelarCredenciais(1L, "gestor@proyfebrasil.com.br");

        assertThat(revelada.login()).isEqualTo("proyfebrasil");
        assertThat(revelada.senha()).isEqualTo("123@abc");
    }

    @Test
    @DisplayName("RN20_deveRecusarRevelacaoQuandoNaoHaCredencialCadastrada")
    void recusaRevelacaoSemCredencial() {
        Locadora semCredencial = new Locadora("SpeedWay", TipoLocadora.AVULSA);
        ReflectionTestUtils.setField(semCredencial, "id", 2L);
        when(locadoras.buscarPorId(2L)).thenReturn(Optional.of(semCredencial));

        assertThatThrownBy(() -> servico.revelarCredenciais(2L, "gestor@proyfebrasil.com.br"))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.CREDENCIAL_INDISPONIVEL);
    }

    @Test
    @DisplayName("RN20_aExclusaoDescartaAsCredenciaisEmVezDeDeixarSegredoOrfao")
    void exclusaoDescartaCredenciais() {
        Locadora existente = comCredenciais("proyfebrasil", "123@abc");
        when(locadoras.buscarPorId(1L)).thenReturn(Optional.of(existente));

        servico.excluir(1L);

        assertThat(existente.possuiCredenciais()).isFalse();
        assertThat(existente.isExcluida()).isTrue();
    }

    @Test
    @DisplayName("recusa nome de locadora já cadastrado")
    void recusaNomeDuplicado() {
        when(locadoras.existeOutraComNome("Unidas", null)).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(dadosCom(null, null)))
                .isInstanceOf(NegocioException.class)
                .extracting(excecao -> ((NegocioException) excecao).codigoErro())
                .isEqualTo(ErroCadastro.NOME_LOCADORA_DUPLICADO);
        verify(locadoras, never()).save(any());
    }

    @Test
    @DisplayName("cada cifragem usa IV novo, então o mesmo segredo nunca gera o mesmo criptograma")
    void ivAleatorioPorGravacao() {
        when(locadoras.existeOutraComNome(eq("Unidas"), any())).thenReturn(false);
        when(locadoras.save(any(Locadora.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Locadora primeira = servico.criar(dadosCom("login", "mesma-senha"));
        Locadora segunda = servico.criar(dadosCom("login", "mesma-senha"));

        assertThat(primeira.getPortalSenhaCifrada()).isNotEqualTo(segunda.getPortalSenhaCifrada());
    }

    private Locadora comCredenciais(String login, String senha) {
        Locadora locadora = new Locadora("Unidas", TipoLocadora.NACIONAL);
        ReflectionTestUtils.setField(locadora, "id", 1L);
        locadora.definirCredenciaisCifradas(criptografia.cifrar(login), criptografia.cifrar(senha));
        return locadora;
    }
}
