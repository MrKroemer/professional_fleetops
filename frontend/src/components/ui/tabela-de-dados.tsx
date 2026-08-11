import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
  type VisibilityState,
} from '@tanstack/react-table'
import {
  ArrowDown,
  ArrowUp,
  ChevronLeft,
  ChevronRight,
  ChevronsUpDown,
  Columns3,
  Download,
  Rows2,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { Button } from './button'
import { Card } from './card'
import {
  DropdownMenu,
  DropdownMenuConteudo,
  DropdownMenuGatilho,
  DropdownMenuItem,
  DropdownMenuRotulo,
  DropdownMenuSeparador,
} from './dropdown-menu'
import { Select } from './select'
import { SkeletonDeTabela } from './skeleton'
import { EstadoDeErro } from './estados'
import { baixarCsv } from '@/lib/exportacao'
import { cn } from '@/lib/utils'

/** Espaçamento vertical das linhas, ajustável pelo usuário. */
export type Densidade = 'compacta' | 'normal' | 'confortavel'

const ALTURA_POR_DENSIDADE: Record<Densidade, string> = {
  compacta: 'px-3 py-1.5',
  normal: 'px-4 py-2.5',
  confortavel: 'px-4 py-4',
}

const TAMANHOS_DE_PAGINA = [20, 50, 100]

export interface EstadoDePaginacao {
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

interface TabelaDeDadosProps<T> {
  /** Descrição da tabela para leitores de tela. Obrigatória. */
  legenda: string
  colunas: ColumnDef<T, unknown>[]
  dados: T[]
  paginacao: EstadoDePaginacao
  aoMudarPagina: (pagina: number) => void
  aoMudarTamanho: (tamanho: number) => void
  /** Ordenação atual, no formato do TanStack Table. */
  ordenacao: SortingState
  aoMudarOrdenacao: (ordenacao: SortingState) => void
  carregando?: boolean
  erro?: unknown
  aoTentarNovamente?: () => void
  /** Conteúdo exibido quando não há linhas — tipicamente um `EstadoVazio`. */
  vazio?: ReactNode
  /**
   * Busca todas as linhas da visão filtrada para exportação.
   *
   * Fica a cargo da página porque só ela sabe quais filtros estão aplicados. Sem
   * isso, o botão exportaria apenas a página visível — o que quase nunca é o que
   * o usuário quer ao pedir "exportar".
   */
  aoExportar?: () => Promise<T[]>
  nomeDoArquivo?: string
  /**
   * Abre o detalhe da linha.
   *
   * Quando informado, a linha inteira vira alvo de clique e de teclado — um alvo
   * grande é mais fácil de acertar que um ícone de 16px, e é o gesto que o usuário
   * já espera de uma listagem.
   */
  aoSelecionarLinha?: (linha: T) => void
}

/**
 * Tabela de dados do sistema.
 *
 * Ordenação, paginação e filtros são **do servidor**: com 270 veículos e um cadastro
 * que só cresce, ordenar no navegador daria a resposta errada — ordenaria a página
 * visível, não o conjunto. Por isso o componente não usa os modelos de ordenação e
 * paginação do TanStack Table; ele emite o estado e a página faz a consulta.
 *
 * Densidade e colunas visíveis, ao contrário, são preferências de exibição e vivem no
 * cliente. A exportação leva a visão filtrada inteira, não a página.
 */
export function TabelaDeDados<T>({
  legenda,
  colunas,
  dados,
  paginacao,
  aoMudarPagina,
  aoMudarTamanho,
  ordenacao,
  aoMudarOrdenacao,
  carregando,
  erro,
  aoTentarNovamente,
  vazio,
  aoExportar,
  nomeDoArquivo = 'exportacao',
  aoSelecionarLinha,
}: TabelaDeDadosProps<T>) {
  const [densidade, definirDensidade] = useState<Densidade>('normal')
  const [colunasVisiveis, definirColunasVisiveis] = useState<VisibilityState>({})
  const [exportando, definirExportando] = useState(false)

  /*
   * O React Compiler não consegue memoizar o objeto devolvido por `useReactTable`,
   * que expõe métodos recriados a cada render. Diferente do `watch` do
   * react-hook-form — que tem `useWatch` como alternativa —, a biblioteca não
   * oferece uma API memoizável, então o componente fica fora da otimização. Não há
   * risco de interface desatualizada: todo o estado que importa (ordenação,
   * paginação, filtros) mora fora da tabela.
   */
  // eslint-disable-next-line react-hooks/incompatible-library
  const tabela = useReactTable({
    data: dados,
    columns: colunas,
    state: { sorting: ordenacao, columnVisibility: colunasVisiveis },
    onSortingChange: (atualizador) => {
      aoMudarOrdenacao(typeof atualizador === 'function' ? atualizador(ordenacao) : atualizador)
    },
    onColumnVisibilityChange: definirColunasVisiveis,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    manualPagination: true,
    pageCount: paginacao.totalPaginas,
  })

  const colunasOcultaveis = tabela.getAllLeafColumns().filter((coluna) => coluna.getCanHide())

  const exportar = () => {
    if (!aoExportar) {
      return
    }
    definirExportando(true)
    void aoExportar()
      .then((linhas) => {
        const visiveis = tabela.getVisibleLeafColumns().filter((coluna) => coluna.id !== 'acoes')
        baixarCsv({
          nomeDoArquivo,
          cabecalhos: visiveis.map((coluna) => rotuloDaColuna(coluna.columnDef)),
          linhas: linhas.map((linha) =>
            visiveis.map((coluna) => valorParaTexto(linha, coluna.id, coluna.columnDef)),
          ),
        })
      })
      .finally(() => {
        definirExportando(false)
      })
  }

  const primeiroDaPagina = paginacao.pagina * paginacao.tamanho + 1
  const ultimoDaPagina = Math.min(primeiroDaPagina + dados.length - 1, paginacao.totalElementos)

  if (carregando) {
    return <SkeletonDeTabela linhas={6} colunas={Math.min(colunas.length, 6)} />
  }

  if (erro) {
    return <EstadoDeErro erro={erro} aoTentarNovamente={aoTentarNovamente} />
  }

  return (
    <div className="space-y-3">
      {/* Barra de ferramentas: preferências de exibição e exportação. */}
      <div className="flex flex-wrap items-center justify-end gap-2">
        <DropdownMenu>
          <DropdownMenuGatilho asChild>
            <Button variante="secundaria" tamanho="pequeno">
              <Rows2 aria-hidden="true" />
              Densidade
            </Button>
          </DropdownMenuGatilho>
          <DropdownMenuConteudo align="end">
            <DropdownMenuRotulo>Espaçamento das linhas</DropdownMenuRotulo>
            <DropdownMenuSeparador />
            {(['compacta', 'normal', 'confortavel'] as const).map((opcao) => (
              <DropdownMenuItem
                key={opcao}
                onSelect={() => {
                  definirDensidade(opcao)
                }}
                className={cn(densidade === opcao && 'font-semibold text-marca-forte')}
              >
                {opcao === 'compacta' ? 'Compacta' : opcao === 'normal' ? 'Normal' : 'Confortável'}
              </DropdownMenuItem>
            ))}
          </DropdownMenuConteudo>
        </DropdownMenu>

        {colunasOcultaveis.length > 0 ? (
          <DropdownMenu>
            <DropdownMenuGatilho asChild>
              <Button variante="secundaria" tamanho="pequeno">
                <Columns3 aria-hidden="true" />
                Colunas
              </Button>
            </DropdownMenuGatilho>
            <DropdownMenuConteudo align="end" className="max-h-80 overflow-y-auto">
              <DropdownMenuRotulo>Colunas exibidas</DropdownMenuRotulo>
              <DropdownMenuSeparador />
              {colunasOcultaveis.map((coluna) => (
                <DropdownMenuItem
                  key={coluna.id}
                  onSelect={(evento) => {
                    // O menu fica aberto: marcar várias colunas é o uso comum.
                    evento.preventDefault()
                    coluna.toggleVisibility()
                  }}
                >
                  <input
                    type="checkbox"
                    readOnly
                    checked={coluna.getIsVisible()}
                    className="size-3.5 accent-[var(--marca)]"
                    tabIndex={-1}
                  />
                  {rotuloDaColuna(coluna.columnDef)}
                </DropdownMenuItem>
              ))}
            </DropdownMenuConteudo>
          </DropdownMenu>
        ) : null}

        {aoExportar ? (
          <Button variante="secundaria" tamanho="pequeno" onClick={exportar} carregando={exportando}>
            <Download aria-hidden="true" />
            {exportando ? 'Preparando…' : 'Exportar CSV'}
          </Button>
        ) : null}
      </div>

      {dados.length === 0 ? (
        vazio
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <caption className="sr-only">{legenda}</caption>
              <thead className="border-b border-borda bg-fundo-alternativo/60">
                {tabela.getHeaderGroups().map((grupo) => (
                  <tr key={grupo.id}>
                    {grupo.headers.map((cabecalho) => {
                      const ordenavel = cabecalho.column.getCanSort()
                      const direcao = cabecalho.column.getIsSorted()
                      const numerica = Boolean(cabecalho.column.columnDef.meta?.numerica)
                      return (
                        <th
                          key={cabecalho.id}
                          scope="col"
                          aria-sort={
                            direcao === 'asc'
                              ? 'ascending'
                              : direcao === 'desc'
                                ? 'descending'
                                : ordenavel
                                  ? 'none'
                                  : undefined
                          }
                          className={cn(
                            'whitespace-nowrap font-medium text-texto-suave',
                            ALTURA_POR_DENSIDADE[densidade],
                            numerica ? 'text-right' : 'text-left',
                          )}
                        >
                          {cabecalho.isPlaceholder ? null : ordenavel ? (
                            <button
                              type="button"
                              onClick={cabecalho.column.getToggleSortingHandler()}
                              className={cn(
                                'inline-flex items-center gap-1 rounded transition-colors hover:text-texto',
                                numerica && 'flex-row-reverse',
                              )}
                            >
                              {flexRender(cabecalho.column.columnDef.header, cabecalho.getContext())}
                              {direcao === 'asc' ? (
                                <ArrowUp className="size-3.5 text-marca-forte" aria-hidden="true" />
                              ) : direcao === 'desc' ? (
                                <ArrowDown className="size-3.5 text-marca-forte" aria-hidden="true" />
                              ) : (
                                <ChevronsUpDown className="size-3.5 opacity-40" aria-hidden="true" />
                              )}
                            </button>
                          ) : (
                            flexRender(cabecalho.column.columnDef.header, cabecalho.getContext())
                          )}
                        </th>
                      )
                    })}
                  </tr>
                ))}
              </thead>
              <tbody>
                {tabela.getRowModel().rows.map((linha, indice) => (
                  <tr
                    key={linha.id}
                    style={{ '--indice': Math.min(indice, 12) } as React.CSSProperties}
                    tabIndex={aoSelecionarLinha ? 0 : undefined}
                    onClick={aoSelecionarLinha ? () => { aoSelecionarLinha(linha.original) } : undefined}
                    onKeyDown={
                      aoSelecionarLinha
                        ? (evento) => {
                            if (evento.key === 'Enter' || evento.key === ' ') {
                              evento.preventDefault()
                              aoSelecionarLinha(linha.original)
                            }
                          }
                        : undefined
                    }
                    className={cn(
                      'surgir border-b border-borda transition-colors last:border-0 hover:bg-fundo-alternativo/50',
                      aoSelecionarLinha &&
                        'cursor-pointer focus-visible:bg-fundo-alternativo focus-visible:outline-none',
                    )}
                  >
                    {linha.getVisibleCells().map((celula) => (
                      <td
                        key={celula.id}
                        className={cn(
                          'align-middle',
                          ALTURA_POR_DENSIDADE[densidade],
                          celula.column.columnDef.meta?.numerica && 'text-right tabular-nums',
                        )}
                      >
                        {flexRender(celula.column.columnDef.cell, celula.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-borda px-4 py-2.5">
            <p className="text-xs text-texto-tenue">
              {primeiroDaPagina}–{ultimoDaPagina} de {paginacao.totalElementos.toLocaleString('pt-BR')}
            </p>

            <div className="flex items-center gap-2">
              <label className="flex items-center gap-1.5 text-xs text-texto-tenue">
                Por página
                <Select
                  value={String(paginacao.tamanho)}
                  onChange={(evento) => {
                    aoMudarTamanho(Number(evento.target.value))
                  }}
                  className="h-7 w-[4.5rem] text-xs"
                  aria-label="Registros por página"
                >
                  {TAMANHOS_DE_PAGINA.map((tamanho) => (
                    <option key={tamanho} value={tamanho}>
                      {tamanho}
                    </option>
                  ))}
                </Select>
              </label>

              <div className="flex items-center gap-1">
                <Button
                  variante="sutil"
                  tamanho="icone"
                  aria-label="Página anterior"
                  disabled={paginacao.pagina === 0}
                  onClick={() => {
                    aoMudarPagina(paginacao.pagina - 1)
                  }}
                >
                  <ChevronLeft aria-hidden="true" />
                </Button>
                <span className="min-w-16 text-center text-xs tabular-nums text-texto-suave">
                  {paginacao.pagina + 1} / {Math.max(paginacao.totalPaginas, 1)}
                </span>
                <Button
                  variante="sutil"
                  tamanho="icone"
                  aria-label="Próxima página"
                  disabled={paginacao.pagina + 1 >= paginacao.totalPaginas}
                  onClick={() => {
                    aoMudarPagina(paginacao.pagina + 1)
                  }}
                >
                  <ChevronRight aria-hidden="true" />
                </Button>
              </div>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}

/** Rótulo textual de uma coluna, para o menu de colunas e o cabeçalho do CSV. */
function rotuloDaColuna<T>(definicao: ColumnDef<T, unknown>): string {
  return definicao.meta?.rotulo ?? (typeof definicao.header === 'string' ? definicao.header : '')
}

/**
 * Valor de uma célula em texto puro, para o CSV.
 *
 * Usa `exportar` do `meta` quando existe: a célula renderizada pode conter selos e
 * ícones, que não têm equivalente em uma planilha.
 */
function valorParaTexto<T>(linha: T, id: string, definicao: ColumnDef<T, unknown>): string {
  if (definicao.meta?.exportar) {
    return definicao.meta.exportar(linha)
  }
  const valor: unknown = (linha as Record<string, unknown>)[id]
  if (valor == null) {
    return ''
  }
  if (typeof valor === 'boolean') {
    return valor ? 'Sim' : 'Não'
  }
  if (typeof valor === 'string' || typeof valor === 'number') {
    return String(valor)
  }
  // Valores compostos não têm representação óbvia em planilha: a coluna precisa
  // declarar `meta.exportar` para dizer como o dado vira texto.
  return ''
}
