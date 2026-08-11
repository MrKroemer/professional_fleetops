import { format, formatDistanceToNow, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'

/**
 * Formatação centralizada em pt-BR.
 *
 * Nenhuma tela formata data ou moeda por conta própria: divergências de formato
 * entre telas são exatamente o tipo de inconsistência que o sistema veio eliminar
 * das planilhas.
 */

/** Fuso de exibição do sistema (RN-22). O armazenamento é sempre em UTC. */
export const FUSO_EXIBICAO = 'America/Recife'

const moeda = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const numero = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })

const decimal = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dataHoraLocal = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO_EXIBICAO,
  dateStyle: 'short',
  timeStyle: 'short',
})

const dataLocal = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO_EXIBICAO,
  dateStyle: 'short',
})

/** Formata um valor monetário em reais. Valores ausentes viram travessão. */
export function formatarMoeda(valor: number | string | null | undefined): string {
  if (valor === null || valor === undefined || valor === '') return '—'
  const numerico = typeof valor === 'string' ? Number(valor) : valor
  if (!Number.isFinite(numerico)) return '—'
  return moeda.format(numerico)
}

/** Formata uma quantidade inteira, ex.: quilometragem. */
export function formatarNumero(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—'
  return numero.format(valor)
}

/** Formata um número com duas casas decimais, ex.: litros abastecidos. */
export function formatarDecimal(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—'
  return decimal.format(valor)
}

/** Formata quilometragem com a unidade, ex.: `12.480 km`. */
export function formatarQuilometragem(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—'
  return `${numero.format(valor)} km`
}

function paraData(valor: string | Date | null | undefined): Date | null {
  if (!valor) return null
  const data = typeof valor === 'string' ? parseISO(valor) : valor
  return Number.isNaN(data.getTime()) ? null : data
}

/** Formata um instante ISO-8601 como data e hora no fuso de exibição. */
export function formatarDataHora(valor: string | Date | null | undefined): string {
  const data = paraData(valor)
  return data ? dataHoraLocal.format(data) : '—'
}

/** Formata um instante ISO-8601 como data no fuso de exibição. */
export function formatarData(valor: string | Date | null | undefined): string {
  const data = paraData(valor)
  return data ? dataLocal.format(data) : '—'
}

/** Formata uma competência mensal, ex.: `março de 2026`. */
export function formatarCompetencia(valor: string | Date | null | undefined): string {
  const data = paraData(valor)
  return data ? format(data, "MMMM 'de' yyyy", { locale: ptBR }) : '—'
}

/** Descreve há quanto tempo algo aconteceu, ex.: `há 3 dias`. */
export function formatarTempoRelativo(valor: string | Date | null | undefined): string {
  const data = paraData(valor)
  return data ? formatDistanceToNow(data, { locale: ptBR, addSuffix: true }) : '—'
}

/** Normaliza uma placa para caixa alta sem espaços nem hífens (RN-02). */
export function normalizarPlaca(placa: string): string {
  return placa.toUpperCase().replace(/[\s-]/g, '')
}

/** Exibe uma placa no formato usual, ex.: `ABC-1D23`. */
export function formatarPlaca(placa: string | null | undefined): string {
  if (!placa) return '—'
  const limpa = normalizarPlaca(placa)
  return limpa.length === 7 ? `${limpa.slice(0, 3)}-${limpa.slice(3)}` : limpa
}

/** Extrai as iniciais de um nome, para avatares. */
export function iniciaisDe(nome: string | null | undefined): string {
  if (!nome) return '?'
  const partes = nome.trim().split(/\s+/).filter(Boolean)
  const primeira = partes[0]?.[0] ?? ''
  const ultima = partes.length > 1 ? (partes[partes.length - 1]?.[0] ?? '') : ''
  return (primeira + ultima).toUpperCase() || '?'
}
