package br.com.proyfebrasil.fleetops.cadastros.domain;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dia da semana em que um posto autoriza abastecimento (RN-04).
 *
 * <p>Existe em vez de usar {@link DayOfWeek} diretamente porque a persistência precisa
 * de uma sigla curta e estável em português — {@code TER,QUI,SAB} —, legível tanto no
 * banco quanto para quem migra os dados vindos das planilhas, onde os dias aparecem
 * escritos por extenso ("terça, quinta e sábado").
 */
public enum DiaDaSemana {

    SEG("Segunda-feira", DayOfWeek.MONDAY),
    TER("Terça-feira", DayOfWeek.TUESDAY),
    QUA("Quarta-feira", DayOfWeek.WEDNESDAY),
    QUI("Quinta-feira", DayOfWeek.THURSDAY),
    SEX("Sexta-feira", DayOfWeek.FRIDAY),
    SAB("Sábado", DayOfWeek.SATURDAY),
    DOM("Domingo", DayOfWeek.SUNDAY);

    private final String descricao;
    private final DayOfWeek equivalente;

    DiaDaSemana(String descricao, DayOfWeek equivalente) {
        this.descricao = descricao;
        this.equivalente = equivalente;
    }

    public String getDescricao() {
        return descricao;
    }

    public DayOfWeek getEquivalente() {
        return equivalente;
    }

    /** Converte um {@link DayOfWeek} do calendário para a sigla do domínio. */
    public static DiaDaSemana de(DayOfWeek dia) {
        return Arrays.stream(values())
                .filter(valor -> valor.equivalente == dia)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Dia da semana desconhecido: " + dia));
    }

    /**
     * Interpreta a lista persistida, ex.: {@code "TER,QUI,SAB"}.
     *
     * <p>Texto vazio devolve conjunto vazio, que por convenção significa "sem restrição
     * de dia" — e não "nenhum dia autorizado".
     */
    public static Set<DiaDaSemana> interpretar(String lista) {
        if (lista == null || lista.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(lista.split(","))
                .map(String::trim)
                .filter(texto -> !texto.isEmpty())
                .map(texto -> valueOf(texto.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Serializa o conjunto na ordem natural da semana, para persistência estável. */
    public static String serializar(Set<DiaDaSemana> dias) {
        if (dias == null || dias.isEmpty()) {
            return "";
        }
        return Arrays.stream(values())
                .filter(dias::contains)
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }
}
