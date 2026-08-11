import type { components } from '@/lib/api/schema'

/** Tipos da operação mensal (Fase 3), derivados do OpenAPI. */
export type RegistroDeKm = components['schemas']['RegistroDeKmResponse']
export type Abastecimento = components['schemas']['AbastecimentoResponse']
export type ServicoOperacional = components['schemas']['ServicoResponse']
export type Fechamento = components['schemas']['FechamentoResponse']
export type Fatura = components['schemas']['FaturaResponse']
export type UsoParticular = components['schemas']['UsoParticularResponse']
export type Conformidade = components['schemas']['ConformidadeResponse']

/** Rótulos dos status de conferência de fatura (RN-13). */
export const STATUS_DE_FATURA: Record<string, string> = {
  PENDENTE: 'Pendente de conferência',
  OK: 'Conferida e correta',
  EM_CONTESTACAO: 'Em contestação',
  AJUSTADA: 'Ajustada após tratativa',
}

export const TIPOS_DE_SERVICO: Record<string, string> = {
  LAVA_JATO: 'Lava-jato',
  BORRACHARIA: 'Borracharia',
  PARA_BRISAS: 'Para-brisas',
}

/**
 * Competência do mês anterior, em `AAAA-MM`.
 *
 * O padrão da tela é o mês passado, não o corrente: a operação mensal se confere depois
 * de o mês fechar, e abrir no mês em curso mostraria um fechamento sempre incompleto.
 */
export function competenciaPadrao(): string {
  const hoje = new Date()
  const anterior = new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1)
  return `${String(anterior.getFullYear())}-${String(anterior.getMonth() + 1).padStart(2, '0')}`
}

/** Primeiro e último dia da competência, para os filtros de período dos lançamentos. */
export function limitesDaCompetencia(competencia: string): { inicio: string; fim: string } {
  const [ano, mes] = competencia.split('-').map(Number)
  const primeiro = new Date(ano ?? 2026, (mes ?? 1) - 1, 1)
  const ultimo = new Date(ano ?? 2026, mes ?? 1, 0)
  const iso = (data: Date) =>
    `${String(data.getFullYear())}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`
  return { inicio: iso(primeiro), fim: iso(ultimo) }
}
