import type { components } from '@/lib/api/schema'

/**
 * Tipos de autenticação derivados da OpenAPI.
 *
 * Nada é redigitado à mão: qualquer mudança no contrato do backend aparece aqui
 * como erro de compilação, que é exatamente o comportamento desejado.
 */

export type Usuario = components['schemas']['UsuarioResponse']
export type Sessao = components['schemas']['SessaoResponse']
export type Perfil = Usuario['perfil']

/** Rótulos em pt-BR dos perfis de acesso (RN-19). */
export const ROTULOS_DE_PERFIL: Record<Perfil, string> = {
  ADMIN: 'Administrador',
  GESTOR_FROTA: 'Gestor de frota',
  CONSULTA: 'Consulta',
}

/** Descrição do que cada perfil pode fazer, exibida na administração de usuários. */
export const DESCRICOES_DE_PERFIL: Record<Perfil, string> = {
  ADMIN: 'Acesso total, incluindo administração de usuários.',
  GESTOR_FROTA: 'Operação completa da frota, sem administrar usuários.',
  CONSULTA: 'Somente leitura, sem acesso a credenciais de fornecedores e locadoras.',
}
