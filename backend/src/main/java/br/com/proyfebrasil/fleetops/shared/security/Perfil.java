package br.com.proyfebrasil.fleetops.shared.security;

import java.util.Arrays;

/**
 * Perfis de acesso do sistema (RN-19).
 *
 * <p>Vive em {@code shared} porque o controle de acesso é transversal a todos os módulos:
 * qualquer endpoint declara suas exigências em termos destes perfis.
 *
 * <ul>
 *   <li>{@link #ADMIN} — acesso total, incluindo administração de usuários;</li>
 *   <li>{@link #GESTOR_FROTA} — operação completa da frota, sem administrar usuários;</li>
 *   <li>{@link #CONSULTA} — somente leitura, sem acesso a credenciais de fornecedores
 *       e locadoras (RN-20).</li>
 * </ul>
 */
public enum Perfil {

    ADMIN("Administrador"),
    GESTOR_FROTA("Gestor de frota"),
    CONSULTA("Consulta");

    /** Prefixo exigido pelo Spring Security para autoridades baseadas em papel. */
    public static final String PREFIXO_AUTHORITY = "ROLE_";

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    /** Rótulo em pt-BR para exibição na interface. */
    public String getDescricao() {
        return descricao;
    }

    /** Autoridade correspondente no Spring Security, ex.: {@code ROLE_GESTOR_FROTA}. */
    public String authority() {
        return PREFIXO_AUTHORITY + name();
    }

    /** Converte um nome vindo do banco ou de um token, rejeitando valores desconhecidos. */
    public static Perfil de(String nome) {
        return Arrays.stream(values())
                .filter(perfil -> perfil.name().equals(nome))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil desconhecido: " + nome));
    }
}
