package br.com.proyfebrasil.fleetops.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RN-20 — credenciais de portais são cifradas e mascaradas")
class CriptografiaCredenciaisTest {

    private static final String CHAVE_VALIDA =
            Base64.getEncoder().encodeToString("chave-de-teste-aes-256-bits-32b!".getBytes());

    private CriptografiaCredenciais criptografia;

    @BeforeEach
    void preparar() {
        criptografia = new CriptografiaCredenciais(propriedadesCom(CHAVE_VALIDA));
    }

    @Test
    @DisplayName("RN20_deveRecuperarOValorOriginalAoDecifrar")
    void cicloCompleto() {
        String segredo = "senha-do-portal-unidas#2026";
        String cifrado = criptografia.cifrar(segredo);

        assertThat(cifrado).isNotNull().isNotEqualTo(segredo);
        assertThat(criptografia.decifrar(cifrado)).isEqualTo(segredo);
    }

    @Test
    @DisplayName("RN20_deveProduzirCriptogramasDiferentesParaOMesmoSegredo")
    void ivAleatorio() {
        String segredo = "mesma-senha";

        String primeiro = criptografia.cifrar(segredo);
        String segundo = criptografia.cifrar(segredo);

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(criptografia.decifrar(primeiro)).isEqualTo(criptografia.decifrar(segundo));
    }

    @Test
    @DisplayName("RN20_deveRejeitarCriptogramaAdulterado")
    void detectaAdulteracao() {
        String cifrado = criptografia.cifrar("senha-do-portal");
        byte[] bytes = Base64.getDecoder().decode(cifrado);
        bytes[bytes.length - 1] ^= 0x01;
        String adulterado = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> criptografia.decifrar(adulterado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao decifrar");
    }

    @Test
    @DisplayName("RN20_deveRejeitarChaveDeTamanhoIncorreto")
    void chaveInvalida() {
        FleetOpsProperties curta = propriedadesCom(Base64.getEncoder().encodeToString("curta".getBytes()));

        assertThatThrownBy(() -> new CriptografiaCredenciais(curta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("RN20_deveMascararCredencialParaExibicao")
    void mascara() {
        assertThat(CriptografiaCredenciais.mascaraPara(true))
                .isEqualTo(CriptografiaCredenciais.MASCARA_PADRAO);
        assertThat(CriptografiaCredenciais.mascaraPara(false)).isEmpty();
    }

    @Test
    @DisplayName("RN20_aMascaraNaoRevelaOComprimentoDaSenha")
    void mascaraTemComprimentoFixo() {
        // Uma máscara proporcional entregaria o tamanho da senha a quem observa a tela.
        assertThat(CriptografiaCredenciais.mascaraPara(true))
                .hasSize(CriptografiaCredenciais.MASCARA_PADRAO.length());
    }

    @Test
    @DisplayName("valores nulos atravessam sem cifragem")
    void nuloPermaneceNulo() {
        assertThat(criptografia.cifrar(null)).isNull();
        assertThat(criptografia.decifrar(null)).isNull();
    }

    private FleetOpsProperties propriedadesCom(String chave) {
        return new FleetOpsProperties(
                new FleetOpsProperties.Jwt(
                        "segredo-de-teste-com-mais-de-32-caracteres", Duration.ofMinutes(15), Duration.ofDays(7), false),
                new FleetOpsProperties.Cripto(chave),
                new FleetOpsProperties.Cors(List.of()),
                new FleetOpsProperties.Alertas(5));
    }
}
