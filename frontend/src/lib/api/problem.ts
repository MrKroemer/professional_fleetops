/**
 * Erros da API no formato RFC 7807 (Problem Details).
 *
 * O backend garante a extensão `codigo` — um identificador estável do erro de
 * negócio. É por ele que a interface decide o que fazer, nunca pelo texto da
 * mensagem, que pode ser reescrito sem quebrar contrato.
 */

/** Violação de um campo específico da requisição. */
export interface ViolacaoCampo {
  campo: string
  mensagem: string
}

/** Corpo de erro devolvido pela API. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  codigo?: string
  timestamp?: string
  requestId?: string
  contexto?: Record<string, unknown>
  erros?: ViolacaoCampo[]
}

/** Erro de chamada à API, com o corpo RFC 7807 quando disponível. */
export class ErroDaApi extends Error {
  readonly status: number
  readonly codigo: string
  readonly requestId: string | undefined
  readonly violacoes: ViolacaoCampo[]

  constructor(status: number, problema: ProblemDetail | null) {
    super(problema?.detail ?? mensagemPadraoPara(status))
    this.name = 'ErroDaApi'
    this.status = status
    this.codigo = problema?.codigo ?? `HTTP-${status}`
    this.requestId = problema?.requestId
    this.violacoes = problema?.erros ?? []
  }

  /** Sessão ausente, expirada ou revogada. */
  get eNaoAutenticado(): boolean {
    return this.status === 401
  }

  /** Perfil sem permissão para a operação (RN-19). */
  get eAcessoNegado(): boolean {
    return this.status === 403
  }

  /** Falha temporária: vale a pena oferecer "tentar novamente". */
  get eRecuperavel(): boolean {
    return this.status === 0 || this.status >= 500
  }
}

function mensagemPadraoPara(status: number): string {
  if (status === 0) return 'Não foi possível falar com o servidor. Verifique sua conexão.'
  if (status === 401) return 'Sessão expirada. Entre novamente.'
  if (status === 403) return 'Seu perfil não permite executar esta operação.'
  if (status === 404) return 'Registro não encontrado.'
  if (status >= 500) return 'O servidor encontrou um erro. Tente novamente em instantes.'
  return 'Não foi possível concluir a operação.'
}

/** Converte um corpo desconhecido em {@link ProblemDetail}, sem confiar no formato. */
export function comoProblemDetail(corpo: unknown): ProblemDetail | null {
  if (typeof corpo !== 'object' || corpo === null) return null
  return corpo
}

/** Mensagem pronta para exibição a partir de qualquer erro capturado. */
export function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ErroDaApi) return erro.message
  if (erro instanceof Error) return erro.message
  return 'Ocorreu um erro inesperado.'
}
