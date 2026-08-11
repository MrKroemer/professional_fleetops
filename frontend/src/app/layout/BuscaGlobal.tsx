import { useQuery } from '@tanstack/react-query'
import { Building2, Car, IdCard, Loader2, Search, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { Input } from '@/components/ui/input'
import { api, exigirSucesso } from '@/lib/api/client'
import type { components } from '@/lib/api/schema'
import { useDebounce } from '@/lib/use-debounce'
import { cn } from '@/lib/utils'

type Resultado = components['schemas']['ResultadoDaBuscaResponse']

/** Abaixo disso a busca não consulta: dois caracteres devolveriam a base inteira. */
const TERMO_MINIMO = 2

const ICONES = { VEICULO: Car, CONDUTOR: IdCard, OBRA: Building2 } as const
const GRUPOS = [
  { chave: 'veiculos', titulo: 'Veículos' },
  { chave: 'condutores', titulo: 'Condutores' },
  { chave: 'obras', titulo: 'Obras' },
] as const

/**
 * Busca global da barra superior (Seção 6.1).
 *
 * Procura por placa, condutor e obra — as três formas pelas quais o gestor se refere a
 * um veículo. Substitui o Ctrl+F que ele usa hoje na planilha, e por isso precisa levar
 * ao registro: uma busca que só diz "existe" obrigaria a procurar de novo no menu.
 *
 * A navegação por teclado é o caminho principal: `/` foca o campo de qualquer tela,
 * as setas percorrem os resultados, Enter abre e Esc fecha. Quem usa o sistema o dia
 * inteiro não tira a mão do teclado para clicar em uma lista de cinco itens.
 */
export function BuscaGlobal() {
  const navegar = useNavigate()
  const campo = useRef<HTMLInputElement>(null)
  const [termo, definirTermo] = useState('')
  const [aberta, definirAberta] = useState(false)
  const [selecionado, definirSelecionado] = useState(0)
  const termoAplicado = useDebounce(termo, 250)

  const consulta = useQuery({
    queryKey: ['busca-global', termoAplicado],
    enabled: termoAplicado.trim().length >= TERMO_MINIMO,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/painel/busca', { params: { query: { termo: termoAplicado } } }),
      ),
  })

  const achados: Resultado[] = consulta.data
    ? [...consulta.data.veiculos, ...consulta.data.condutores, ...consulta.data.obras]
    : []

  // Atalho global: `/` foca a busca, exceto quando já se está digitando em outro campo.
  useEffect(() => {
    const aoTeclar = (evento: KeyboardEvent) => {
      const alvo = evento.target as HTMLElement | null
      const digitando =
        alvo?.tagName === 'INPUT' || alvo?.tagName === 'TEXTAREA' || alvo?.isContentEditable
      if (evento.key === '/' && !digitando) {
        evento.preventDefault()
        campo.current?.focus()
      }
    }
    window.addEventListener('keydown', aoTeclar)
    return () => {
      window.removeEventListener('keydown', aoTeclar)
    }
  }, [])

  const abrir = (resultado: Resultado) => {
    definirTermo('')
    definirAberta(false)
    campo.current?.blur()
    void navegar(resultado.rota)
  }

  const aoTeclarNoCampo = (evento: React.KeyboardEvent<HTMLInputElement>) => {
    if (evento.key === 'Escape') {
      definirAberta(false)
      campo.current?.blur()
      return
    }
    if (achados.length === 0) {
      return
    }
    if (evento.key === 'ArrowDown') {
      evento.preventDefault()
      definirSelecionado((atual) => (atual + 1) % achados.length)
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault()
      definirSelecionado((atual) => (atual - 1 + achados.length) % achados.length)
    } else if (evento.key === 'Enter') {
      evento.preventDefault()
      const escolhido = achados[selecionado]
      if (escolhido) {
        abrir(escolhido)
      }
    }
  }

  const mostrarPainel = aberta && termo.trim().length >= TERMO_MINIMO
  let indiceGlobal = -1

  return (
    <div className="relative hidden w-full max-w-md md:block">
      <Search
        className="pointer-events-none absolute left-2.5 top-1/2 z-10 size-4 -translate-y-1/2 text-texto-tenue"
        aria-hidden="true"
      />
      <Input
        ref={campo}
        type="search"
        value={termo}
        onChange={(evento) => {
          definirTermo(evento.target.value)
          definirSelecionado(0)
          definirAberta(true)
        }}
        onFocus={() => {
          definirAberta(true)
        }}
        // O atraso deixa o clique em um resultado acontecer antes do fechamento.
        onBlur={() => {
          window.setTimeout(() => {
            definirAberta(false)
          }, 150)
        }}
        onKeyDown={aoTeclarNoCampo}
        placeholder="Buscar placa, condutor ou obra…"
        className="pl-8 pr-8"
        role="combobox"
        aria-expanded={mostrarPainel}
        aria-controls="resultados-da-busca"
        aria-label="Busca global por placa, condutor ou obra"
      />

      {consulta.isFetching ? (
        <Loader2
          className="absolute right-2.5 top-1/2 size-4 -translate-y-1/2 animate-spin text-texto-tenue"
          aria-hidden="true"
        />
      ) : termo ? (
        <button
          type="button"
          onClick={() => {
            definirTermo('')
            campo.current?.focus()
          }}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-texto-tenue hover:text-texto"
          aria-label="Limpar busca"
        >
          <X className="size-3.5" aria-hidden="true" />
        </button>
      ) : null}

      {mostrarPainel ? (
        <div
          id="resultados-da-busca"
          role="listbox"
          className="absolute left-0 right-0 top-full z-50 mt-1.5 max-h-96 overflow-y-auto rounded-[var(--radius-base)] border border-borda bg-superficie-elevada py-1 shadow-xl"
        >
          {consulta.isPending ? (
            <p className="px-3 py-4 text-center text-sm text-texto-tenue">Procurando…</p>
          ) : achados.length === 0 ? (
            <p className="px-3 py-4 text-center text-sm text-texto-tenue">
              Nada encontrado para “{termo}”.
            </p>
          ) : (
            GRUPOS.map((grupo) => {
              const itens = consulta.data?.[grupo.chave] ?? []
              if (itens.length === 0) {
                return null
              }
              return (
                <div key={grupo.chave} className="py-0.5">
                  <p className="px-3 py-1 text-[0.68rem] font-semibold uppercase tracking-wider text-texto-tenue">
                    {grupo.titulo}
                  </p>
                  {itens.map((resultado) => {
                    indiceGlobal += 1
                    const ehSelecionado = indiceGlobal === selecionado
                    const Icone = ICONES[resultado.tipo as keyof typeof ICONES] ?? Search
                    return (
                      <button
                        key={`${resultado.tipo}-${String(resultado.id)}`}
                        type="button"
                        role="option"
                        aria-selected={ehSelecionado}
                        onMouseDown={(evento) => {
                          // `mousedown` em vez de `click`: o blur fecharia o painel antes.
                          evento.preventDefault()
                          abrir(resultado)
                        }}
                        className={cn(
                          'flex w-full items-center gap-2.5 px-3 py-2 text-left transition-colors',
                          ehSelecionado ? 'bg-marca-suave' : 'hover:bg-fundo-alternativo',
                        )}
                      >
                        <Icone className="size-4 shrink-0 text-texto-tenue" aria-hidden="true" />
                        <span className="min-w-0 flex-1">
                          <span
                            className={cn(
                              'block truncate text-sm text-texto',
                              resultado.tipo === 'VEICULO' && 'font-mono font-medium',
                            )}
                          >
                            {resultado.rotulo}
                          </span>
                          {resultado.detalhe ? (
                            <span className="block truncate text-xs text-texto-suave">
                              {resultado.detalhe}
                            </span>
                          ) : null}
                        </span>
                      </button>
                    )
                  })}
                </div>
              )
            })
          )}
          <p className="border-t border-borda px-3 pb-1 pt-2 text-[0.68rem] text-texto-tenue">
            <kbd className="rounded border border-borda px-1">↑</kbd>{' '}
            <kbd className="rounded border border-borda px-1">↓</kbd> navegar ·{' '}
            <kbd className="rounded border border-borda px-1">Enter</kbd> abrir ·{' '}
            <kbd className="rounded border border-borda px-1">Esc</kbd> fechar
          </p>
        </div>
      ) : null}
    </div>
  )
}
