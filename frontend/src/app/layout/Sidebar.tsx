import { ChevronLeft } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { estaDisponivel, navegacaoPara, type ItemDeNavegacao } from './navegacao'
import { Button } from '@/components/ui/button'
import { Marca } from '@/components/ui/marca'
import { Tooltip } from '@/components/ui/tooltip'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { cn } from '@/lib/utils'

interface SidebarProps {
  colapsada: boolean
  aoAlternar: () => void
}

/** Navegação lateral, agrupada por área e colapsável para ganhar largura útil em tabelas densas. */
export function Sidebar({ colapsada, aoAlternar }: SidebarProps) {
  const { usuario } = useAutenticacao()
  const grupos = navegacaoPara(usuario?.perfil)

  return (
    <nav
      aria-label="Navegação principal"
      className={cn(
        'flex h-full flex-col border-r border-borda bg-superficie transition-[width] duration-200',
        colapsada ? 'w-16' : 'w-64',
      )}
    >
      {/*
        Só a marca, sem o nome escrito ao lado. Com a barra recolhida ela fica no ícone
        de 32px; expandida, cresce e se centraliza, ocupando a faixa que o texto deixou
        em vez de flutuar encostada à esquerda.
      */}
      <div className="flex h-14 shrink-0 items-center justify-center border-b border-borda px-2">
        <Marca
          tamanho={colapsada ? 'compacto' : 'grande'}
          className={cn('w-auto shrink-0 transition-[height] duration-200', colapsada ? 'h-8' : 'h-10')}
        />
      </div>

      <div className="flex-1 overflow-y-auto px-2 py-3">
        {grupos.map((grupo) => (
          <div key={grupo.titulo} className="mb-4 last:mb-0">
            {!colapsada ? (
              <p className="px-2.5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-texto-tenue">
                {grupo.titulo}
              </p>
            ) : (
              <div className="mx-2 mb-2 border-t border-borda first:border-0" aria-hidden="true" />
            )}
            <ul className="space-y-0.5">
              {grupo.itens.map((item) => (
                <li key={item.para}>
                  <ItemDaSidebar item={item} colapsada={colapsada} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="border-t border-borda p-2">
        <Button
          variante="sutil"
          tamanho={colapsada ? 'icone' : 'pequeno'}
          onClick={aoAlternar}
          className={cn('w-full', colapsada && 'justify-center')}
          aria-label={colapsada ? 'Expandir menu lateral' : 'Recolher menu lateral'}
        >
          <ChevronLeft
            className={cn('transition-transform duration-200', colapsada && 'rotate-180')}
            aria-hidden="true"
          />
          {!colapsada ? <span>Recolher</span> : null}
        </Button>
      </div>
    </nav>
  )
}

function ItemDaSidebar({ item, colapsada }: { item: ItemDeNavegacao; colapsada: boolean }) {
  const Icone = item.icone
  const disponivel = estaDisponivel(item)

  const classesBase = cn(
    'flex items-center gap-2.5 rounded-[var(--radius-base)] px-2.5 py-2 text-sm font-medium transition-colors',
    colapsada && 'justify-center px-0',
  )

  if (!disponivel) {
    const conteudo = (
      <span
        className={cn(classesBase, 'cursor-not-allowed text-texto-tenue')}
        aria-disabled="true"
      >
        <Icone className="size-4 shrink-0" aria-hidden="true" />
        {!colapsada ? (
          <>
            <span className="flex-1 truncate">{item.rotulo}</span>
            <span className="rounded bg-fundo-alternativo px-1.5 py-0.5 text-[10px] font-semibold text-texto-tenue">
              Fase {item.fase}
            </span>
          </>
        ) : null}
      </span>
    )
    return (
      <Tooltip conteudo={`${item.rotulo} — previsto para a Fase ${String(item.fase)}`}>
        {conteudo}
      </Tooltip>
    )
  }

  const link = (
    <NavLink
      to={item.para}
      end={item.para === '/'}
      className={({ isActive }) =>
        cn(
          classesBase,
          isActive
            ? 'bg-marca-suave text-marca-forte'
            : 'text-texto-suave hover:bg-fundo-alternativo hover:text-texto',
        )
      }
    >
      <Icone className="size-4 shrink-0" aria-hidden="true" />
      {!colapsada ? <span className="truncate">{item.rotulo}</span> : null}
      {colapsada ? <span className="sr-only">{item.rotulo}</span> : null}
    </NavLink>
  )

  return colapsada ? <Tooltip conteudo={item.rotulo}>{link}</Tooltip> : link
}
