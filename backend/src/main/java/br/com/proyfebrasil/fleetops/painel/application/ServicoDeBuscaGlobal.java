package br.com.proyfebrasil.fleetops.painel.application;

import br.com.proyfebrasil.fleetops.cadastros.domain.Placa;
import br.com.proyfebrasil.fleetops.painel.infra.BuscaGlobalRepository;
import br.com.proyfebrasil.fleetops.painel.infra.BuscaGlobalRepository.Achado;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca global da barra superior (Seção 6.1).
 *
 * <p>Procura por placa, condutor e obra — as três formas pelas quais o gestor se
 * refere a um veículo no dia a dia. Devolve poucos resultados de cada tipo, com a rota
 * que abre o registro: uma busca que apenas informa "existe" obrigaria o usuário a
 * procurar de novo no menu.
 */
@Service
public class ServicoDeBuscaGlobal {

    /** Mínimo de caracteres para consultar — abaixo disso o resultado seria a base inteira. */
    public static final int TERMO_MINIMO = 2;

    /** Resultados por tipo. Poucos, porque a lista fica sob o campo e precisa caber na tela. */
    private static final Pageable LIMITE = PageRequest.of(0, 5);

    /**
     * Curinga usado quando o termo não é uma busca por CPF.
     *
     * <p>O curinga vazio (<code>%%</code>) casaria com <em>todo</em> CPF e traria a tabela
     * inteira de condutores. O espaço nunca aparece em um CPF, gravado só com dígitos, de
     * modo que este padrão não casa com nada — que é a intenção.
     */
    private static final String NENHUM_CPF = "% %";

    /** Abaixo disso, um trecho de dígitos casa CPF demais para significar alguma coisa. */
    private static final int DIGITOS_MINIMOS_DE_CPF = 3;

    /** Um termo só se parece com CPF se tiver apenas dígitos e os separadores usuais. */
    private static final Pattern PARECE_CPF = Pattern.compile("[0-9.\\-\\s]+");

    private final BuscaGlobalRepository busca;

    public ServicoDeBuscaGlobal(BuscaGlobalRepository busca) {
        this.busca = busca;
    }

    /** Um resultado, com a rota que o abre. */
    public record Resultado(String tipo, Long id, String rotulo, String detalhe, String rota) {
    }

    /** Resultados agrupados por tipo, na ordem em que a interface os exibe. */
    public record Resultados(
            List<Resultado> veiculos, List<Resultado> condutores, List<Resultado> obras, int total) {
    }

    @Transactional(readOnly = true)
    public Resultados buscar(String termo) {
        String limpo = termo == null ? "" : termo.trim();
        if (limpo.length() < TERMO_MINIMO) {
            return new Resultados(List.of(), List.of(), List.of(), 0);
        }

        String curinga = "%" + limpo.toLowerCase(Locale.ROOT) + "%";
        // A placa é gravada sem separadores; procurar por "abc-1d23" precisa achar
        // "ABC1D23". O mesmo vale para o CPF, guardado só com dígitos.
        String curingaDePlaca = "%" + limpo.replaceAll("[\\s.-]", "").toLowerCase(Locale.ROOT) + "%";
        // Só busca por CPF quando o termo é de fato numérico. Extrair os dígitos de um
        // termo misto casaria coisas sem sentido: a placa "TEL2B29" viraria "229" e traria
        // qualquer condutor cujo CPF contenha esse trecho.
        String digitos = limpo.replaceAll("\\D", "");
        boolean buscaPorCpf =
                PARECE_CPF.matcher(limpo).matches() && digitos.length() >= DIGITOS_MINIMOS_DE_CPF;
        String curingaDeCpf = buscaPorCpf ? "%" + digitos + "%" : NENHUM_CPF;

        List<Resultado> veiculos = converter(
                busca.veiculos(curinga, curingaDePlaca, LIMITE),
                "VEICULO",
                achado -> "/cadastros/veiculos/" + achado.getId(),
                achado -> Placa.formatar(achado.getRotulo()));

        List<Resultado> condutores = converter(
                busca.condutores(curinga, curingaDeCpf, LIMITE),
                "CONDUTOR",
                achado -> "/cadastros/condutores",
                Achado::getRotulo);

        List<Resultado> obras = converter(
                busca.obras(curinga, LIMITE), "OBRA", achado -> "/cadastros/obras", Achado::getRotulo);

        return new Resultados(
                veiculos, condutores, obras, veiculos.size() + condutores.size() + obras.size());
    }

    private static List<Resultado> converter(
            List<Achado> achados,
            String tipo,
            Function<Achado, String> rota,
            Function<Achado, String> rotulo) {
        return achados.stream()
                .map(achado -> new Resultado(
                        tipo, achado.getId(), rotulo.apply(achado), achado.getDetalhe(), rota.apply(achado)))
                .toList();
    }
}
