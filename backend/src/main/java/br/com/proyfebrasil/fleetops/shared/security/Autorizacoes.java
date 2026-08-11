package br.com.proyfebrasil.fleetops.shared.security;

/**
 * Expressões de autorização reutilizadas nos controllers (RN-19).
 *
 * <p>Existem como constantes porque {@code @PreAuthorize} exige literais em tempo de
 * compilação: sem elas, a mesma expressão SpEL seria copiada dezenas de vezes, e um erro
 * de digitação em uma delas abriria um endpoint sem que nada acusasse.
 */
public final class Autorizacoes {

    /** Leitura de dados operacionais: todos os perfis autenticados. */
    public static final String LEITURA = "hasAnyRole('ADMIN', 'GESTOR_FROTA', 'CONSULTA')";

    /** Escrita em cadastros e operação: administradores e gestores de frota. */
    public static final String OPERACAO = "hasAnyRole('ADMIN', 'GESTOR_FROTA')";

    /**
     * Acesso a credenciais de portais (RN-20).
     *
     * <p>Deliberadamente exclui {@code CONSULTA}: a especificação define esse perfil como
     * somente leitura <em>e sem acesso a credenciais</em> de fornecedores e locadoras.
     */
    public static final String CREDENCIAIS = "hasAnyRole('ADMIN', 'GESTOR_FROTA')";

    /** Administração do sistema: exclusiva de administradores. */
    public static final String ADMINISTRACAO = "hasRole('ADMIN')";

    private Autorizacoes() {
    }
}
