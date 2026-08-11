import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { RevelarCredenciais } from './RevelarCredenciais'
import { ErroDaApi } from '@/lib/api/problem'
import { renderizarComProvedores } from '@/testes/utilitarios'

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

describe('RN-20 — revelação de credenciais', () => {
  beforeEach(() => {
    consultar.mockReset()
  })

  it('RN20_naoBuscaACredencialAntesDoPedidoExplicito', async () => {
    const usuario = userEvent.setup()
    consultar.mockResolvedValue({ data: { login: 'proyfebrasil', senha: '123@abc' } })

    renderizarComProvedores(<RevelarCredenciais origem="locadora" id={1} nome="Unidas" />)

    // Abrir o diálogo não pode, sozinho, trazer o segredo para o navegador.
    await usuario.click(screen.getByRole('button', { name: /revelar credenciais de unidas/i }))

    expect(consultar).not.toHaveBeenCalled()
    expect(screen.queryByText('123@abc')).not.toBeInTheDocument()
    expect(screen.getByText(/permanecem cifradas no banco/i)).toBeInTheDocument()
  })

  it('RN20_avisaQueOAcessoERegistrado', async () => {
    const usuario = userEvent.setup()

    renderizarComProvedores(<RevelarCredenciais origem="locadora" id={1} nome="Unidas" />)
    await usuario.click(screen.getByRole('button', { name: /revelar credenciais de unidas/i }))

    expect(screen.getByText(/registrado com o seu usuário/i)).toBeInTheDocument()
  })

  it('RN20_revelaOValorApenasAposAAcaoDoUsuario', async () => {
    const usuario = userEvent.setup()
    consultar.mockResolvedValue({ data: { login: 'proyfebrasil', senha: '123@abc' } })

    renderizarComProvedores(<RevelarCredenciais origem="locadora" id={7} nome="Unidas" />)
    await usuario.click(screen.getByRole('button', { name: /revelar credenciais de unidas/i }))
    await usuario.click(screen.getByRole('button', { name: /^revelar$/i }))

    expect(await screen.findByText('123@abc')).toBeInTheDocument()
    expect(screen.getByText('proyfebrasil')).toBeInTheDocument()
    expect(consultar).toHaveBeenCalledWith('/api/v1/locadoras/{id}/credenciais', {
      params: { path: { id: 7 } },
    })
  })

  it('usa a rota de fornecedor quando a origem é um rastreador', async () => {
    const usuario = userEvent.setup()
    consultar.mockResolvedValue({ data: { login: 'proyfebrasil', senha: '123@abc' } })

    renderizarComProvedores(<RevelarCredenciais origem="fornecedor" id={3} nome="Recife GPS" />)
    await usuario.click(screen.getByRole('button', { name: /revelar credenciais de recife gps/i }))
    await usuario.click(screen.getByRole('button', { name: /^revelar$/i }))

    await screen.findByText('123@abc')
    expect(consultar).toHaveBeenCalledWith('/api/v1/fornecedores/{id}/credenciais', {
      params: { path: { id: 3 } },
    })
  })

  it('mostra a mensagem do servidor quando não há credencial cadastrada', async () => {
    const usuario = userEvent.setup()
    consultar.mockRejectedValue(
      new ErroDaApi(404, {
        codigo: 'RN-020-CREDENCIAL_INDISPONIVEL',
        detail: 'Esta locadora não tem credenciais de portal cadastradas.',
      }),
    )

    renderizarComProvedores(<RevelarCredenciais origem="locadora" id={1} nome="SpeedWay" />)
    await usuario.click(screen.getByRole('button', { name: /revelar credenciais de speedway/i }))
    await usuario.click(screen.getByRole('button', { name: /^revelar$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esta locadora não tem credenciais de portal cadastradas.',
    )
  })
})
