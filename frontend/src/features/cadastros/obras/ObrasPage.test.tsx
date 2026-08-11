import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ObrasPage } from './ObrasPage'
import { ErroDaApi } from '@/lib/api/problem'
import { renderizarComProvedores } from '@/testes/utilitarios'

/**
 * O cliente da API é substituído por completo: o objetivo aqui é verificar o
 * comportamento da tela, não a comunicação HTTP — essa já é coberta pelos testes de
 * integração do backend.
 */
const consultar = vi.fn()

vi.mock('@/lib/api/client', () => ({
  api: {
    GET: (...args: unknown[]) => consultar(...args) as unknown,
    POST: vi.fn(),
    PUT: vi.fn(),
    DELETE: vi.fn(),
  },
  // O duplo reproduz o contrato do `exigirSucesso` real: devolve o dado ou lança.
  exigirSucesso: <T,>(resultado: { data?: T; erro?: Error }) => {
    if (resultado.erro) {
      throw resultado.erro
    }
    return resultado.data
  },
}))

function paginaCom(obras: unknown[]) {
  return {
    data: {
      conteudo: obras,
      pagina: 0,
      tamanho: 20,
      totalElementos: obras.length,
      totalPaginas: 1,
      primeira: true,
      ultima: true,
    },
  }
}

const OBRA = {
  id: 1,
  codigo: '24.019',
  nome: 'SKER Ventos de Santa Eugênia',
  cliente: 'SKER',
  cidade: 'Uibaí',
  uf: 'BA',
  status: 'ATIVA',
  statusDescricao: 'Ativa',
  dataInicio: '2024-01-15',
  criadoEm: '2026-01-01T00:00:00Z',
  atualizadoEm: '2026-01-01T00:00:00Z',
}

describe('ObrasPage', () => {
  beforeEach(() => {
    consultar.mockReset()
  })

  it('mostra o estado de carregamento antes da resposta', () => {
    consultar.mockReturnValue(new Promise(() => {
      /* nunca resolve: mantém a tela em carregamento */
    }))

    renderizarComProvedores(<ObrasPage />)

    expect(screen.getByRole('status', { name: /carregando/i })).toBeInTheDocument()
  })

  it('lista as obras retornadas', async () => {
    consultar.mockResolvedValue(paginaCom([OBRA]))

    renderizarComProvedores(<ObrasPage />)

    expect(await screen.findByText('SKER Ventos de Santa Eugênia')).toBeInTheDocument()
    expect(screen.getByText('24.019')).toBeInTheDocument()
    expect(screen.getByText('Uibaí — BA')).toBeInTheDocument()
    // "Ativa" também aparece no filtro de situação; a busca é escopada à tabela.
    expect(within(screen.getByRole('table')).getByText('Ativa')).toBeInTheDocument()
  })

  it('mostra o estado vazio com ação quando não há obras', async () => {
    consultar.mockResolvedValue(paginaCom([]))

    renderizarComProvedores(<ObrasPage />)

    expect(await screen.findByText('Nenhuma obra cadastrada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cadastrar obra/i })).toBeInTheDocument()
  })

  it('mostra o estado de erro com opção de tentar novamente', async () => {
    consultar.mockRejectedValue(new ErroDaApi(503, { detail: 'Serviço indisponível.' }))

    renderizarComProvedores(<ObrasPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Serviço indisponível.')
    expect(screen.getByRole('button', { name: /tentar novamente/i })).toBeInTheDocument()
  })

  it('RN19_naoOfereceAcoesDeEscritaAoPerfilDeConsulta', async () => {
    const usuario = userEvent.setup()
    consultar.mockResolvedValue(paginaCom([OBRA]))

    renderizarComProvedores(<ObrasPage />, { perfil: 'CONSULTA' })

    await screen.findByText('SKER Ventos de Santa Eugênia')
    expect(screen.queryByRole('button', { name: /nova obra/i })).not.toBeInTheDocument()

    // O detalhe continua acessível — só as ações de escrita somem dele.
    await usuario.click(screen.getByText('SKER Ventos de Santa Eugênia'))
    const painel = await screen.findByRole('dialog')
    expect(within(painel).queryByRole('button', { name: /^editar$/i })).not.toBeInTheDocument()
    expect(within(painel).queryByRole('button', { name: /^excluir$/i })).not.toBeInTheDocument()
  })

  it('RN19_ofereceAcoesDeEscritaAoGestorDeFrota', async () => {
    const usuario = userEvent.setup()
    consultar.mockResolvedValue(paginaCom([OBRA]))

    renderizarComProvedores(<ObrasPage />, { perfil: 'GESTOR_FROTA' })

    await screen.findByText('SKER Ventos de Santa Eugênia')
    expect(screen.getByRole('button', { name: /nova obra/i })).toBeInTheDocument()

    await usuario.click(screen.getByText('SKER Ventos de Santa Eugênia'))
    const painel = await screen.findByRole('dialog')
    expect(within(painel).getByRole('button', { name: /^editar$/i })).toBeInTheDocument()
    expect(within(painel).getByRole('button', { name: /^excluir$/i })).toBeInTheDocument()
  })

  it('clicar na linha abre o detalhe do registro', async () => {
    const usuario = userEvent.setup()
    consultar.mockResolvedValue(paginaCom([OBRA]))

    renderizarComProvedores(<ObrasPage />)
    await usuario.click(await screen.findByText('SKER Ventos de Santa Eugênia'))

    const painel = await screen.findByRole('dialog')
    // O detalhe mostra o que não cabe na tabela.
    expect(within(painel).getByText('Uibaí')).toBeInTheDocument()
    expect(within(painel).getByText('Identificação')).toBeInTheDocument()
  })

  it('a tabela oferece densidade, colunas e exportação (Seção 6.1)', async () => {
    consultar.mockResolvedValue(paginaCom([OBRA]))

    renderizarComProvedores(<ObrasPage />)
    await screen.findByText('SKER Ventos de Santa Eugênia')

    expect(screen.getByRole('button', { name: /densidade/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /colunas/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /exportar csv/i })).toBeInTheDocument()
  })

  it('a tabela tem legenda acessível descrevendo o conteúdo', async () => {
    consultar.mockResolvedValue(paginaCom([OBRA]))

    renderizarComProvedores(<ObrasPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toHaveAccessibleName(/obras cadastradas/i)
    })
  })
})
