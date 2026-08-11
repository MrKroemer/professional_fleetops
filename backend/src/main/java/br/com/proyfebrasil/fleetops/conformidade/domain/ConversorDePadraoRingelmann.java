package br.com.proyfebrasil.fleetops.conformidade.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Grava o padrão como o número da cartela, não como ordinal nem como nome.
 *
 * <p>O ordinal quebraria se a ordem da enumeração mudasse, e o nome ocuparia espaço para
 * dizer o que um dígito já diz. O número gravado é o mesmo que o avaliador escreveu no
 * formulário em papel — o que também deixa a coluna legível em consulta direta ao banco,
 * e é o que a coluna gerada `conforme` compara.
 */
@Converter(autoApply = false)
public class ConversorDePadraoRingelmann implements AttributeConverter<PadraoRingelmann, Short> {

    @Override
    public Short convertToDatabaseColumn(PadraoRingelmann padrao) {
        return padrao == null ? null : (short) padrao.getCodigo();
    }

    @Override
    public PadraoRingelmann convertToEntityAttribute(Short codigo) {
        return codigo == null ? null : PadraoRingelmann.porCodigo(codigo);
    }
}
