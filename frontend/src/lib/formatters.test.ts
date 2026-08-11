import { describe, expect, it } from 'vitest'

import {
  formatarData,
  formatarMoeda,
  formatarPlaca,
  formatarQuilometragem,
  iniciaisDe,
  normalizarPlaca,
} from './formatters'

describe('formatação pt-BR', () => {
  describe('moeda', () => {
    it('formata valores em reais com duas casas', () => {
      // O `Intl` usa espaço não separável (U+00A0) depois de "R$" em algumas versões
      // do ICU e espaço comum em outras; normalizar evita um teste que quebra por
      // motivo alheio ao código.
      const semEspacoEspecial = (texto: string) => texto.replace(/\u00A0/g, ' ')

      expect(semEspacoEspecial(formatarMoeda(2606.08))).toBe('R$ 2.606,08')
      expect(semEspacoEspecial(formatarMoeda('0.6'))).toBe('R$ 0,60')
    })

    it('mostra travessão para valores ausentes', () => {
      expect(formatarMoeda(null)).toBe('—')
      expect(formatarMoeda(undefined)).toBe('—')
      expect(formatarMoeda('')).toBe('—')
      expect(formatarMoeda('não é número')).toBe('—')
    })
  })

  describe('quilometragem', () => {
    it('formata com separador de milhar e unidade', () => {
      expect(formatarQuilometragem(12480)).toBe('12.480 km')
      expect(formatarQuilometragem(null)).toBe('—')
    })
  })

  describe('data', () => {
    it('formata no fuso de exibição do sistema', () => {
      // 2026-03-15T02:00:00Z é ainda 14/03 em America/Recife (UTC-3).
      expect(formatarData('2026-03-15T02:00:00Z')).toBe('14/03/2026')
      expect(formatarData(null)).toBe('—')
      expect(formatarData('data inválida')).toBe('—')
    })
  })

  describe('placa (RN-02)', () => {
    it('normaliza para caixa alta sem separadores', () => {
      expect(normalizarPlaca('abc-1d23')).toBe('ABC1D23')
      expect(normalizarPlaca(' abc 1d23 ')).toBe('ABC1D23')
    })

    it('formata com hífen para exibição', () => {
      expect(formatarPlaca('ABC1D23')).toBe('ABC-1D23')
      expect(formatarPlaca('ABC1234')).toBe('ABC-1234')
      expect(formatarPlaca(null)).toBe('—')
    })
  })

  describe('iniciais', () => {
    it('usa a primeira e a última palavra do nome', () => {
      expect(iniciaisDe('Ana Souza')).toBe('AS')
      expect(iniciaisDe('Ana Maria Souza')).toBe('AS')
      expect(iniciaisDe('Ana')).toBe('A')
      expect(iniciaisDe(null)).toBe('?')
    })
  })
})
