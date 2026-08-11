package br.com.proyfebrasil.fleetops.contratos.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Ângulos do book fotográfico de retirada e devolução (Seção 3.2, RN-12).
 *
 * <p>A lista vem literalmente da especificação: quatro lados, pneus, hodômetro, motor,
 * porta-malas e avarias. Os oito primeiros são obrigatórios; AVARIAS é condicional — só
 * existe quando há avaria a registrar, e por isso não entra na conta do book completo.
 * Exigi-lo tornaria impossível concluir a retirada de um carro sem avaria, que é o caso
 * comum e desejável.
 *
 * <p>A ordem da declaração é a ordem em que a tela pede as fotos, e ela importa: quem
 * fotografa no pátio segue dando a volta no veículo. Pedir o hodômetro entre a lateral
 * esquerda e a direita obrigaria a entrar no carro no meio da volta.
 */
public enum ItemDoBook {

    FRENTE("Frente", true),
    LATERAL_DIREITA("Lateral direita", true),
    TRASEIRA("Traseira", true),
    LATERAL_ESQUERDA("Lateral esquerda", true),
    PNEUS("Pneus", true),
    MOTOR("Motor", true),
    PORTA_MALAS("Porta-malas", true),
    HODOMETRO("Hodômetro", true),
    AVARIAS("Avarias", false);

    private final String descricao;
    private final boolean obrigatorio;

    ItemDoBook(String descricao, boolean obrigatorio) {
        this.descricao = descricao;
        this.obrigatorio = obrigatorio;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isObrigatorio() {
        return obrigatorio;
    }

    /** Aceita mais de uma foto — um veículo pode ter várias avarias, cada uma com registro próprio. */
    public boolean aceitaVarias() {
        return this == AVARIAS;
    }

    /** Itens sem os quais o evento não pode ser concluído (RN-12). */
    public static List<ItemDoBook> obrigatorios() {
        return Arrays.stream(values()).filter(ItemDoBook::isObrigatorio).toList();
    }
}
