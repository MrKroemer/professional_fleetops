import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import { Building2, Plus } from 'lucide-react'
import { useState } from 'react'

import { FormularioDeObra } from './FormularioDeObra'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { TabelaDeDados } from '@/components/ui/tabela-de-dados'
import { BarraDeFiltros } from '@/features/cadastros/componentes/BarraDeFiltros'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { DetalheDoRegistro } from '@/features/cadastros/componentes/DetalheDoRegistro'
import { DialogoDeExclusao } from '@/features/cadastros/componentes/DialogoDeExclusao'
import { STATUS_DE_OBRA, type Obra, type StatusObra } from '@/features/cadastros/tipos'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData, formatarDataHora } from '@/lib/formatters'
import { useDebounce } from '@/lib/use-debounce'

/** Tamanho de página usado só na exportação, para levar a visão filtrada inteira. */
const TUDO = 2000

/** Cadastro de obras — as frentes de trabalho onde a frota é alocada. */
export function ObrasPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [termo, definirTermo] = useState('')
  const [status, definirStatus] = useState<StatusObra | ''>('')
  const termoAplicado = useDebounce(termo)

  const [pagina, definirPagina] = useState(0)
  const [tamanho, definirTamanho] = useState(20)
  const [ordenacao, definirOrdenacao] = useState<SortingState>([{ id: 'codigo', desc: false }])

  const [criando, definirCriando] = useState(false)
  const [emDetalhe, definirEmDetalhe] = useState<Obra | null>(null)
  const [emEdicao, definirEmEdicao] = useState<Obra | null>(null)
  const [emExclusao, definirEmExclusao] = useState<Obra | null>(null)

  const filtros = {
    ...(termoAplicado ? { termo: termoAplicado } : {}),
    ...(status ? { status } : {}),
  }
  const ordem = ordenacao[0]
  const sort = [`${ordem?.id ?? 'codigo'},${ordem?.desc ? 'desc' : 'asc'}`]

  const consulta = useQuery({
    queryKey: ['obras', { filtros, pagina, tamanho, sort }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/obras', { params: { query: { ...filtros, page: pagina, size: tamanho, sort } } }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(await api.DELETE('/api/v1/obras/{id}', { params: { path: { id } } })),
    onSuccess: async () => {
      definirEmDetalhe(null)
      await clienteDeConsultas.invalidateQueries({ queryKey: ['obras'] })
    },
  })

  const obras = consulta.data?.conteudo ?? []
  const filtrando = termoAplicado.length > 0 || status !== ''

  const colunas: ColumnDef<Obra, unknown>[] = [
    {
      id: 'codigo',
      header: 'Obra',
      enableHiding: false,
      meta: { rotulo: 'Obra', exportar: (o) => `${o.codigo} — ${o.nome}` },
      cell: ({ row }) => (
        <div>
          <p className="font-medium text-texto">{row.original.nome}</p>
          <p className="font-mono text-xs text-texto-suave">{row.original.codigo}</p>
        </div>
      ),
    },
    {
      id: 'cliente',
      header: 'Cliente',
      meta: { rotulo: 'Cliente', exportar: (o) => o.cliente ?? '' },
      cell: ({ row }) => <span className="text-texto-suave">{row.original.cliente ?? '—'}</span>,
    },
    {
      id: 'cidade',
      header: 'Localização',
      meta: { rotulo: 'Localização', exportar: (o) => `${o.cidade} — ${o.uf}` },
      cell: ({ row }) => (
        <span className="text-texto-suave">
          {row.original.cidade} — {row.original.uf}
        </span>
      ),
    },
    {
      id: 'dataInicio',
      header: 'Período',
      meta: {
        rotulo: 'Período',
        exportar: (o) => `${formatarData(o.dataInicio)} a ${formatarData(o.dataFim)}`,
      },
      cell: ({ row }) => (
        <span className="text-texto-suave">
          {row.original.dataInicio || row.original.dataFim
            ? `${formatarData(row.original.dataInicio)} → ${formatarData(row.original.dataFim)}`
            : '—'}
        </span>
      ),
    },
    {
      id: 'status',
      header: 'Situação',
      meta: { rotulo: 'Situação', exportar: (o) => o.statusDescricao },
      cell: ({ row }) => (
        <Badge variante={row.original.status === 'ATIVA' ? 'sucesso' : 'neutra'}>
          {row.original.statusDescricao}
        </Badge>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Obras"
        descricao="Frentes de trabalho onde os veículos são alocados. É por obra que fornecedores são credenciados e os custos da frota são apurados."
        acao={
          podeEditar ? (
            <Button onClick={() => { definirCriando(true) }}>
              <Plus aria-hidden="true" />
              Nova obra
            </Button>
          ) : undefined
        }
      />

      <BarraDeFiltros
        termo={termo}
        aoMudarTermo={(valor) => { definirTermo(valor); definirPagina(0) }}
        placeholder="Buscar por código, nome, cliente ou cidade"
      >
        <Select
          value={status}
          onChange={(evento) => { definirStatus(evento.target.value as StatusObra | ''); definirPagina(0) }}
          className="w-48"
          aria-label="Filtrar por situação"
        >
          <option value="">Todas as situações</option>
          {Object.entries(STATUS_DE_OBRA).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </Select>
      </BarraDeFiltros>

      <TabelaDeDados
        legenda="Obras cadastradas, com código, cliente, localização, período e situação"
        colunas={colunas}
        dados={obras}
        paginacao={{
          pagina,
          tamanho,
          totalElementos: consulta.data?.totalElementos ?? 0,
          totalPaginas: consulta.data?.totalPaginas ?? 0,
        }}
        aoMudarPagina={definirPagina}
        aoMudarTamanho={(novo) => { definirTamanho(novo); definirPagina(0) }}
        ordenacao={ordenacao}
        aoMudarOrdenacao={(nova) => { definirOrdenacao(nova); definirPagina(0) }}
        carregando={consulta.isPending}
        erro={consulta.isError ? consulta.error : undefined}
        aoTentarNovamente={() => { void consulta.refetch() }}
        aoSelecionarLinha={definirEmDetalhe}
        nomeDoArquivo="obras"
        aoExportar={async () => {
          const todas = exigirSucesso(
            await api.GET('/api/v1/obras', { params: { query: { ...filtros, page: 0, size: TUDO, sort } } }),
          )
          return todas.conteudo
        }}
        vazio={
          <EstadoVazio
            icone={<Building2 className="size-6" />}
            titulo={filtrando ? 'Nenhuma obra encontrada' : 'Nenhuma obra cadastrada'}
            descricao={
              filtrando
                ? 'Nenhum registro atende aos filtros aplicados. Ajuste a busca ou limpe a situação selecionada.'
                : 'Cadastre a primeira obra para começar a organizar a frota por frente de trabalho.'
            }
            acao={
              podeEditar && !filtrando ? (
                <Button variante="secundaria" tamanho="pequeno" onClick={() => { definirCriando(true) }}>
                  <Plus aria-hidden="true" />
                  Cadastrar obra
                </Button>
              ) : undefined
            }
          />
        }
      />

      {emDetalhe ? (
        <DetalheDoRegistro
          titulo={emDetalhe.nome}
          subtitulo={`${emDetalhe.codigo} · ${emDetalhe.cidade} — ${emDetalhe.uf}`}
          selo={{
            texto: emDetalhe.statusDescricao,
            variante: emDetalhe.status === 'ATIVA' ? 'sucesso' : 'neutra',
          }}
          secoes={[
            {
              titulo: 'Identificação',
              campos: [
                { rotulo: 'Código', valor: emDetalhe.codigo },
                { rotulo: 'Nome', valor: emDetalhe.nome },
                { rotulo: 'Cliente', valor: emDetalhe.cliente },
                { rotulo: 'Situação', valor: emDetalhe.statusDescricao },
              ],
            },
            {
              titulo: 'Localização e período',
              campos: [
                { rotulo: 'Cidade', valor: emDetalhe.cidade },
                { rotulo: 'UF', valor: emDetalhe.uf },
                { rotulo: 'Início', valor: formatarData(emDetalhe.dataInicio) },
                { rotulo: 'Encerramento', valor: formatarData(emDetalhe.dataFim) },
              ],
            },
            {
              titulo: 'Observações',
              colunas: 1,
              campos: [{ rotulo: 'Anotações', valor: emDetalhe.observacoes, larguraTotal: true }],
            },
            {
              titulo: 'Auditoria',
              campos: [
                { rotulo: 'Cadastrada em', valor: formatarDataHora(emDetalhe.criadoEm) },
                { rotulo: 'Última alteração', valor: formatarDataHora(emDetalhe.atualizadoEm) },
              ],
            },
          ]}
          aoFechar={() => { definirEmDetalhe(null) }}
          podeEditar={podeEditar}
          aoEditar={() => { definirEmEdicao(emDetalhe); definirEmDetalhe(null) }}
          aoExcluir={() => { definirEmExclusao(emDetalhe) }}
        />
      ) : null}

      {criando || emEdicao !== null ? (
        <FormularioDeObra
          key={emEdicao?.id ?? 'novo'}
          obra={emEdicao}
          aoFechar={() => { definirCriando(false); definirEmEdicao(null) }}
        />
      ) : null}

      <DialogoDeExclusao
        aberto={emExclusao !== null}
        aoMudarAbertura={(aberto) => { if (!aberto) { definirEmExclusao(null) } }}
        titulo="Excluir obra"
        descricao={`A obra "${emExclusao?.nome ?? ''}" deixará de aparecer nas listagens e não poderá receber novos contratos.`}
        aoConfirmar={() => exclusao.mutateAsync(emExclusao?.id ?? 0)}
      />
    </div>
  )
}
