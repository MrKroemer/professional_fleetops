import createClient from 'openapi-fetch'

import type { paths } from './schema'
import { comoProblemDetail, ErroDaApi } from './problem'

/**
 * Cliente HTTP tipado da API, gerado a partir da OpenAPI do backend.
 *
 * Três responsabilidades concentradas aqui:
 *
 * 1. **Token em memória.** O access token vive apenas nesta variável de módulo —
 *    nunca em `localStorage` nem em `sessionStorage`. Um XSS não consegue lê-lo,
 *    e o refresh token sequer é acessível a JavaScript (cookie `HttpOnly`).
 *    O preço é que recarregar a página perde o token; a sessão é reconstruída
 *    chamando `/auth/refresh` na inicialização do app.
 *
 * 2. **Renovação transparente.** Um 401 dispara uma renovação e uma única
 *    repetição da requisição. Renovações concorrentes compartilham a mesma
 *    promessa, para que dez chamadas simultâneas não gerem dez rotações de token.
 *
 * 3. **Erros normalizados.** Toda falha vira `ErroDaApi`, com o `codigo` estável
 *    do backend — a interface nunca interpreta texto de mensagem.
 */

/** Prefixo vazio: o frontend e a API compartilham a origem (proxy do Vite ou Nginx). */
const BASE_URL = ''

const ROTAS_DE_AUTENTICACAO = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout']

let accessToken: string | null = null
let renovacaoEmAndamento: Promise<string | null> | null = null
let aoPerderSessao: (() => void) | null = null

/** Define o token de acesso corrente. `null` limpa a sessão em memória. */
export function definirAccessToken(token: string | null): void {
  accessToken = token
}

/** Token de acesso corrente, se houver. */
export function obterAccessToken(): string | null {
  return accessToken
}

/**
 * Registra o que fazer quando a sessão se perde de forma irrecuperável — na
 * prática, levar o usuário de volta à tela de login.
 */
export function registrarPerdaDeSessao(callback: (() => void) | null): void {
  aoPerderSessao = callback
}

function ehRotaDeAutenticacao(url: string): boolean {
  return ROTAS_DE_AUTENTICACAO.some((rota) => url.includes(rota))
}

interface RespostaDeRenovacao {
  accessToken?: string
}

/**
 * Renova a sessão usando o cookie `HttpOnly`. Chamadas concorrentes aguardam a
 * mesma renovação em vez de dispararem rotações competindo entre si.
 */
async function renovarSessao(): Promise<string | null> {
  renovacaoEmAndamento ??= (async (): Promise<string | null> => {
    try {
      const resposta = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      })
      if (!resposta.ok) return null
      const corpo = (await resposta.json()) as RespostaDeRenovacao
      const novoToken = corpo.accessToken ?? null
      accessToken = novoToken
      return novoToken
    } catch {
      return null
    } finally {
      renovacaoEmAndamento = null
    }
  })()
  return renovacaoEmAndamento
}

/**
 * `fetch` da aplicação: injeta o token, renova quando expira e repete a
 * requisição uma única vez.
 */
async function fetchAutenticado(requisicao: Request): Promise<Response> {
  const paraRepetir = requisicao.clone()

  const primeira = await fetch(comToken(requisicao, accessToken))
  if (primeira.status !== 401 || ehRotaDeAutenticacao(requisicao.url)) {
    return primeira
  }

  const novoToken = await renovarSessao()
  if (!novoToken) {
    aoPerderSessao?.()
    return primeira
  }
  return fetch(comToken(paraRepetir, novoToken))
}

function comToken(requisicao: Request, token: string | null): Request {
  const cabecalhos = new Headers(requisicao.headers)
  if (token) {
    cabecalhos.set('Authorization', `Bearer ${token}`)
  } else {
    cabecalhos.delete('Authorization')
  }
  return new Request(requisicao, { headers: cabecalhos, credentials: 'same-origin' })
}

/** Cliente tipado. Os tipos vêm de `schema.d.ts`, gerado da OpenAPI. */
export const api = createClient<paths>({
  baseUrl: BASE_URL,
  credentials: 'same-origin',
  fetch: fetchAutenticado,
})

/**
 * Converte o par `{ data, error }` do openapi-fetch em um valor ou uma exceção.
 *
 * Existe para que as telas usem `try/catch` e o TanStack Query enxergue os erros —
 * checar `error` manualmente em cada chamada seria repetitivo e fácil de esquecer.
 */
export function exigirSucesso<T>(
  resultado: { data?: T; error?: unknown; response: Response },
): T {
  if (resultado.error !== undefined || !resultado.response.ok) {
    throw new ErroDaApi(resultado.response.status, comoProblemDetail(resultado.error))
  }
  if (resultado.data === undefined) {
    throw new ErroDaApi(resultado.response.status, null)
  }
  return resultado.data
}
