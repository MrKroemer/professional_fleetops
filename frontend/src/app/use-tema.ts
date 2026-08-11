import { useContext } from 'react'

import { ContextoDeTema } from './tema'

/** Acesso ao tema atual. Falha explicitamente fora do provedor, em vez de devolver um padrão silencioso. */
export function useTema() {
  const contexto = useContext(ContextoDeTema)
  if (!contexto) {
    throw new Error('useTema precisa estar dentro de <ProvedorDeTema>')
  }
  return contexto
}
