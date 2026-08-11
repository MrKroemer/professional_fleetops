import type { ReactNode } from 'react'

/**
 * Cabeçalho padrão das telas de cadastro: título, explicação e ação principal.
 *
 * A descrição não é enfeite — em um sistema que substitui vinte planilhas, dizer em uma
 * frase o que cada tela controla evita que o usuário procure a informação na tela errada.
 */
export function CabecalhoDaPagina({
  titulo,
  descricao,
  acao,
}: {
  titulo: string
  descricao: string
  acao?: ReactNode
}) {
  return (
    <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight text-texto">{titulo}</h1>
        <p className="mt-1.5 max-w-3xl text-sm text-texto-suave">{descricao}</p>
      </div>
      {acao}
    </header>
  )
}
