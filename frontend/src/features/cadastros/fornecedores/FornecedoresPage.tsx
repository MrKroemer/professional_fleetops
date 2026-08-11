import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import { Plus, Store } from 'lucide-react'
import { useState } from 'react'

import { FormularioDeFornecedor } from './FormularioDeFornecedor'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { TabelaDeDados } from '@/components/ui/tabela-de-dados'
import { BarraDeFiltros } from '@/features/cadastros/componentes/BarraDeFiltros'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { DetalheDoRegistro } from '@/features/cadastros/componentes/DetalheDoRegistro'
import { DialogoDeExclusao } from '@/features/cadastros/componentes/DialogoDeExclusao'
import { RevelarCredenciais } from '@/features/cadastros/locadoras/RevelarCredenciais'
import {
  DIAS_DA_SEMANA,
  TIPOS_DE_FORNECEDOR,
  type Fornecedor,
  type TipoFornecedor,
} from '@/features/cadastros/tipos'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData, formatarDataHora, formatarMoeda } from '@/lib/formatters'
import { useDebounce } from '@/lib/use-debounce'

const TUDO = 2000

/** Cadastro de fornecedores credenciados, com os campos próprios de cada tipo. */
export function FornecedoresPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const podeVerCredenciais = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [termo, definirTermo] = useState('')
  const [tipo, definirTipo] = useState<TipoFornecedor | ''>('')
  const [obraId, definirObraId] = useState('')
  const termoAplicado = useDebounce(termo)

  const [pagina, definirPagina] = useState(0)
  const [tamanho, definirTamanho] = useState(20)
  const [ordenacao, definirOrdenacao] = useState<SortingState>([{ id: 'nome', desc: false }])

  const [criando, definirCriando] = useState(false)
  const [emDetalhe, definirEmDetalhe] = useState<Fornecedor | null>(null)
  const [emEdicao, definirEmEdicao] = useState<Fornecedor | null>(null)
  const [emExclusao, definirEmExclusao] = useState<Fornecedor | null>(null)

  const obras = useQuery({
    queryKey: ['obras', 'selecao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/obras', { params: { query: { page: 0, size: 200, sort: ['codigo,asc'] } } }),
      ),
  })

  const filtros = {
    ...(termoAplicado ? { termo: termoAplicado } : {}),
    ...(tipo ? { tipo } : {}),
    ...(obraId ? { obraId: Number(obraId) } : {}),
  }
  const ordem = ordenacao[0]
  const sort = [`${ordem?.id ?? 'nome'},${ordem?.desc ? 'desc' : 'asc'}`]

  const consulta = useQuery({
    queryKey: ['fornecedores', { filtros, pagina, tamanho, sort }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/fornecedores', {
          params: { query: { ...filtros, page: pagina, size: tamanho, sort } },
        }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(await api.DELETE('/api/v1/fornecedores/{id}', { params: { path: { id } } })),
    onSuccess: async () => {
      definirEmDetalhe(null)
      await clienteDeConsultas.invalidateQueries({ queryKey: ['fornecedores'] })
    },
  })

  const fornecedores = consulta.data?.conteudo ?? []
  const filtrando = termoAplicado.length > 0 || tipo !== '' || obraId !== ''

  const colunas: ColumnDef<Fornecedor, unknown>[] = [
    {
      id: 'nome',
      header: 'Fornecedor',
      enableHiding: false,
      meta: { rotulo: 'Fornecedor', exportar: (f) => f.nome },
      cell: ({ row }) => (
        <div>
          <p className="font-medium text-texto">{row.original.nome}</p>
          <p className="text-xs text-texto-suave">
            {[row.original.cidade, row.original.uf].filter(Boolean).join(' — ') || '—'}
          </p>
        </div>
      ),
    },
    {
      id: 'tipo',
      header: 'Tipo',
      meta: { rotulo: 'Tipo', exportar: (f) => f.tipoDescricao },
      cell: ({ row }) => <Badge variante="neutra">{row.original.tipoDescricao}</Badge>,
    },
    {
      id: 'obras',
      header: 'Obras atendidas',
      enableSorting: false,
      meta: { rotulo: 'Obras atendidas', exportar: (f) => f.obras.map((o) => o.codigo).join(' ') },
      cell: ({ row }) =>
        row.original.obras.length === 0 ? (
          <span className="text-xs text-texto-tenue">Nenhuma</span>
        ) : (
          <div className="flex flex-wrap gap-1">
            {row.original.obras.map((obra) => (
              <Badge key={obra.id} variante="marca">
                {obra.codigo}
              </Badge>
            ))}
          </div>
        ),
    },
    {
      id: 'condicoes',
      header: 'Condições',
      enableSorting: false,
      meta: { rotulo: 'Condições', exportar: condicoesDoTipo },
      cell: ({ row }) => (
        <span className="text-xs text-texto-suave">{condicoesDoTipo(row.original)}</span>
      ),
    },
    {
      id: 'ativo',
      header: 'Situação',
      meta: { rotulo: 'Situação', exportar: (f) => (f.ativo ? 'Ativo' : 'Inativo') },
      cell: ({ row }) => (
        <div className="flex items-center gap-1.5">
          <Badge variante={row.original.ativo ? 'sucesso' : 'neutra'}>
            {row.original.ativo ? 'Ativo' : 'Inativo'}
          </Badge>
          {podeVerCredenciais && row.original.rastreador?.possuiCredenciais ? (
            <RevelarCredenciais origem="fornecedor" id={row.original.id} nome={row.original.nome} />
          ) : null}
        </div>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Fornecedores credenciados"
        descricao="Postos, lava-jatos, borracharias, para-brisas, rastreadores, gráficas e oficinas. Cada tipo tem seus próprios dados — dias autorizados de abastecimento, frequência de lavagem, custos de rastreamento."
        acao={
          podeEditar ? (
            <Button onClick={() => { definirCriando(true) }}>
              <Plus aria-hidden="true" />
              Novo fornecedor
            </Button>
          ) : undefined
        }
      />

      <BarraDeFiltros
        termo={termo}
        aoMudarTermo={(valor) => { definirTermo(valor); definirPagina(0) }}
        placeholder="Buscar por nome, cidade ou responsável"
      >
        <Select
          value={tipo}
          onChange={(evento) => { definirTipo(evento.target.value as TipoFornecedor | ''); definirPagina(0) }}
          className="w-56"
          aria-label="Filtrar por tipo"
        >
          <option value="">Todos os tipos</option>
          {Object.entries(TIPOS_DE_FORNECEDOR).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </Select>
        <Select
          value={obraId}
          onChange={(evento) => { definirObraId(evento.target.value); definirPagina(0) }}
          className="w-56"
          aria-label="Filtrar por obra atendida"
        >
          <option value="">Todas as obras</option>
          {(obras.data?.conteudo ?? []).map((obra) => (
            <option key={obra.id} value={obra.id}>{obra.codigo} — {obra.nome}</option>
          ))}
        </Select>
      </BarraDeFiltros>

      <TabelaDeDados
        legenda="Fornecedores credenciados, com tipo, localização, obras atendidas e condições"
        colunas={colunas}
        dados={fornecedores}
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
        nomeDoArquivo="fornecedores"
        aoExportar={async () => {
          const todos = exigirSucesso(
            await api.GET('/api/v1/fornecedores', {
              params: { query: { ...filtros, page: 0, size: TUDO, sort } },
            }),
          )
          return todos.conteudo
        }}
        vazio={
          <EstadoVazio
            icone={<Store className="size-6" />}
            titulo={filtrando ? 'Nenhum fornecedor encontrado' : 'Nenhum fornecedor cadastrado'}
            descricao={
              filtrando
                ? 'Nenhum registro atende aos filtros aplicados.'
                : 'Credencie os fornecedores que atendem cada obra para controlar abastecimentos, lavagens e serviços.'
            }
            acao={
              podeEditar && !filtrando ? (
                <Button variante="secundaria" tamanho="pequeno" onClick={() => { definirCriando(true) }}>
                  <Plus aria-hidden="true" />
                  Credenciar fornecedor
                </Button>
              ) : undefined
            }
          />
        }
      />

      {emDetalhe ? (
        <DetalheDoRegistro
          titulo={emDetalhe.nome}
          subtitulo={`${emDetalhe.tipoDescricao}${emDetalhe.cidade ? ` · ${emDetalhe.cidade}` : ''}`}
          selo={{
            texto: emDetalhe.ativo ? 'Ativo' : 'Inativo',
            variante: emDetalhe.ativo ? 'sucesso' : 'neutra',
          }}
          secoes={[
            {
              titulo: 'Contato',
              campos: [
                { rotulo: 'Cidade', valor: emDetalhe.cidade },
                { rotulo: 'UF', valor: emDetalhe.uf },
                { rotulo: 'Endereço', valor: emDetalhe.endereco, larguraTotal: true },
                { rotulo: 'Telefone', valor: emDetalhe.telefone },
                { rotulo: 'E-mail', valor: emDetalhe.email },
                { rotulo: 'Responsável', valor: emDetalhe.responsavel },
                { rotulo: 'Funcionamento', valor: emDetalhe.funcionamento },
              ],
            },
            {
              titulo: 'Condições comerciais',
              campos: [
                { rotulo: 'Faturamento', valor: emDetalhe.formaFaturamento },
                { rotulo: 'Pagamento', valor: emDetalhe.formaPagamento },
                { rotulo: 'Credenciado em', valor: formatarData(emDetalhe.credenciadoEm) },
                {
                  rotulo: 'Obras atendidas',
                  valor: emDetalhe.obras.map((obra) => obra.codigo).join(', ') || null,
                  larguraTotal: true,
                },
              ],
            },
            ...camposDoTipo(emDetalhe),
            {
              titulo: 'Observações',
              colunas: 1,
              campos: [{ rotulo: 'Anotações', valor: emDetalhe.observacoes, larguraTotal: true }],
            },
            {
              titulo: 'Auditoria',
              campos: [
                { rotulo: 'Cadastrado em', valor: formatarDataHora(emDetalhe.criadoEm) },
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
        <FormularioDeFornecedor
          key={emEdicao?.id ?? 'novo'}
          fornecedor={emEdicao}
          aoFechar={() => { definirCriando(false); definirEmEdicao(null) }}
        />
      ) : null}

      <DialogoDeExclusao
        aberto={emExclusao !== null}
        aoMudarAbertura={(aberto) => { if (!aberto) { definirEmExclusao(null) } }}
        titulo="Excluir fornecedor"
        descricao={`O fornecedor "${emExclusao?.nome ?? ''}" sairá das listagens e suas credenciais, se houver, serão descartadas.`}
        aoConfirmar={() => exclusao.mutateAsync(emExclusao?.id ?? 0)}
      />
    </div>
  )
}

/** Resume, em uma linha, o dado que mais importa em cada tipo de fornecedor. */
function condicoesDoTipo(fornecedor: Fornecedor): string {
  if (fornecedor.posto) {
    if (fornecedor.posto.semRestricaoDeDia) {
      return 'Abastecimento sem restrição de dia'
    }
    const dias = DIAS_DA_SEMANA.filter((dia) => fornecedor.posto?.diasAutorizados.includes(dia.valor))
      .map((dia) => dia.abreviado)
      .join(', ')
    return `Abastece ${dias}`
  }
  if (fornecedor.lavaJato) {
    return `${String(fornecedor.lavaJato.servicosPorSemana)}× por semana · Passeio ${formatarMoeda(fornecedor.lavaJato.precoPasseio)}`
  }
  if (fornecedor.rastreador) {
    return `Mensalidade ${formatarMoeda(fornecedor.rastreador.mensalidade)}`
  }
  if (fornecedor.grafica) {
    return `Adesivo ${formatarMoeda(fornecedor.grafica.precoAdesivo)}`
  }
  return fornecedor.funcionamento ?? '—'
}

/** Seções específicas do tipo, para o painel de detalhe. */
function camposDoTipo(fornecedor: Fornecedor) {
  if (fornecedor.posto) {
    const dias = DIAS_DA_SEMANA.filter((dia) => fornecedor.posto?.diasAutorizados.includes(dia.valor))
      .map((dia) => dia.rotulo)
      .join(', ')
    return [
      {
        titulo: 'Dados do posto',
        campos: [
          {
            rotulo: 'Dias autorizados',
            valor: fornecedor.posto.semRestricaoDeDia ? 'Sem restrição de dia' : dias,
            larguraTotal: true,
          },
          { rotulo: 'Acesso às faturas', valor: fornecedor.posto.acessoFaturas },
        ],
      },
    ]
  }
  if (fornecedor.lavaJato) {
    return [
      {
        titulo: 'Dados do lava-jato',
        campos: [
          {
            rotulo: 'Serviços por semana',
            valor: String(fornecedor.lavaJato.servicosPorSemana),
          },
          { rotulo: 'Preço passeio', valor: formatarMoeda(fornecedor.lavaJato.precoPasseio) },
          { rotulo: 'Preço SUV', valor: formatarMoeda(fornecedor.lavaJato.precoSuv) },
          { rotulo: 'Preço 4x4', valor: formatarMoeda(fornecedor.lavaJato.precoQuatroXQuatro) },
        ],
      },
    ]
  }
  if (fornecedor.rastreador) {
    return [
      {
        titulo: 'Dados do rastreador',
        campos: [
          { rotulo: 'Mensalidade', valor: formatarMoeda(fornecedor.rastreador.mensalidade) },
          { rotulo: 'Instalação', valor: formatarMoeda(fornecedor.rastreador.custoInstalacao) },
          { rotulo: 'Desinstalação', valor: formatarMoeda(fornecedor.rastreador.custoDesinstalacao) },
          { rotulo: 'Equipadora', valor: fornecedor.rastreador.equipadora },
          { rotulo: 'Portal', valor: fornecedor.rastreador.portalUrl, larguraTotal: true },
          {
            rotulo: 'Credenciais',
            valor: fornecedor.rastreador.possuiCredenciais
              ? `${fornecedor.rastreador.credencialMascarada} — revele pela listagem`
              : 'Não cadastradas',
          },
        ],
      },
    ]
  }
  if (fornecedor.grafica) {
    return [
      {
        titulo: 'Dados da gráfica',
        campos: [
          { rotulo: 'Tamanho do adesivo', valor: fornecedor.grafica.tamanhoAdesivo },
          { rotulo: 'Preço do adesivo', valor: formatarMoeda(fornecedor.grafica.precoAdesivo) },
          { rotulo: 'Tamanho do imã', valor: fornecedor.grafica.tamanhoIma },
          { rotulo: 'Preço do imã', valor: formatarMoeda(fornecedor.grafica.precoIma) },
        ],
      },
    ]
  }
  return []
}
