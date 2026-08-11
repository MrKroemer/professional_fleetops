import { useEffect, useState } from 'react'

/**
 * Adia a propagação de um valor enquanto ele continua mudando.
 *
 * Usado nos campos de busca das listagens: sem isso, cada tecla digitada dispararia
 * uma consulta ao servidor.
 */
export function useDebounce<T>(valor: T, atrasoMs = 350): T {
  const [adiado, definirAdiado] = useState(valor)

  useEffect(() => {
    const temporizador = setTimeout(() => {
      definirAdiado(valor)
    }, atrasoMs)
    return () => {
      clearTimeout(temporizador)
    }
  }, [valor, atrasoMs])

  return adiado
}
