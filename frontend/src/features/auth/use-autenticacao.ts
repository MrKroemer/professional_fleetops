import { useContext } from 'react'

import { ContextoDeAutenticacao } from './autenticacao'

/** Acesso à sessão atual. Falha explicitamente fora do provedor. */
export function useAutenticacao() {
  const contexto = useContext(ContextoDeAutenticacao)
  if (!contexto) {
    throw new Error('useAutenticacao precisa estar dentro de <ProvedorDeAutenticacao>')
  }
  return contexto
}
