import type { components } from '@/lib/api/schema'

/**
 * Tipos do ciclo de vida do contrato (Fase 2).
 *
 * Derivados do OpenAPI, nunca escritos à mão: uma mudança no backend que o frontend não
 * acompanhou tem de quebrar a compilação, e não aparecer como `undefined` em produção.
 */
export type ContratoResumo = components['schemas']['ContratoResumoResponse']
export type LinhaDoTempo = components['schemas']['LinhaDoTempoResponse']
export type Marco = components['schemas']['MarcoResponse']
export type Evento = components['schemas']['EventoResponse']
export type Foto = components['schemas']['FotoResponse']
export type ItemPendente = components['schemas']['ItemPendenteResponse']
export type SituacaoNaData = components['schemas']['SituacaoNaDataResponse']
export type VerificacaoDeDevolucao = components['schemas']['VerificacaoDeDevolucaoResponse']
export type SituacaoDaFumaca = components['schemas']['SituacaoDaFumacaResponse']
export type TesteDeFumaca = components['schemas']['TesteDeFumacaResponse']
export type StatusContrato = ContratoResumo['status']

/** Rótulos dos status, para filtros e legendas. */
export const STATUS_DE_CONTRATO: Record<string, string> = {
  ATIVO: 'Ativo',
  DESMOBILIZADO: 'Desmobilizado',
  DEVOLVIDO: 'Devolvido',
  INATIVO: 'Inativo',
}

/**
 * Aparência de cada tipo de marco na linha do tempo.
 *
 * Cor e forma juntas, nunca cor sozinha: a linha do tempo distingue quatro naturezas de
 * evento, e quem não separa matiz precisa do ícone para lê-la.
 */
export const APARENCIA_DO_MARCO: Record<string, { cor: string; anel: string }> = {
  VEICULO_INICIAL: { cor: 'text-marca-forte', anel: 'bg-marca-suave ring-marca/30' },
  SUBSTITUICAO_VEICULO: { cor: 'text-marca-forte', anel: 'bg-marca-suave ring-marca/30' },
  CONDUTOR_INICIAL: { cor: 'text-informativo', anel: 'bg-informativo-suave ring-informativo/30' },
  TROCA_CONDUTOR: { cor: 'text-informativo', anel: 'bg-informativo-suave ring-informativo/30' },
  EVENTO_RETIRADA: { cor: 'text-sucesso', anel: 'bg-sucesso-suave ring-sucesso/30' },
  EVENTO_DEVOLUCAO: { cor: 'text-atencao', anel: 'bg-atencao-suave ring-atencao/30' },
}
