import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, CircleUser, Plus, Trash2, XCircle } from 'lucide-react'
import { useState } from 'react'

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
import { Switch } from '@/components/ui/switch'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'
import { formatarData } from '@/lib/formatters'

/** Teto da RN-10, repetido aqui só para o campo não aceitar o que o servidor recusaria. */
const LIMITE_DE_KM = 1000

/**
 * Autorizações de uso particular de um contrato (RN-10).
 *
 * O aceite das regras aparece como estado do registro, não como caixinha escondida no
 * formulário: sem ele a autorização não vale, porque é o aceite que sustenta a
 * responsabilização do condutor por multas e custos do período.
 */
export function UsoParticularDoContrato({
  contratoId,
  podeEditar,
}: {
  contratoId: number
  podeEditar: boolean
}) {
  const clienteDeConsultas = useQueryClient()
  const [autorizando, definirAutorizando] = useState(false)

  const usos = useQuery({
    queryKey: ['operacao', 'uso-particular', contratoId],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/uso-particular', {
          params: { path: { contratoId } },
        }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(
        await api.DELETE('/api/v1/operacao/uso-particular/{id}', { params: { path: { id } } }),
      ),
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
    },
  })

  const lista = usos.data ?? []

  return (
    <section>
      <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-texto">
        <CircleUser className="size-4 text-marca-forte" aria-hidden="true" />
        Uso particular
        <span className="text-xs font-normal text-texto-tenue">({lista.length})</span>
        {podeEditar ? (
          <Button
            variante="sutil"
            tamanho="pequeno"
            className="ml-auto"
            onClick={() => definirAutorizando(true)}
          >
            <Plus aria-hidden="true" />
            Autorizar
          </Button>
        ) : null}
      </h3>

      {lista.length === 0 ? (
        <p className="rounded-[var(--radius-base)] border border-dashed border-borda px-3 py-2.5 text-sm text-texto-tenue">
          Nenhuma autorização de uso particular neste contrato.
        </p>
      ) : (
        <ul className="divide-y divide-borda rounded-[var(--radius-base)] border border-borda bg-superficie">
          {lista.map((uso) => (
            <li key={uso.id} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
              <span className="w-40 shrink-0 text-sm text-texto-suave">
                {formatarData(uso.inicio)} → {formatarData(uso.fim)}
              </span>
              <span className="min-w-0 flex-1 text-sm">
                <span className="text-texto">{uso.condutor}</span>
                <span className="text-texto-suave">
                  {' '}· {uso.tipoDescricao} · {uso.kmAutorizado} km autorizados
                  {uso.kmPercorrido != null ? ` · ${String(uso.kmPercorrido)} km rodados` : ''}
                </span>
                {uso.kmExcedido > 0 ? (
                  <span className="ml-1 text-critico">
                    ({uso.kmExcedido} km acima do autorizado)
                  </span>
                ) : null}
              </span>
              <span
                className={`inline-flex shrink-0 items-center gap-1.5 text-xs font-medium ${
                  uso.valida ? 'text-sucesso' : 'text-atencao'
                }`}
              >
                {uso.valida ? (
                  <CheckCircle2 className="size-3.5" aria-hidden="true" />
                ) : (
                  <XCircle className="size-3.5" aria-hidden="true" />
                )}
                {uso.valida ? 'regras aceitas' : 'sem aceite'}
              </span>
              {podeEditar ? (
                <button
                  type="button"
                  onClick={() => exclusao.mutate(uso.id)}
                  className="rounded p-1 text-texto-tenue transition-colors hover:text-critico"
                  aria-label={`Excluir a autorização de ${uso.condutor}`}
                >
                  <Trash2 className="size-3.5" aria-hidden="true" />
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {autorizando ? (
        <FormularioDeUso contratoId={contratoId} aoFechar={() => definirAutorizando(false)} />
      ) : null}
    </section>
  )
}

function FormularioDeUso({
  contratoId,
  aoFechar,
}: {
  contratoId: number
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [condutorId, definirCondutorId] = useState('')
  const [tipo, definirTipo] = useState('USO_PONTUAL')
  const [inicio, definirInicio] = useState('')
  const [fim, definirFim] = useState('')
  const [kmAutorizado, definirKm] = useState(String(LIMITE_DE_KM))
  const [aceitar, definirAceitar] = useState(false)

  const condutores = useQuery({
    queryKey: ['condutores', 'para-uso-particular'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/condutores', {
          params: { query: { status: 'ATIVO', page: 0, size: 200 } },
        }),
      ),
  })

  const envio = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/operacao/contratos/{contratoId}/uso-particular', {
          params: { path: { contratoId } },
          body: {
            condutorId: Number(condutorId),
            tipo,
            inicio,
            fim,
            kmAutorizado: Number(kmAutorizado) || LIMITE_DE_KM,
            aceitarRegras: aceitar,
          },
        }),
      ),
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
      aoFechar()
    },
  })

  return (
    <Dialog open onOpenChange={(aberto) => !aberto && aoFechar()}>
      <DialogConteudo className="sm:max-w-lg">
        <DialogCabecalho>
          <DialogTitulo>Autorizar uso particular</DialogTitulo>
          <DialogDescricao>
            Limite de {LIMITE_DE_KM} km por período e condução proibida após as 20:00.
            Infrações e custos no período são de responsabilidade do condutor (RN-10).
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
            <CampoDeFormulario id="condutor-do-uso" rotulo="Condutor" obrigatorio>
              <Select
                id="condutor-do-uso"
                value={condutorId}
                onChange={(evento) => definirCondutorId(evento.target.value)}
              >
                <option value="">Selecione…</option>
                {(condutores.data?.conteudo ?? []).map((condutor) => (
                  <option key={condutor.id} value={condutor.id}>
                    {condutor.nome}
                  </option>
                ))}
              </Select>
            </CampoDeFormulario>
            <CampoDeFormulario id="tipo-do-uso" rotulo="Tipo" obrigatorio>
              <Select
                id="tipo-do-uso"
                value={tipo}
                onChange={(evento) => definirTipo(evento.target.value)}
              >
                <option value="USO_PONTUAL">Uso particular pontual</option>
                <option value="FOLGA_RECORRENTE">Folga recorrente</option>
              </Select>
            </CampoDeFormulario>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <CampoDeFormulario id="inicio-do-uso" rotulo="Início" obrigatorio>
              <Input
                id="inicio-do-uso"
                type="date"
                value={inicio}
                onChange={(evento) => definirInicio(evento.target.value)}
              />
            </CampoDeFormulario>
            <CampoDeFormulario id="fim-do-uso" rotulo="Fim" obrigatorio>
              <Input
                id="fim-do-uso"
                type="date"
                value={fim}
                onChange={(evento) => definirFim(evento.target.value)}
              />
            </CampoDeFormulario>
            <CampoDeFormulario id="km-do-uso" rotulo="KM autorizados">
              <Input
                id="km-do-uso"
                type="number"
                min={1}
                max={LIMITE_DE_KM}
                value={kmAutorizado}
                onChange={(evento) => definirKm(evento.target.value)}
              />
            </CampoDeFormulario>
          </div>

          <div className="rounded-[var(--radius-base)] border border-borda px-3 py-2.5">
            <Switch
              rotulo="O condutor declarou conhecer e aceitar as regras"
              descricao="Sem o aceite, a autorização fica registrada mas não vale — é ele que sustenta a responsabilização do condutor no período."
              checked={aceitar}
              onChange={(evento) => definirAceitar(evento.target.checked)}
            />
          </div>
        </DialogCorpo>

        <DialogRodape>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button
            disabled={envio.isPending || !condutorId || !inicio || !fim}
            onClick={() => envio.mutate()}
          >
            {envio.isPending ? 'Autorizando…' : 'Autorizar'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
