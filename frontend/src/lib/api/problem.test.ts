import { describe, expect, it } from 'vitest'

import { comoProblemDetail, ErroDaApi, mensagemDeErro } from './problem'

describe('ErroDaApi — contrato de erro RFC 7807', () => {
  it('preserva o código de negócio estável devolvido pelo backend', () => {
    const erro = new ErroDaApi(409, {
      codigo: 'RN-002-PLACA_DUPLICADA',
      detail: 'A placa ABC-1D23 já está cadastrada em outro veículo.',
      requestId: 'abc-123',
    })

    expect(erro.codigo).toBe('RN-002-PLACA_DUPLICADA')
    expect(erro.message).toContain('ABC-1D23')
    expect(erro.requestId).toBe('abc-123')
    expect(erro.status).toBe(409)
  })

  it('classifica falhas de sessão e de permissão', () => {
    expect(new ErroDaApi(401, null).eNaoAutenticado).toBe(true)
    expect(new ErroDaApi(403, null).eAcessoNegado).toBe(true)
    expect(new ErroDaApi(403, null).eNaoAutenticado).toBe(false)
  })

  it('só considera recuperável o que faz sentido repetir', () => {
    // Repetir um 409 ou um 400 daria exatamente o mesmo resultado.
    expect(new ErroDaApi(0, null).eRecuperavel).toBe(true)
    expect(new ErroDaApi(503, null).eRecuperavel).toBe(true)
    expect(new ErroDaApi(409, null).eRecuperavel).toBe(false)
    expect(new ErroDaApi(400, null).eRecuperavel).toBe(false)
  })

  it('usa uma mensagem padrão em pt-BR quando o corpo não traz detalhe', () => {
    expect(new ErroDaApi(0, null).message).toContain('conexão')
    expect(new ErroDaApi(404, null).message).toContain('não encontrado')
    expect(new ErroDaApi(500, null).message).toContain('servidor')
  })

  it('expõe as violações de campo para exibição junto aos inputs', () => {
    const erro = new ErroDaApi(400, {
      codigo: 'GEN-002-VALIDACAO',
      erros: [{ campo: 'email', mensagem: 'Informe um e-mail válido' }],
    })

    expect(erro.violacoes).toHaveLength(1)
    expect(erro.violacoes[0]?.campo).toBe('email')
  })
})

describe('comoProblemDetail', () => {
  it('rejeita corpos que não são objeto', () => {
    expect(comoProblemDetail('erro')).toBeNull()
    expect(comoProblemDetail(null)).toBeNull()
    expect(comoProblemDetail(42)).toBeNull()
  })

  it('aceita um objeto como corpo de problema', () => {
    expect(comoProblemDetail({ codigo: 'X' })).toEqual({ codigo: 'X' })
  })
})

describe('mensagemDeErro', () => {
  it('extrai a mensagem de qualquer erro capturado', () => {
    expect(mensagemDeErro(new ErroDaApi(404, { detail: 'Obra não encontrada.' }))).toBe(
      'Obra não encontrada.',
    )
    expect(mensagemDeErro(new Error('falha genérica'))).toBe('falha genérica')
    expect(mensagemDeErro('texto solto')).toBe('Ocorreu um erro inesperado.')
  })
})
