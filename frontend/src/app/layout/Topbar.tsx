import { LogOut, Monitor, Moon, Sun, UserRound } from 'lucide-react'
import { useState } from 'react'

import { useTema } from '../use-tema'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuConteudo,
  DropdownMenuGatilho,
  DropdownMenuItem,
  DropdownMenuRotulo,
  DropdownMenuSeparador,
} from '@/components/ui/dropdown-menu'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { BuscaGlobal } from './BuscaGlobal'
import { SinoDeAlertas } from './SinoDeAlertas'
import { iniciaisDe } from '@/lib/formatters'

/**
 * Barra superior: busca global, central de alertas e menu do usuário.
 *
 * Busca e sino ficaram desabilitados durante a Fase 0, apontando para as fases que os
 * habilitariam. Com os cadastros e a central de pendências entregues, os dois passaram
 * a funcionar — um aviso que envelhece vira informação errada na tela.
 */
export function Topbar() {
  const { usuario, sair } = useAutenticacao()
  const { tema, definirTema } = useTema()
  const [saindo, definirSaindo] = useState(false)

  const encerrar = () => {
    definirSaindo(true)
    void sair().finally(() => {
      definirSaindo(false)
    })
  }

  return (
    <header className="flex h-14 shrink-0 items-center gap-3 border-b border-borda bg-superficie px-4">
      <BuscaGlobal />

      <div className="ml-auto flex items-center gap-1">
        <SinoDeAlertas />

        <SeletorDeTema tema={tema} aoDefinir={definirTema} />

        <DropdownMenu>
          <DropdownMenuGatilho asChild>
            <Button variante="sutil" className="gap-2 pl-1.5 pr-2.5" aria-label="Menu do usuário">
              <span className="grid size-7 shrink-0 place-items-center rounded-full bg-marca-suave text-xs font-semibold text-marca-forte">
                {iniciaisDe(usuario?.nome)}
              </span>
              <span className="hidden max-w-36 truncate text-sm font-medium sm:block">
                {usuario?.nome ?? '—'}
              </span>
            </Button>
          </DropdownMenuGatilho>
          <DropdownMenuConteudo align="end">
            <DropdownMenuRotulo>Sessão</DropdownMenuRotulo>
            <div className="px-2.5 pb-2">
              <p className="truncate text-sm font-medium text-texto">{usuario?.nome ?? '—'}</p>
              <p className="truncate text-xs text-texto-suave">{usuario?.email ?? '—'}</p>
              {usuario ? (
                <Badge variante="marca" className="mt-1.5">
                  <UserRound className="size-3" aria-hidden="true" />
                  {usuario.perfilDescricao}
                </Badge>
              ) : null}
            </div>
            <DropdownMenuSeparador />
            <DropdownMenuItem onSelect={encerrar} disabled={saindo}>
              <LogOut aria-hidden="true" />
              {saindo ? 'Saindo…' : 'Sair'}
            </DropdownMenuItem>
          </DropdownMenuConteudo>
        </DropdownMenu>
      </div>
    </header>
  )
}

function SeletorDeTema({
  tema,
  aoDefinir,
}: {
  tema: 'claro' | 'escuro' | 'sistema'
  aoDefinir: (tema: 'claro' | 'escuro' | 'sistema') => void
}) {
  return (
    <DropdownMenu>
      <DropdownMenuGatilho asChild>
        <Button variante="sutil" tamanho="icone" aria-label="Alternar tema de cores">
          <Sun className="hidden dark:block" aria-hidden="true" />
          <Moon className="block dark:hidden" aria-hidden="true" />
        </Button>
      </DropdownMenuGatilho>
      <DropdownMenuConteudo align="end">
        <DropdownMenuRotulo>Tema</DropdownMenuRotulo>
        <DropdownMenuItem
          onSelect={() => {
            aoDefinir('claro')
          }}
        >
          <Sun aria-hidden="true" />
          Claro
          {tema === 'claro' ? <span className="ml-auto text-xs text-marca">•</span> : null}
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={() => {
            aoDefinir('escuro')
          }}
        >
          <Moon aria-hidden="true" />
          Escuro
          {tema === 'escuro' ? <span className="ml-auto text-xs text-marca">•</span> : null}
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={() => {
            aoDefinir('sistema')
          }}
        >
          <Monitor aria-hidden="true" />
          Seguir o sistema
          {tema === 'sistema' ? <span className="ml-auto text-xs text-marca">•</span> : null}
        </DropdownMenuItem>
      </DropdownMenuConteudo>
    </DropdownMenu>
  )
}
