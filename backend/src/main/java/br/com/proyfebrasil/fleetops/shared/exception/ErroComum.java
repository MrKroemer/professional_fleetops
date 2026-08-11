package br.com.proyfebrasil.fleetops.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Erros transversais, não atrelados a uma regra de negócio específica.
 * Erros decorrentes de regras {@code RN-xx} são declarados no enum do módulo dono da regra.
 */
public enum ErroComum implements CodigoErro {

    RECURSO_NAO_ENCONTRADO("GEN-001-RECURSO_NAO_ENCONTRADO", "Recurso não encontrado", HttpStatus.NOT_FOUND),
    VALIDACAO("GEN-002-VALIDACAO", "Dados inválidos", HttpStatus.BAD_REQUEST),
    CREDENCIAIS_INVALIDAS("GEN-003-CREDENCIAIS_INVALIDAS", "Credenciais inválidas", HttpStatus.UNAUTHORIZED),
    NAO_AUTENTICADO("GEN-004-NAO_AUTENTICADO", "Autenticação necessária", HttpStatus.UNAUTHORIZED),
    ACESSO_NEGADO("GEN-005-ACESSO_NEGADO", "Acesso negado para o seu perfil", HttpStatus.FORBIDDEN),
    CONFLITO_DE_ESTADO("GEN-006-CONFLITO_DE_ESTADO", "Conflito com o estado atual do recurso", HttpStatus.CONFLICT),
    SESSAO_INVALIDA("GEN-007-SESSAO_INVALIDA", "Sessão expirada ou revogada", HttpStatus.UNAUTHORIZED),
    USUARIO_INATIVO("GEN-008-USUARIO_INATIVO", "Usuário inativo", HttpStatus.FORBIDDEN),
    REQUISICAO_MALFORMADA("GEN-009-REQUISICAO_MALFORMADA", "Requisição malformada", HttpStatus.BAD_REQUEST),
    METODO_NAO_SUPORTADO("GEN-010-METODO_NAO_SUPORTADO", "Método HTTP não suportado", HttpStatus.METHOD_NOT_ALLOWED),
    MIDIA_NAO_SUPORTADA("GEN-011-MIDIA_NAO_SUPORTADA", "Formato de conteúdo não suportado",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    ERRO_INTERNO("GEN-500-ERRO_INTERNO", "Erro interno inesperado", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String codigo;
    private final String titulo;
    private final HttpStatus status;

    ErroComum(String codigo, String titulo, HttpStatus status) {
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
