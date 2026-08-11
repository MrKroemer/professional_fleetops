package br.com.proyfebrasil.fleetops.shared.security;

/**
 * Dados mínimos do usuário necessários para emitir um token.
 *
 * <p>Deliberadamente feito de tipos primitivos, e não da entidade {@code Usuario}: assim a
 * camada {@code shared} não passa a depender de um módulo de domínio.
 *
 * @param usuarioId identificador do usuário
 * @param email     e-mail, usado como nome do principal e como autor nas auditorias
 * @param nome      nome de exibição
 * @param perfil    perfil de acesso (RN-19)
 */
public record DadosDoToken(Long usuarioId, String email, String nome, Perfil perfil) {
}
