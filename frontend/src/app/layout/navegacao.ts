import {
  BadgeCheck,
  Building2,
  Car,
  ClipboardCheck,
  FileSpreadsheet,
  Fuel,
  Handshake,
  IdCard,
  LayoutDashboard,
  Settings,
  Store,
  Table2,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { Perfil } from '@/features/auth/tipos'

/**
 * Estrutura de navegação, agrupada pelas áreas do sistema.
 *
 * Itens marcados como `disponivelEm` de fase futura aparecem desabilitados em vez
 * de omitidos: o gestor enxerga desde já o mapa completo do produto e entende que
 * a área existe, mas ainda não foi entregue.
 */

export interface ItemDeNavegacao {
  rotulo: string
  para: string
  icone: LucideIcon
  /** Perfis que enxergam o item. Ausente significa todos. */
  perfis?: Perfil[]
  /** Fase de entrega em que o item passa a funcionar. */
  fase: number
}

export interface GrupoDeNavegacao {
  titulo: string
  itens: ItemDeNavegacao[]
}

/** Fase atualmente entregue. Itens de fases posteriores ficam desabilitados. */
export const FASE_ATUAL = 3

export const NAVEGACAO: GrupoDeNavegacao[] = [
  {
    titulo: 'Visão geral',
    itens: [{ rotulo: 'Dashboard', para: '/', icone: LayoutDashboard, fase: 0 }],
  },
  {
    titulo: 'Frota',
    itens: [
      { rotulo: 'Contratos', para: '/contratos', icone: FileSpreadsheet, fase: 2 },
      { rotulo: 'Operação mensal', para: '/operacao', icone: Fuel, fase: 3 },
      { rotulo: 'Conformidade', para: '/conformidade', icone: ClipboardCheck, fase: 4 },
    ],
  },
  {
    titulo: 'Cadastros',
    itens: [
      { rotulo: 'Obras', para: '/cadastros/obras', icone: Building2, fase: 1 },
      { rotulo: 'Locadoras', para: '/cadastros/locadoras', icone: Handshake, fase: 1 },
      { rotulo: 'Condutores', para: '/cadastros/condutores', icone: IdCard, fase: 1 },
      { rotulo: 'Veículos', para: '/cadastros/veiculos', icone: Car, fase: 1 },
      { rotulo: 'Fornecedores', para: '/cadastros/fornecedores', icone: Store, fase: 1 },
      { rotulo: 'Tabelas de preço', para: '/cadastros/tabelas-preco', icone: Table2, fase: 1 },
    ],
  },
  {
    titulo: 'Relatórios',
    itens: [{ rotulo: 'Custos e exportações', para: '/relatorios', icone: BadgeCheck, fase: 5 }],
  },
  {
    titulo: 'Administração',
    itens: [
      { rotulo: 'Usuários', para: '/usuarios', icone: Users, perfis: ['ADMIN'], fase: 0 },
      { rotulo: 'Configurações', para: '/configuracoes', icone: Settings, perfis: ['ADMIN'], fase: 4 },
    ],
  },
]

/** Indica se o item já está entregue e navegável. */
export function estaDisponivel(item: ItemDeNavegacao): boolean {
  return item.fase <= FASE_ATUAL
}

/** Filtra a navegação pelos perfis do usuário. */
export function navegacaoPara(perfil: Perfil | undefined): GrupoDeNavegacao[] {
  if (!perfil) return []
  return NAVEGACAO.map((grupo) => ({
    ...grupo,
    itens: grupo.itens.filter((item) => !item.perfis || item.perfis.includes(perfil)),
  })).filter((grupo) => grupo.itens.length > 0)
}
