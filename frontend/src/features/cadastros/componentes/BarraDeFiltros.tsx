import { Search } from 'lucide-react'
import type { ReactNode } from 'react'

import { Input } from '@/components/ui/input'

/**
 * Busca e filtros das listagens.
 *
 * A busca é sempre o primeiro elemento e ocupa a maior largura: nas planilhas atuais o
 * gestor localiza um registro com Ctrl+F, e a tela precisa oferecer um caminho ao menos
 * tão direto quanto esse.
 */
export function BarraDeFiltros({
  termo,
  aoMudarTermo,
  placeholder,
  children,
}: {
  termo: string
  aoMudarTermo: (valor: string) => void
  placeholder: string
  children?: ReactNode
}) {
  return (
    <div className="mb-4 flex flex-wrap items-center gap-2">
      <div className="relative min-w-64 flex-1">
        <Search
          className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-texto-tenue"
          aria-hidden="true"
        />
        <Input
          type="search"
          value={termo}
          onChange={(evento) => {
            aoMudarTermo(evento.target.value)
          }}
          placeholder={placeholder}
          className="pl-8"
          aria-label={placeholder}
        />
      </div>
      {children}
    </div>
  )
}
