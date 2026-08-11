package br.com.proyfebrasil.fleetops.cadastros.domain;

import br.com.proyfebrasil.fleetops.shared.exception.CodigoErro;
import org.springframework.http.HttpStatus;

/**
 * Erros de negócio dos cadastros.
 *
 * <p>Os códigos que derivam diretamente de uma regra numerada carregam o número dela,
 * de modo que uma resposta de erro seja rastreável até a especificação sem consulta a
 * documentação intermediária.
 */
public enum ErroCadastro implements CodigoErro {

    PLACA_DUPLICADA(
            "RN-002-PLACA_DUPLICADA",
            "Placa já cadastrada",
            HttpStatus.CONFLICT),
    PLACA_INVALIDA(
            "RN-002-PLACA_INVALIDA",
            "Placa inválida",
            HttpStatus.BAD_REQUEST),
    VIGENCIA_DUPLICADA(
            "RN-014-VIGENCIA_DUPLICADA",
            "Vigência já cadastrada para a locadora",
            HttpStatus.CONFLICT),
    VIGENCIA_INEXISTENTE(
            "RN-014-VIGENCIA_INEXISTENTE",
            "Nenhuma tabela de preços vigente para a competência",
            HttpStatus.NOT_FOUND),
    CNH_VENCIDA(
            "RN-016-CNH_VENCIDA",
            "CNH vencida",
            HttpStatus.CONFLICT),
    CREDENCIAL_INDISPONIVEL(
            "RN-020-CREDENCIAL_INDISPONIVEL",
            "Credencial não cadastrada",
            HttpStatus.NOT_FOUND),
    CODIGO_OBRA_DUPLICADO(
            "CAD-001-CODIGO_OBRA_DUPLICADO",
            "Código de obra já utilizado",
            HttpStatus.CONFLICT),
    NOME_LOCADORA_DUPLICADO(
            "CAD-002-NOME_LOCADORA_DUPLICADO",
            "Locadora já cadastrada",
            HttpStatus.CONFLICT),
    CPF_DUPLICADO(
            "CAD-003-CPF_DUPLICADO",
            "CPF já cadastrado",
            HttpStatus.CONFLICT),
    CPF_INVALIDO(
            "CAD-004-CPF_INVALIDO",
            "CPF inválido",
            HttpStatus.BAD_REQUEST),
    FORNECEDOR_DUPLICADO(
            "CAD-005-FORNECEDOR_DUPLICADO",
            "Fornecedor já cadastrado para este tipo",
            HttpStatus.CONFLICT),
    DADOS_INCOMPATIVEIS_COM_O_TIPO(
            "CAD-006-DADOS_INCOMPATIVEIS_COM_O_TIPO",
            "Dados incompatíveis com o tipo de fornecedor",
            HttpStatus.BAD_REQUEST),
    OBRA_ENCERRADA(
            "CAD-007-OBRA_ENCERRADA",
            "Obra encerrada",
            HttpStatus.CONFLICT),
    GRUPO_TARIFARIO_DUPLICADO(
            "CAD-008-GRUPO_TARIFARIO_DUPLICADO",
            "Grupo tarifário repetido na vigência",
            HttpStatus.CONFLICT);

    private final String codigo;
    private final String titulo;
    private final HttpStatus status;

    ErroCadastro(String codigo, String titulo, HttpStatus status) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.status = status;
    }

    @Override
    public String codigo() {
        return codigo;
    }

    @Override
    public String titulo() {
        return titulo;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
