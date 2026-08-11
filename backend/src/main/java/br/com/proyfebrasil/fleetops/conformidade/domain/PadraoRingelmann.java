package br.com.proyfebrasil.fleetops.conformidade.domain;

import java.util.Arrays;

/**
 * Escala de Ringelmann — a régua do FOR.MA.01 (Seção 3.4).
 *
 * <p>Cada padrão corresponde a uma faixa de opacidade da fumaça, em passos de 20%. O
 * avaliador compara a fumaça do escapamento com a cartela e anota o padrão; o percentual
 * existe aqui porque é o que a cartela mostra, e a tela reproduz a cartela.
 */
public enum PadraoRingelmann {

    PADRAO_1(1, 20, "Padrão 1 — 20%"),
    PADRAO_2(2, 40, "Padrão 2 — 40%"),
    PADRAO_3(3, 60, "Padrão 3 — 60%"),
    PADRAO_4(4, 80, "Padrão 4 — 80%"),
    PADRAO_5(5, 100, "Padrão 5 — 100%");

    private final int codigo;
    private final int opacidadePercentual;
    private final String descricao;

    PadraoRingelmann(int codigo, int opacidadePercentual, String descricao) {
        this.codigo = codigo;
        this.opacidadePercentual = opacidadePercentual;
        this.descricao = descricao;
    }

    public int getCodigo() {
        return codigo;
    }

    public int getOpacidadePercentual() {
        return opacidadePercentual;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Converte o número anotado no formulário para o padrão correspondente. */
    public static PadraoRingelmann porCodigo(int codigo) {
        return Arrays.stream(values())
                .filter(padrao -> padrao.codigo == codigo)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "A escala de Ringelmann vai do Padrão 1 ao 5; recebido: %d.".formatted(codigo)));
    }
}
