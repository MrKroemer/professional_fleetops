import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import type { Perfil, Usuario } from './tipos'
import { api, definirAccessToken, exigirSucesso, registrarPerdaDeSessao } from '@/lib/api/client'

/**
 * Estado da sessão do usuário.
 *
 * Ao iniciar, o app tenta uma renovação silenciosa: como o access token vive só em
 * memória, um F5 o descarta, mas o cookie `HttpOnly` de refresh sobrevive e permite
 * reconstruir a sessão sem novo login. Enquanto essa tentativa está em curso, o
 * estado é `verificando` — e nenhuma rota decide nada, para não expulsar para o
 * login alguém que na verdade continua autenticado.
 */

export type EstadoDaSessao = 'verificando' | 'autenticado' | 'anonimo'

interface ContextoDeAutenticacao {
  estado: EstadoDaSessao
  usuario: Usuario | null
  entrar: (email: string, senha: string) => Promise<void>
  sair: () => Promise<void>
  temPerfil: (...perfis: Perfil[]) => boolean
}

// eslint-disable-next-line react-refresh/only-export-components
export const ContextoDeAutenticacao = createContext<ContextoDeAutenticacao | null>(null)

export function ProvedorDeAutenticacao({ children }: { children: ReactNode }) {
  const [estado, definirEstado] = useState<EstadoDaSessao>('verificando')
  const [usuario, definirUsuario] = useState<Usuario | null>(null)
  const clienteDeConsultas = useQueryClient()

  const limparSessao = useCallback(() => {
    definirAccessToken(null)
    definirUsuario(null)
    definirEstado('anonimo')
    clienteDeConsultas.clear()
  }, [clienteDeConsultas])

  // Renovação silenciosa na inicialização.
  useEffect(() => {
    let cancelado = false

    void (async () => {
      try {
        const sessao = exigirSucesso(await api.POST('/api/v1/auth/refresh'))
        if (cancelado) return
        definirAccessToken(sessao.accessToken)
        definirUsuario(sessao.usuario)
        definirEstado('autenticado')
      } catch {
        if (cancelado) return
        // Ausência de sessão é o caso normal do primeiro acesso, não um erro.
        definirAccessToken(null)
        definirUsuario(null)
        definirEstado('anonimo')
      }
    })()

    return () => {
      cancelado = true
    }
  }, [])

  // Uma renovação que falha no meio da navegação derruba a sessão na interface.
  useEffect(() => {
    registrarPerdaDeSessao(limparSessao)
    return () => {
      registrarPerdaDeSessao(null)
    }
  }, [limparSessao])

  const entrar = useCallback(
    async (email: string, senha: string) => {
      const sessao = exigirSucesso(
        await api.POST('/api/v1/auth/login', { body: { email, senha } }),
      )
      definirAccessToken(sessao.accessToken)
      definirUsuario(sessao.usuario)
      definirEstado('autenticado')
    },
    [],
  )

  const sair = useCallback(async () => {
    try {
      await api.POST('/api/v1/auth/logout')
    } finally {
      // A sessão local é encerrada mesmo que a chamada falhe: manter o usuário
      // "logado" na interface após ele pedir para sair seria pior.
      limparSessao()
    }
  }, [limparSessao])

  const temPerfil = useCallback(
    (...perfis: Perfil[]) => (usuario ? perfis.includes(usuario.perfil) : false),
    [usuario],
  )

  const valor = useMemo(
    () => ({ estado, usuario, entrar, sair, temPerfil }),
    [estado, usuario, entrar, sair, temPerfil],
  )

  return <ContextoDeAutenticacao.Provider value={valor}>{children}</ContextoDeAutenticacao.Provider>
}
