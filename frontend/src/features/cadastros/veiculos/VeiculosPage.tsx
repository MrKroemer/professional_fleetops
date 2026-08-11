import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import { Car, Fuel, Plus, Radio, Sticker } from 'lucide-react'
import { useState } from 'react'

import { FormularioDeVeiculo } from './FormularioDeVeiculo'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { TabelaDeDados } from '@/components/ui/tabela-de-dados'
import { Tooltip } from '@/components/ui/tooltip'
import { BarraDeFiltros } from '@/features/cadastros/componentes/BarraDeFiltros'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { DetalheDoRegistro } from '@/features/cadastros/componentes/DetalheDoRegistro'
import { DialogoDeExclusao } from '@/features/cadastros/componentes/DialogoDeExclusao'
import {
  CATEGORIAS_DE_VEICULO,
  STATUS_DE_VEICULO,
  type CategoriaVeiculo,
  type StatusVeiculo,
  type Veiculo,
} from '@/features/cadastros/tipos'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarDataHora } from '@/lib/formatters'
import { useDebounce } from '@/lib/use-debounce'

/** Tamanho de página máximo aceito pela API, usado apenas na exportação. */
const TUDO = 2000

/**
 * Cadastro de veículos.
 *
 * A lista usa a tabela de dados do sistema: ordenação e paginação acontecem no
 * servidor, porque com centenas de veículos ordenar no navegador ordenaria só a
 * página visível. Clicar na linha abre o detalhe lateral; editar e excluir são ações
 * do detalhe, não da linha — o que mantém a tabela legível e evita cliques acidentais
 * em ícones minúsculos.
 */
export function VeiculosPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [termo, definirTermo] = useState('')
  const [categoria, definirCategoria] = useState<CategoriaVeiculo | ''>('')
  const [status, definirStatus] = useState<StatusVeiculo | ''>('')
  const termoAplicado = useDebounce(termo)

  const [pagina, definirPagina] = useState(0)
  const [tamanho, definirTamanho] = useState(20)
  const [ordenacao, definirOrdenacao] = useState<SortingState>([{ id: 'placa', desc: false }])

  const [criando, definirCriando] = useState(false)
  const [emDetalhe, definirEmDetalhe] = useState<Veiculo | null>(null)
  const [emEdicao, definirEmEdicao] = useState<Veiculo | null>(null)
  const [emExclusao, definirEmExclusao] = useState<Veiculo | null>(null)

  const filtros = {
    ...(termoAplicado ? { termo: termoAplicado } : {}),
    ...(categoria ? { categoria } : {}),
    ...(status ? { status } : {}),
  }
  const ordem = ordenacao[0]
  const sort = ordem ? [`${ordem.id},${ordem.desc ? 'desc' : 'asc'}`] : ['placa,asc']

  const consulta = useQuery({
    queryKey: ['veiculos', { filtros, pagina, tamanho, sort }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/veiculos', { params: { query: { ...filtros, page: pagina, size: tamanho, sort } } }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(await api.DELETE('/api/v1/veiculos/{id}', { params: { path: { id } } })),
    onSuccess: async () => {
      definirEmDetalhe(null)
      await clienteDeConsultas.invalidateQueries({ queryKey: ['veiculos'] })
    },
  })

  const veiculos = consulta.data?.conteudo ?? []
  const filtrando = termoAplicado.length > 0 || categoria !== '' || status !== ''

  const colunas: ColumnDef<Veiculo, unknown>[] = [
    {
      id: 'placa',
      header: 'Placa',
      enableHiding: false,
      meta: { rotulo: 'Placa', exportar: (v) => v.placaFormatada },
      cell: ({ row }) => (
        <div>
          <span className="font-mono font-medium text-texto">{row.original.placaFormatada}</span>
          {row.original.codigoInterno ? (
            <span className="block text-xs text-texto-suave">{row.original.codigoInterno}</span>
          ) : null}
        </div>
      ),
    },
    {
      id: 'modelo',
      header: 'Modelo',
      meta: { rotulo: 'Modelo', exportar: (v) => [v.modelo, v.fabricante].filter(Boolean).join(' · ') },
      cell: ({ row }) => (
        <div>
          <p className="text-texto">{row.original.modelo}</p>
          <p className="text-xs text-texto-suave">
            {[row.original.fabricante, row.original.anoFabricacao].filter(Boolean).join(' · ') || '—'}
          </p>
        </div>
      ),
    },
    {
      id: 'categoria',
      header: 'Categoria',
      meta: { rotulo: 'Categoria', exportar: (v) => v.categoriaDescricao },
      cell: ({ row }) => (
        <div className="flex flex-wrap items-center gap-1">
          <Badge variante="neutra">{row.original.categoriaDescricao}</Badge>
          {row.original.exigeTesteFumacaPreta ? (
            <Tooltip conteudo="Diesel: exige teste de fumaça preta na retirada (RN-09)" lado="top">
              <span>
                <Badge variante="atencao">
                  <Fuel className="size-3" aria-hidden="true" />
                  Diesel
                </Badge>
              </span>
            </Tooltip>
          ) : null}
        </div>
      ),
    },
    {
      id: 'locadora',
      header: 'Locadora',
      meta: { rotulo: 'Locadora', exportar: (v) => v.locadora.nome },
      cell: ({ row }) => (
        <div className="text-texto-suave">
          {row.original.locadora.nome}
          {row.original.grupoTarifario ? (
            <span className="block text-xs">Grupo {row.original.grupoTarifario}</span>
          ) : null}
        </div>
      ),
    },
    {
      id: 'equipamentos',
      header: 'Equipamentos',
      enableSorting: false,
      meta: {
        rotulo: 'Equipamentos',
        exportar: (v) =>
          [v.possuiRastreador ? 'Rastreador' : null, v.possuiAdesivo ? 'Adesivo' : null]
            .filter(Boolean)
            .join(' · '),
      },
      cell: ({ row }) => (
        <div className="flex flex-wrap gap-1">
          {row.original.possuiRastreador ? (
            <Badge variante="informativa">
              <Radio className="size-3" aria-hidden="true" />
              Rastreador
            </Badge>
          ) : null}
          {row.original.possuiAdesivo ? (
            <Badge variante="neutra">
              <Sticker className="size-3" aria-hidden="true" />
              Adesivo
            </Badge>
          ) : null}
          {!row.original.possuiRastreador && !row.original.possuiAdesivo ? (
            <span className="text-xs text-texto-tenue">—</span>
          ) : null}
        </div>
      ),
    },
    {
      id: 'status',
      header: 'Situação',
      meta: { rotulo: 'Situação', exportar: (v) => v.statusDescricao },
      cell: ({ row }) => (
        <Badge variante={row.original.status === 'DISPONIVEL' ? 'sucesso' : 'neutra'}>
          {row.original.statusDescricao}
        </Badge>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Veículos"
        descricao="Veículos alugados das locadoras. O vínculo com obra e condutor pertence ao contrato, não ao veículo — é o que permite responder quem dirigia cada placa em qualquer data."
        acao={
          podeEditar ? (
            <Button onClick={() => { definirCriando(true) }}>
              <Plus aria-hidden="true" />
              Novo veículo
            </Button>
          ) : undefined
        }
      />

      <BarraDeFiltros
        termo={termo}
        aoMudarTermo={(valor) => { definirTermo(valor); definirPagina(0) }}
        placeholder="Buscar por placa, modelo, fabricante ou código interno"
      >
        <Select
          value={categoria}
          onChange={(evento) => { definirCategoria(evento.target.value as CategoriaVeiculo | ''); definirPagina(0) }}
          className="w-44"
          aria-label="Filtrar por categoria"
        >
          <option value="">Todas as categorias</option>
          {Object.entries(CATEGORIAS_DE_VEICULO).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </Select>
        <Select
          value={status}
          onChange={(evento) => { definirStatus(evento.target.value as StatusVeiculo | ''); definirPagina(0) }}
          className="w-48"
          aria-label="Filtrar por situação"
        >
          <option value="">Todas as situações</option>
          {Object.entries(STATUS_DE_VEICULO).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </Select>
      </BarraDeFiltros>

      <TabelaDeDados
        legenda="Veículos cadastrados, com placa, modelo, categoria, locadora, equipamentos e situação"
        colunas={colunas}
        dados={veiculos}
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
        nomeDoArquivo="veiculos"
        aoExportar={async () => {
          const todos = exigirSucesso(
            await api.GET('/api/v1/veiculos', { params: { query: { ...filtros, page: 0, size: TUDO, sort } } }),
          )
          return todos.conteudo
        }}
        vazio={
          <EstadoVazio
            icone={<Car className="size-6" />}
            titulo={filtrando ? 'Nenhum veículo encontrado' : 'Nenhum veículo cadastrado'}
            descricao={
              filtrando
                ? 'Nenhum registro atende aos filtros aplicados. A busca por placa aceita qualquer grafia.'
                : 'Cadastre os veículos alugados para vinculá-los a contratos.'
            }
            acao={
              podeEditar && !filtrando ? (
                <Button variante="secundaria" tamanho="pequeno" onClick={() => { definirCriando(true) }}>
                  <Plus aria-hidden="true" />
                  Cadastrar veículo
                </Button>
              ) : undefined
            }
          />
        }
      />

      {emDetalhe ? (
        <DetalheDoRegistro
          titulo={emDetalhe.placaFormatada}
          tituloMonoespacado
          subtitulo={[emDetalhe.modelo, emDetalhe.fabricante, emDetalhe.locadora.nome]
            .filter(Boolean)
            .join(' · ')}
          selo={{
            texto: emDetalhe.statusDescricao,
            variante: emDetalhe.status === 'DISPONIVEL' ? 'sucesso' : 'neutra',
          }}
          aviso={
            emDetalhe.exigeTesteFumacaPreta ? (
              <div className="flex items-start gap-3 rounded-[var(--radius-base)] border border-atencao/30 bg-atencao-suave/40 px-3 py-2.5">
                <Fuel className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
                <p className="text-sm text-texto-suave">
                  Veículo a diesel: a retirada exige teste de fumaça preta na escala de
                  Ringelmann <span className="whitespace-nowrap">(RN-09)</span>.
                </p>
              </div>
            ) : undefined
          }
          secoes={[
            {
              titulo: 'Identificação',
              campos: [
                { rotulo: 'Placa', valor: emDetalhe.placaFormatada },
                { rotulo: 'Código interno', valor: emDetalhe.codigoInterno },
                { rotulo: 'Modelo', valor: emDetalhe.modelo },
                { rotulo: 'Fabricante', valor: emDetalhe.fabricante },
                { rotulo: 'Ano', valor: emDetalhe.anoFabricacao?.toString() },
                { rotulo: 'Combustível', valor: emDetalhe.combustivelDescricao },
              ],
            },
            {
              titulo: 'Locação',
              campos: [
                { rotulo: 'Locadora', valor: emDetalhe.locadora.nome },
                { rotulo: 'Grupo tarifário', valor: emDetalhe.grupoTarifario },
                { rotulo: 'Categoria', valor: emDetalhe.categoriaDescricao },
                { rotulo: 'Situação', valor: emDetalhe.statusDescricao },
              ],
            },
            {
              titulo: 'Equipamentos',
              campos: [
                {
                  rotulo: 'Rastreador',
                  valor: emDetalhe.possuiRastreador
                    ? (emDetalhe.fornecedorRastreador ?? 'Sim')
                    : 'Não',
                },
                { rotulo: 'Adesivo', valor: emDetalhe.possuiAdesivo ? 'Sim' : 'Não' },
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
        <FormularioDeVeiculo
          key={emEdicao?.id ?? 'novo'}
          veiculo={emEdicao}
          aoFechar={() => { definirCriando(false); definirEmEdicao(null) }}
        />
      ) : null}

      <DialogoDeExclusao
        aberto={emExclusao !== null}
        aoMudarAbertura={(aberto) => { if (!aberto) { definirEmExclusao(null) } }}
        titulo="Excluir veículo"
        descricao={`O veículo de placa ${emExclusao?.placaFormatada ?? ''} sairá das listagens. A placa volta a ficar disponível para novo cadastro.`}
        aoConfirmar={() => exclusao.mutateAsync(emExclusao?.id ?? 0)}
      />
    </div>
  )
}
