import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import { Handshake, Plus } from 'lucide-react'
import { useState } from 'react'

import { FormularioDeLocadora } from './FormularioDeLocadora'
import { RevelarCredenciais } from './RevelarCredenciais'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { TabelaDeDados } from '@/components/ui/tabela-de-dados'
import { BarraDeFiltros } from '@/features/cadastros/componentes/BarraDeFiltros'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { DetalheDoRegistro } from '@/features/cadastros/componentes/DetalheDoRegistro'
import { DialogoDeExclusao } from '@/features/cadastros/componentes/DialogoDeExclusao'
import { TIPOS_DE_LOCADORA, type Locadora, type TipoLocadora } from '@/features/cadastros/tipos'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarDataHora } from '@/lib/formatters'
import { useDebounce } from '@/lib/use-debounce'

const TUDO = 2000

/** Cadastro de locadoras, com canais de atendimento e credenciais de portal (RN-20). */
export function LocadorasPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const podeVerCredenciais = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [termo, definirTermo] = useState('')
  const [tipo, definirTipo] = useState<TipoLocadora | ''>('')
  const termoAplicado = useDebounce(termo)

  const [pagina, definirPagina] = useState(0)
  const [tamanho, definirTamanho] = useState(20)
  const [ordenacao, definirOrdenacao] = useState<SortingState>([{ id: 'nome', desc: false }])

  const [criando, definirCriando] = useState(false)
  const [emDetalhe, definirEmDetalhe] = useState<Locadora | null>(null)
  const [emEdicao, definirEmEdicao] = useState<Locadora | null>(null)
  const [emExclusao, definirEmExclusao] = useState<Locadora | null>(null)

  const filtros = {
    ...(termoAplicado ? { termo: termoAplicado } : {}),
    ...(tipo ? { tipo } : {}),
  }
  const ordem = ordenacao[0]
  const sort = [`${ordem?.id ?? 'nome'},${ordem?.desc ? 'desc' : 'asc'}`]

  const consulta = useQuery({
    queryKey: ['locadoras', { filtros, pagina, tamanho, sort }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/locadoras', { params: { query: { ...filtros, page: pagina, size: tamanho, sort } } }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(await api.DELETE('/api/v1/locadoras/{id}', { params: { path: { id } } })),
    onSuccess: async () => {
      definirEmDetalhe(null)
      await clienteDeConsultas.invalidateQueries({ queryKey: ['locadoras'] })
    },
  })

  const locadoras = consulta.data?.conteudo ?? []
  const filtrando = termoAplicado.length > 0 || tipo !== ''

  const colunas: ColumnDef<Locadora, unknown>[] = [
    {
      id: 'nome',
      header: 'Locadora',
      enableHiding: false,
      meta: { rotulo: 'Locadora', exportar: (l) => l.nome },
      cell: ({ row }) => (
        <div>
          <p className="font-medium text-texto">{row.original.nome}</p>
          <p className="text-xs text-texto-suave">{row.original.telefone ?? '—'}</p>
        </div>
      ),
    },
    {
      id: 'tipo',
      header: 'Tipo',
      meta: { rotulo: 'Tipo', exportar: (l) => l.tipoDescricao },
      cell: ({ row }) => (
        <Badge variante={row.original.tipo === 'NACIONAL' ? 'marca' : 'neutra'}>
          {row.original.tipoDescricao}
        </Badge>
      ),
    },
    {
      id: 'consultor',
      header: 'Consultor',
      meta: { rotulo: 'Consultor', exportar: (l) => l.consultor ?? '' },
      cell: ({ row }) => <span className="text-texto-suave">{row.original.consultor ?? '—'}</span>,
    },
    {
      id: 'credenciais',
      header: 'Portal',
      enableSorting: false,
      meta: {
        rotulo: 'Portal',
        // A exportação nunca leva o segredo, só a informação de que ele existe (RN-20).
        exportar: (l) => (l.possuiCredenciais ? 'Com credenciais' : 'Sem credenciais'),
      },
      cell: ({ row }) =>
        row.original.possuiCredenciais ? (
          <div className="flex items-center gap-1.5">
            <span className="font-mono text-sm text-texto-suave">{row.original.credencialMascarada}</span>
            {podeVerCredenciais ? (
              <RevelarCredenciais origem="locadora" id={row.original.id} nome={row.original.nome} />
            ) : null}
          </div>
        ) : (
          <span className="text-sm text-texto-tenue">Sem credenciais</span>
        ),
    },
    {
      id: 'ativa',
      header: 'Situação',
      meta: { rotulo: 'Situação', exportar: (l) => (l.ativa ? 'Ativa' : 'Inativa') },
      cell: ({ row }) => (
        <Badge variante={row.original.ativa ? 'sucesso' : 'neutra'}>
          {row.original.ativa ? 'Ativa' : 'Inativa'}
        </Badge>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Locadoras"
        descricao="Empresas de quem os veículos são alugados, com os canais de atendimento que o condutor precisa acionar em campo e as credenciais dos portais."
        acao={
          podeEditar ? (
            <Button onClick={() => { definirCriando(true) }}>
              <Plus aria-hidden="true" />
              Nova locadora
            </Button>
          ) : undefined
        }
      />

      <BarraDeFiltros
        termo={termo}
        aoMudarTermo={(valor) => { definirTermo(valor); definirPagina(0) }}
        placeholder="Buscar por nome ou consultor"
      >
        <Select
          value={tipo}
          onChange={(evento) => { definirTipo(evento.target.value as TipoLocadora | ''); definirPagina(0) }}
          className="w-52"
          aria-label="Filtrar por tipo"
        >
          <option value="">Todos os tipos</option>
          {Object.entries(TIPOS_DE_LOCADORA).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </Select>
      </BarraDeFiltros>

      <TabelaDeDados
        legenda="Locadoras cadastradas, com tipo, consultor, credenciais e situação"
        colunas={colunas}
        dados={locadoras}
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
        nomeDoArquivo="locadoras"
        aoExportar={async () => {
          const todas = exigirSucesso(
            await api.GET('/api/v1/locadoras', { params: { query: { ...filtros, page: 0, size: TUDO, sort } } }),
          )
          return todas.conteudo
        }}
        vazio={
          <EstadoVazio
            icone={<Handshake className="size-6" />}
            titulo={filtrando ? 'Nenhuma locadora encontrada' : 'Nenhuma locadora cadastrada'}
            descricao={
              filtrando
                ? 'Nenhum registro atende aos filtros aplicados.'
                : 'Cadastre as locadoras parceiras para vincular veículos e tabelas de preço.'
            }
            acao={
              podeEditar && !filtrando ? (
                <Button variante="secundaria" tamanho="pequeno" onClick={() => { definirCriando(true) }}>
                  <Plus aria-hidden="true" />
                  Cadastrar locadora
                </Button>
              ) : undefined
            }
          />
        }
      />

      {emDetalhe ? (
        <DetalheDoRegistro
          titulo={emDetalhe.nome}
          subtitulo={`${emDetalhe.tipoDescricao}${emDetalhe.consultor ? ` · ${emDetalhe.consultor}` : ''}`}
          selo={{
            texto: emDetalhe.ativa ? 'Ativa' : 'Inativa',
            variante: emDetalhe.ativa ? 'sucesso' : 'neutra',
          }}
          secoes={[
            {
              titulo: 'Contato',
              campos: [
                { rotulo: 'Consultor', valor: emDetalhe.consultor },
                { rotulo: 'Telefone', valor: emDetalhe.telefone },
                { rotulo: 'E-mail', valor: emDetalhe.email },
                { rotulo: 'Portal', valor: emDetalhe.portalUrl, larguraTotal: true },
                {
                  rotulo: 'Credenciais',
                  valor: emDetalhe.possuiCredenciais
                    ? `${emDetalhe.credencialMascarada} — revele pela listagem`
                    : 'Não cadastradas',
                },
              ],
            },
            {
              titulo: 'Canais de atendimento',
              campos: [
                { rotulo: 'Reservas', valor: emDetalhe.canais?.reservas },
                { rotulo: 'Manutenção', valor: emDetalhe.canais?.manutencao },
                { rotulo: 'Guincho / sinistro', valor: emDetalhe.canais?.guinchoSinistro },
                { rotulo: 'Assistência 24h', valor: emDetalhe.canais?.assistencia24h },
                { rotulo: 'Financeiro', valor: emDetalhe.canais?.financeiro },
                { rotulo: 'Suporte', valor: emDetalhe.canais?.suporte },
                { rotulo: 'Telemetria', valor: emDetalhe.canais?.telemetria, larguraTotal: true },
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
        <FormularioDeLocadora
          key={emEdicao?.id ?? 'novo'}
          locadora={emEdicao}
          aoFechar={() => { definirCriando(false); definirEmEdicao(null) }}
        />
      ) : null}

      <DialogoDeExclusao
        aberto={emExclusao !== null}
        aoMudarAbertura={(aberto) => { if (!aberto) { definirEmExclusao(null) } }}
        titulo="Excluir locadora"
        descricao={`A locadora "${emExclusao?.nome ?? ''}" sairá das listagens e suas credenciais de portal serão descartadas.`}
        aoConfirmar={() => exclusao.mutateAsync(emExclusao?.id ?? 0)}
      />
    </div>
  )
}
