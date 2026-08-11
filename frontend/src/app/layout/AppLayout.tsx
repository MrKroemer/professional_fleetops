import { useState } from 'react'
import { Outlet } from 'react-router-dom'

import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

const CHAVE_SIDEBAR = 'fleetops.sidebar-colapsada'

function lerPreferenciaDaSidebar(): boolean {
  try {
    return localStorage.getItem(CHAVE_SIDEBAR) === 'sim'
  } catch {
    return false
  }
}

/** Casca da aplicação autenticada: navegação lateral, barra superior e conteúdo. */
export function AppLayout() {
  const [colapsada, definirColapsada] = useState(lerPreferenciaDaSidebar)

  const alternar = () => {
    definirColapsada((anterior) => {
      const proxima = !anterior
      try {
        localStorage.setItem(CHAVE_SIDEBAR, proxima ? 'sim' : 'nao')
      } catch {
        /* Sem persistência: a preferência vale apenas para esta sessão. */
      }
      return proxima
    })
  }

  return (
    <div className="flex h-full bg-fundo">
      <a href="#conteudo" className="pular-para-conteudo">
        Pular para o conteúdo
      </a>

      {/* A navegação lateral fica oculta no mobile; o acesso às áreas se dá pelas
          telas de índice, evitando um menu sobreposto pouco usável em tabelas densas. */}
      <div className="hidden md:block">
        <Sidebar colapsada={colapsada} aoAlternar={alternar} />
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <main id="conteudo" className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
