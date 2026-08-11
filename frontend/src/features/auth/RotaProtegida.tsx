import { Loader2 } from 'lucide-react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import type { Perfil } from './tipos'
import { useAutenticacao } from './use-autenticacao'
import { EstadoDeErro } from '@/components/ui/estados'
import { ErroDaApi } from '@/lib/api/problem'

interface RotaProtegidaProps {
  /** Perfis autorizados. Ausente significa "qualquer usuário autenticado". */
  perfis?: Perfil[]
}

/**
 * Barreira de acesso das rotas (RN-19).
 *
 * É apenas conveniência de navegação: a autorização real é do backend, que rejeita
 * qualquer chamada não permitida com 403 independentemente do que o roteador faça.
 * Esconder a rota evita levar o usuário a uma tela que ele veria vazia.
 */
export function RotaProtegida({ perfis }: RotaProtegidaProps) {
  const { estado, usuario } = useAutenticacao()
  const localizacao = useLocation()

  if (estado === 'verificando') {
    return (
      <div
        className="flex min-h-full items-center justify-center py-24"
        role="status"
        aria-live="polite"
      >
        <Loader2 className="size-6 animate-spin text-texto-tenue" aria-hidden="true" />
        <span className="sr-only">Verificando sua sessão…</span>
      </div>
    )
  }

  if (estado === 'anonimo' || !usuario) {
    return <Navigate to="/entrar" replace state={{ de: localizacao.pathname }} />
  }

  if (perfis && !perfis.includes(usuario.perfil)) {
    return (
      <div className="p-6">
        <EstadoDeErro erro={new ErroDaApi(403, { detail: 'Seu perfil não permite acessar esta área.' })} />
      </div>
    )
  }

  return <Outlet />
}
