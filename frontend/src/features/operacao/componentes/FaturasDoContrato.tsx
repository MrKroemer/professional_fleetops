import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, FileText, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'

import { STATUS_DE_FATURA, type Fatura } from '../tipos'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { CampoDeFormulario } from '@/components/ui/campo-de-formulario'
import {
  Dialog,
  DialogCabecalho,
  DialogConteudo,
  DialogCorpo,
  DialogDescricao,
  DialogRodape,
  DialogTitulo,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'
import { formatarData, formatarMoeda } from '@/lib/formatters'

/**
 * Faturas mensais da locadora de um contrato (RN-13).
 *
 * A divergência é calculada pelo servidor e mostrada em destaque, com o sinal preservado:
 * cobrança a mais e a menos são problemas diferentes e levam a conversas diferentes com a
 * locadora. Anular o sinal esconderia qual dos dois é.
 *
 * O formulário deixa a observação sempre visível quando há divergência, porque é o que a
 * RN-13 vai exigir na hora de concluir a tratativa — e escondê-la atrás de uma condição
 * faria o campo aparecer só depois de o envio falhar.
 */
export function FaturasDoContrato({
  contratoId,
  competencia,
  podeEditar,
}: {
  contratoId: number
  competencia: string
  podeEditar: boolean
}) {
  const clienteDeConsultas = useQueryClient()
  const [editando, definirEditando] = useState<number | 'nova' | null>(null)

  const faturas = useQuery({
    queryKey: ['operacao', 'faturas', contratoId],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/faturas', {
          params: { path: { contratoId } },
        }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(
        await api.DELETE('/api/v1/operacao/faturas/{id}', { params: { path: { id } } }),
      ),
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
    },
  })

  const lista = faturas.data ?? []
  const emEdicao = typeof editando === 'number' ? lista.find((f) => f.id === editando) : undefined

  return (
    <section>
      <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-texto">
        <FileText className="size-4 text-marca-forte" aria-hidden="true" />
        Faturas da locadora
        <span className="text-xs font-normal text-texto-tenue">({lista.length})</span>
        {podeEditar ? (
          <Button
            variante="sutil"
            tamanho="pequeno"
            className="ml-auto"
            onClick={() => definirEditando('nova')}
          >
            <Plus aria-hidden="true" />
            Lançar fatura
          </Button>
        ) : null}
      </h3>

      {lista.length === 0 ? (
        <p className="rounded-[var(--radius-base)] border border-dashed border-borda px-3 py-2.5 text-sm text-texto-tenue">
          Nenhuma fatura lançada para este contrato.
        </p>
      ) : (
        <ul className="divide-y divide-borda rounded-[var(--radius-base)] border border-borda bg-superficie">
          {lista.map((fatura) => {
            const divergencia = fatura.divergencia ?? 0
            return (
              <li key={fatura.id} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
                <span className="w-20 shrink-0 text-sm font-medium text-texto">
                  {fatura.competencia}
                </span>
                <span className="min-w-0 flex-1 text-sm text-texto-suave">
                  contratado {formatarMoeda(fatura.valorContratado)} · faturado{' '}
                  {formatarMoeda(fatura.valorFaturado)}
                  {fatura.extrasAprovados && fatura.extrasAprovados > 0
                    ? ` · extras ${formatarMoeda(fatura.extrasAprovados)}`
                    : ''}
                  {fatura.numeroDaNota ? ` · ${fatura.numeroDaNota}` : ''}
                  {fatura.vencimento ? ` · vence ${formatarData(fatura.vencimento)}` : ''}
                </span>
                {divergencia !== 0 ? (
                  <span
                    className={`shrink-0 text-sm font-semibold ${
                      divergencia > 0 ? 'text-critico' : 'text-informativo'
                    }`}
                    title={divergencia > 0 ? 'Cobrado a mais' : 'Cobrado a menos'}
                  >
                    {divergencia > 0 ? '+' : ''}
                    {formatarMoeda(divergencia)}
                  </span>
                ) : (
                  <span className="shrink-0 text-sm text-sucesso">sem divergência</span>
                )}
                <Badge
                  variante={
                    fatura.status === 'OK'
                      ? 'sucesso'
                      : fatura.exigeTratativa
                        ? 'atencao'
                        : 'neutra'
                  }
                >
                  {STATUS_DE_FATURA[fatura.status] ?? fatura.statusDescricao}
                </Badge>
                {podeEditar ? (
                  <>
                    <Button
                      variante="sutil"
                      tamanho="pequeno"
                      onClick={() => definirEditando(fatura.id)}
                    >
                      Conferir
                    </Button>
                    <button
                      type="button"
                      onClick={() => exclusao.mutate(fatura.id)}
                      className="rounded p-1 text-texto-tenue transition-colors hover:text-critico"
                      aria-label={`Excluir a fatura de ${fatura.competencia}`}
                    >
                      <Trash2 className="size-3.5" aria-hidden="true" />
                    </button>
                  </>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}

      {editando != null ? (
        <FormularioDeFatura
          contratoId={contratoId}
          competenciaPadrao={competencia}
          fatura={emEdicao}
          aoFechar={() => definirEditando(null)}
        />
      ) : null}
    </section>
  )
}

function FormularioDeFatura({
  contratoId,
  competenciaPadrao,
  fatura,
  aoFechar,
}: {
  contratoId: number
  competenciaPadrao: string
  fatura: Fatura | undefined
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [competencia, definirCompetencia] = useState(fatura?.competencia ?? competenciaPadrao)
  const [contratado, definirContratado] = useState(String(fatura?.valorContratado ?? ''))
  const [faturado, definirFaturado] = useState(String(fatura?.valorFaturado ?? ''))
  const [extras, definirExtras] = useState(String(fatura?.extrasAprovados ?? '0'))
  const [nota, definirNota] = useState(fatura?.numeroDaNota ?? '')
  const [vencimento, definirVencimento] = useState(fatura?.vencimento ?? '')
  const [status, definirStatus] = useState(fatura?.status ?? 'PENDENTE')
  const [observacoes, definirObservacoes] = useState(fatura?.observacoes ?? '')

  // A divergência é reproduzida aqui só para antecipar o que a RN-13 vai cobrar; a que
  // vale é a coluna gerada no banco.
  const divergencia =
    (Number(faturado) || 0) - ((Number(contratado) || 0) + (Number(extras) || 0))

  const envio = useMutation({
    mutationFn: async () => {
      const corpo = {
        competencia,
        valorContratado: Number(contratado) || 0,
        valorFaturado: Number(faturado) || 0,
        extrasAprovados: Number(extras) || 0,
        numeroDaNota: nota || undefined,
        vencimento: vencimento || undefined,
        status,
        observacoes: observacoes || undefined,
      }
      return fatura
        ? exigirSucesso(
            await api.PUT('/api/v1/operacao/faturas/{id}', {
              params: { path: { id: fatura.id } },
              body: corpo,
            }),
          )
        : exigirSucesso(
            await api.POST('/api/v1/operacao/contratos/{contratoId}/faturas', {
              params: { path: { contratoId } },
              body: corpo,
            }),
          )
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
      aoFechar()
    },
  })

  return (
    <Dialog open onOpenChange={(aberto) => !aberto && aoFechar()}>
      <DialogConteudo className="sm:max-w-lg">
        <DialogCabecalho>
          <DialogTitulo>{fatura ? 'Conferir fatura' : 'Lançar fatura'}</DialogTitulo>
          <DialogDescricao>
            A divergência é calculada como faturado − (contratado + extras). Com divergência,
            a fatura não pode ser marcada como conferida (RN-13).
          </DialogDescricao>
        </DialogCabecalho>

        <DialogCorpo className="space-y-4">
          {envio.isError ? (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/50 px-3 py-2.5"
            >
              <AlertCircle className="mt-0.5 size-4 shrink-0 text-critico" aria-hidden="true" />
              <p className="text-sm text-texto">{mensagemDeErro(envio.error)}</p>
            </div>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <CampoDeFormulario id="competencia-da-fatura" rotulo="Competência" obrigatorio>
              <Input
                id="competencia-da-fatura"
                type="month"
                value={competencia}
                onChange={(evento) => definirCompetencia(evento.target.value)}
                disabled={Boolean(fatura)}
              />
            </CampoDeFormulario>
            <CampoDeFormulario id="nota" rotulo="Número da nota">
              <Input
                id="nota"
                value={nota}
                onChange={(evento) => definirNota(evento.target.value)}
                maxLength={60}
              />
            </CampoDeFormulario>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <CampoDeFormulario id="contratado" rotulo="Contratado (R$)">
              <Input
                id="contratado"
                type="number"
                step="0.01"
                min={0}
                value={contratado}
                onChange={(evento) => definirContratado(evento.target.value)}
              />
            </CampoDeFormulario>
            <CampoDeFormulario id="faturado" rotulo="Faturado (R$)">
              <Input
                id="faturado"
                type="number"
                step="0.01"
                min={0}
                value={faturado}
                onChange={(evento) => definirFaturado(evento.target.value)}
              />
            </CampoDeFormulario>
            <CampoDeFormulario id="extras" rotulo="Extras aprovados (R$)">
              <Input
                id="extras"
                type="number"
                step="0.01"
                min={0}
                value={extras}
                onChange={(evento) => definirExtras(evento.target.value)}
              />
            </CampoDeFormulario>
          </div>

          <p
            aria-live="polite"
            className={`rounded-[var(--radius-base)] px-3 py-2.5 text-sm ${
              divergencia === 0 ? 'bg-sucesso-suave/40 text-texto' : 'bg-atencao-suave/50 text-texto'
            }`}
          >
            Divergência: <strong>{formatarMoeda(divergencia)}</strong>
            {divergencia !== 0
              ? divergencia > 0
                ? ' — a locadora cobrou mais do que o contratado.'
                : ' — a locadora cobrou menos do que o contratado.'
              : ' — os valores fecham.'}
          </p>

          <div className="grid gap-4 sm:grid-cols-2">
            <CampoDeFormulario id="status-da-fatura" rotulo="Conferência">
              <Select
                id="status-da-fatura"
                value={status}
                onChange={(evento) => definirStatus(evento.target.value)}
              >
                {Object.entries(STATUS_DE_FATURA).map(([valor, rotulo]) => (
                  <option key={valor} value={valor} disabled={valor === 'OK' && divergencia !== 0}>
                    {rotulo}
                    {valor === 'OK' && divergencia !== 0 ? ' (bloqueado pela divergência)' : ''}
                  </option>
                ))}
              </Select>
            </CampoDeFormulario>
            <CampoDeFormulario id="vencimento" rotulo="Vencimento">
              <Input
                id="vencimento"
                type="date"
                value={vencimento}
                onChange={(evento) => definirVencimento(evento.target.value)}
              />
            </CampoDeFormulario>
          </div>

          <CampoDeFormulario
            id="observacoes-da-fatura"
            rotulo="Observações"
            obrigatorio={divergencia !== 0 && status !== 'PENDENTE'}
            dica="Obrigatória ao concluir a tratativa de uma fatura divergente."
          >
            <Textarea
              id="observacoes-da-fatura"
              rows={2}
              value={observacoes}
              onChange={(evento) => definirObservacoes(evento.target.value)}
            />
          </CampoDeFormulario>
        </DialogCorpo>

        <DialogRodape>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button disabled={envio.isPending || competencia === ''} onClick={() => envio.mutate()}>
            {envio.isPending ? 'Salvando…' : 'Salvar'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
