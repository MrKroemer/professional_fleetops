import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Pencil, Plus, Table2, Trash2 } from 'lucide-react'
import { useState } from 'react'

import { FormularioDeTabelaDePreco } from './FormularioDeTabelaDePreco'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { EstadoDeErro, EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Tabela,
  TabelaCabecalho,
  TabelaCelula,
  TabelaColuna,
  TabelaCorpo,
  TabelaLinha,
} from '@/components/ui/tabela'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { DialogoDeExclusao } from '@/features/cadastros/componentes/DialogoDeExclusao'
import type { TabelaPreco } from '@/features/cadastros/tipos'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarMoeda, formatarNumero } from '@/lib/formatters'
import { cn } from '@/lib/utils'

/**
 * Tabelas de preço de locação (RN-14).
 *
 * Cada vigência é exibida como um cartão expansível com a grade completa de grupos e
 * pacotes — a mesma leitura da planilha que o gestor recebe da locadora, mas montada
 * a partir de dados normalizados.
 */
export function TabelasDePrecoPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [locadoraId, definirLocadoraId] = useState('')
  const [ano, definirAno] = useState('')
  const [criando, definirCriando] = useState(false)
  const [emEdicao, definirEmEdicao] = useState<TabelaPreco | null>(null)
  const [emExclusao, definirEmExclusao] = useState<TabelaPreco | null>(null)
  const [expandidas, definirExpandidas] = useState<number[]>([])

  const locadoras = useQuery({
    queryKey: ['locadoras', 'selecao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/locadoras', {
          params: { query: { page: 0, size: 200, sort: ['nome,asc'] } },
        }),
      ),
  })

  const consulta = useQuery({
    queryKey: ['tabelas-preco', { locadoraId, ano }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/tabelas-preco', {
          params: {
            query: {
              ...(locadoraId ? { locadoraId: Number(locadoraId) } : {}),
              ...(ano ? { ano: Number(ano) } : {}),
              page: 0,
              size: 100,
              sort: ['anoVigencia,desc'],
            },
          },
        }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(await api.DELETE('/api/v1/tabelas-preco/{id}', { params: { path: { id } } })),
    onSuccess: () => clienteDeConsultas.invalidateQueries({ queryKey: ['tabelas-preco'] }),
  })

  const tabelas = consulta.data?.conteudo ?? []
  const filtrando = locadoraId !== '' || ano !== ''

  const alternarExpansao = (id: number) => {
    definirExpandidas((atuais) =>
      atuais.includes(id) ? atuais.filter((valor) => valor !== id) : [...atuais, id],
    )
  }

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Tabelas de preço"
        descricao="Uma vigência por locadora e ano. Os lançamentos consultam a vigência da competência — reprocessar um fechamento antigo reproduz os preços da época, não os atuais."
        acao={
          podeEditar ? (
            <Button
              onClick={() => {
                definirCriando(true)
              }}
            >
              <Plus aria-hidden="true" />
              Nova vigência
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Select
          value={locadoraId}
          onChange={(evento) => {
            definirLocadoraId(evento.target.value)
          }}
          className="w-64"
          aria-label="Filtrar por locadora"
        >
          <option value="">Todas as locadoras</option>
          {(locadoras.data?.conteudo ?? []).map((locadora) => (
            <option key={locadora.id} value={locadora.id}>
              {locadora.nome}
            </option>
          ))}
        </Select>
        <Select
          value={ano}
          onChange={(evento) => {
            definirAno(evento.target.value)
          }}
          className="w-40"
          aria-label="Filtrar por ano de vigência"
        >
          <option value="">Todos os anos</option>
          {anosDisponiveis(tabelas).map((valor) => (
            <option key={valor} value={valor}>
              {valor}
            </option>
          ))}
        </Select>
      </div>

      {consulta.isPending ? (
        <div className="space-y-3" role="status" aria-live="polite" aria-label="Carregando vigências">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <span className="sr-only">Carregando vigências…</span>
        </div>
      ) : consulta.isError ? (
        <EstadoDeErro
          erro={consulta.error}
          aoTentarNovamente={() => {
            void consulta.refetch()
          }}
        />
      ) : tabelas.length === 0 ? (
        <EstadoVazio
          icone={<Table2 className="size-6" />}
          titulo={filtrando ? 'Nenhuma vigência encontrada' : 'Nenhuma tabela de preços cadastrada'}
          descricao={
            filtrando
              ? 'Nenhuma vigência atende aos filtros aplicados.'
              : 'Cadastre a tabela enviada pela locadora para que os fechamentos mensais calculem o KM excedente automaticamente.'
          }
          acao={
            podeEditar && !filtrando ? (
              <Button
                variante="secundaria"
                tamanho="pequeno"
                onClick={() => {
                  definirCriando(true)
                }}
              >
                <Plus aria-hidden="true" />
                Cadastrar vigência
              </Button>
            ) : undefined
          }
        />
      ) : (
        <div className="space-y-3">
          {tabelas.map((tabela) => {
            const expandida = expandidas.includes(tabela.id)
            return (
              <Card key={tabela.id} className="overflow-hidden">
                <div className="flex flex-wrap items-center gap-3 px-5 py-4">
                  <button
                    type="button"
                    onClick={() => {
                      alternarExpansao(tabela.id)
                    }}
                    aria-expanded={expandida}
                    className="flex min-w-0 flex-1 items-center gap-3 text-left"
                  >
                    <ChevronDown
                      className={cn(
                        'size-4 shrink-0 text-texto-tenue transition-transform',
                        expandida && 'rotate-180',
                      )}
                      aria-hidden="true"
                    />
                    <div className="min-w-0">
                      <p className="font-medium text-texto">
                        {tabela.locadora.nome} — {tabela.anoVigencia}
                      </p>
                      <p className="text-xs text-texto-suave">
                        {tabela.grupos.length} grupo(s) · {tabela.kmExcedente.length} preço(s) de KM
                        excedente
                      </p>
                    </div>
                  </button>

                  <Badge variante="marca">Vigência {tabela.anoVigencia}</Badge>

                  {podeEditar ? (
                    <div className="flex items-center gap-1">
                      <Button
                        variante="sutil"
                        tamanho="icone"
                        aria-label={`Editar vigência ${String(tabela.anoVigencia)} de ${tabela.locadora.nome}`}
                        onClick={() => {
                          definirEmEdicao(tabela)
                        }}
                      >
                        <Pencil aria-hidden="true" />
                      </Button>
                      <Button
                        variante="sutil"
                        tamanho="icone"
                        aria-label={`Excluir vigência ${String(tabela.anoVigencia)} de ${tabela.locadora.nome}`}
                        onClick={() => {
                          definirEmExclusao(tabela)
                        }}
                      >
                        <Trash2 aria-hidden="true" />
                      </Button>
                    </div>
                  ) : null}
                </div>

                {expandida ? (
                  <div className="space-y-5 border-t border-borda bg-fundo-alternativo/30 px-5 py-5">
                    <GradeDeGrupos tabela={tabela} />
                    <GradeDeKmExcedente tabela={tabela} />
                    {tabela.observacoes ? (
                      <p className="text-sm text-texto-suave">{tabela.observacoes}</p>
                    ) : null}
                  </div>
                ) : null}
              </Card>
            )
          })}
        </div>
      )}

      {criando || emEdicao !== null ? (
        <FormularioDeTabelaDePreco
          key={emEdicao?.id ?? 'novo'}
          tabela={emEdicao}
          aoFechar={() => {
          definirCriando(false)
          definirEmEdicao(null)
        }}
        />
      ) : null}

      <DialogoDeExclusao
        aberto={emExclusao !== null}
        aoMudarAbertura={(aberto) => {
          if (!aberto) {
            definirEmExclusao(null)
          }
        }}
        titulo="Excluir vigência"
        descricao={`A tabela de ${emExclusao?.locadora.nome ?? ''} para ${String(emExclusao?.anoVigencia ?? '')} sairá das listagens. Fechamentos já calculados permanecem inalterados.`}
        aoConfirmar={() => exclusao.mutateAsync(emExclusao?.id ?? 0)}
      />
    </div>
  )
}

/** Grade de grupos e pacotes, reproduzindo a leitura da planilha da locadora. */
function GradeDeGrupos({ tabela }: { tabela: TabelaPreco }) {
  const pacotes = [
    ...new Set(tabela.grupos.flatMap((grupo) => grupo.pacotes.map((pacote) => pacote.pacoteKm))),
  ].sort((um, outro) => um - outro)

  if (tabela.grupos.length === 0) {
    return <p className="text-sm text-texto-suave">Nenhum grupo tarifário cadastrado nesta vigência.</p>
  }

  return (
    <section aria-labelledby={`grupos-${String(tabela.id)}`}>
      <h3 id={`grupos-${String(tabela.id)}`} className="mb-2 text-sm font-semibold text-texto">
        Valor mensal por grupo e pacote de KM
      </h3>
      <Tabela legenda={`Grupos tarifários da vigência ${String(tabela.anoVigencia)}`}>
        <TabelaCabecalho>
          <TabelaLinha>
            <TabelaColuna>Grupo</TabelaColuna>
            <TabelaColuna>Veículos</TabelaColuna>
            <TabelaColuna>Categoria</TabelaColuna>
            {pacotes.map((pacote) => (
              <TabelaColuna key={pacote} numerica>
                {formatarNumero(pacote)} km
              </TabelaColuna>
            ))}
          </TabelaLinha>
        </TabelaCabecalho>
        <TabelaCorpo>
          {tabela.grupos.map((grupo) => (
            <TabelaLinha key={grupo.id}>
              <TabelaCelula className="font-mono font-medium text-texto">{grupo.codigo}</TabelaCelula>
              <TabelaCelula className="text-texto-suave">{grupo.veiculosDoGrupo}</TabelaCelula>
              <TabelaCelula>
                <Badge variante="neutra">{grupo.categoriaDescricao}</Badge>
              </TabelaCelula>
              {pacotes.map((pacote) => {
                const encontrado = grupo.pacotes.find((item) => item.pacoteKm === pacote)
                return (
                  <TabelaCelula key={pacote} numerica className="text-texto">
                    {encontrado ? formatarMoeda(encontrado.valorMensal) : '—'}
                  </TabelaCelula>
                )
              })}
            </TabelaLinha>
          ))}
        </TabelaCorpo>
      </Tabela>
    </section>
  )
}

/** Preços de KM excedente por categoria (RN-06). */
function GradeDeKmExcedente({ tabela }: { tabela: TabelaPreco }) {
  if (tabela.kmExcedente.length === 0) {
    return (
      <p className="text-sm text-texto-suave">
        Nenhum valor de KM excedente cadastrado — o fechamento mensal não conseguirá estimar o
        custo de quilometragem ultrapassada nesta vigência.
      </p>
    )
  }

  return (
    <section aria-labelledby={`excedente-${String(tabela.id)}`}>
      <h3 id={`excedente-${String(tabela.id)}`} className="mb-2 text-sm font-semibold text-texto">
        Valor do KM excedente
      </h3>
      <Tabela legenda={`Preços de KM excedente da vigência ${String(tabela.anoVigencia)}`}>
        <TabelaCabecalho>
          <TabelaLinha>
            <TabelaColuna>Categoria</TabelaColuna>
            <TabelaColuna>Aplica-se a</TabelaColuna>
            <TabelaColuna numerica>Valor por KM</TabelaColuna>
          </TabelaLinha>
        </TabelaCabecalho>
        <TabelaCorpo>
          {tabela.kmExcedente.map((preco, indice) => (
            <TabelaLinha key={`${preco.categoria}-${String(preco.pacoteKm ?? indice)}`}>
              <TabelaCelula className="text-texto">{preco.categoriaDescricao}</TabelaCelula>
              <TabelaCelula className="text-texto-suave">
                {preco.pacoteKm ? `Pacote de ${formatarNumero(preco.pacoteKm)} km` : 'Todos os pacotes'}
              </TabelaCelula>
              <TabelaCelula numerica className="text-texto">
                {formatarMoeda(preco.valorKm)}
              </TabelaCelula>
            </TabelaLinha>
          ))}
        </TabelaCorpo>
      </Tabela>
    </section>
  )
}

function anosDisponiveis(tabelas: TabelaPreco[]): number[] {
  return [...new Set(tabelas.map((tabela) => tabela.anoVigencia))].sort((um, outro) => outro - um)
}
