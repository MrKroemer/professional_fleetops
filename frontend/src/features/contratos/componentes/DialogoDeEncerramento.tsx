import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2 } from 'lucide-react'
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
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'

interface Props {
  contratoId: number
  aberto: boolean
  aoFechar: () => void
}

/**
 * Encerramento do contrato (RN-17).
 *
 * Os dois destinos não são equivalentes, e a tela precisa dizer isso antes do clique.
 * **Desmobilizado** é a obra devolvendo o carro — decisão interna, nada a verificar.
 * **Devolvido** encerra a relação com a locadora, e é o que a RN-17 condiciona.
 *
 * As pendências são consultadas ao abrir, não descobertas no erro. Quando existe alguma,
 * a opção de devolver aparece desabilitada com o motivo ao lado — que é exatamente o que
 * a RN-17 manda fazer: permitir apenas a desmobilização, com a pendência à vista.
 */
export function DialogoDeEncerramento({ contratoId, aberto, aoFechar }: Props) {
  const clienteDeConsultas = useQueryClient()
  const [status, definirStatus] = useState<'DESMOBILIZADO' | 'DEVOLVIDO' | 'INATIVO'>('DESMOBILIZADO')
  const [data, definirData] = useState('')

  const verificacao = useQuery({
    queryKey: ['contrato', contratoId, 'verificacao-de-devolucao'],
    enabled: aberto,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/contratos/{id}/verificacao-de-devolucao', {
          params: { path: { id: contratoId } },
        }),
      ),
  })

  const envio = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/contratos/{id}/encerramento', {
          params: { path: { id: contratoId } },
          body: { status, dataDeEncerramento: data || undefined },
        }),
      ),
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', contratoId] })
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contratos'] })
      aoFechar()
    },
  })

  const liberado = verificacao.data?.liberaDevolucao ?? false
  const pendencias = verificacao.data?.pendencias ?? []

  const opcoes = [
    {
      valor: 'DESMOBILIZADO' as const,
      titulo: 'Desmobilizado',
      descricao: 'A obra devolveu o veículo. A tratativa com a locadora pode continuar.',
      bloqueado: false,
    },
    {
      valor: 'DEVOLVIDO' as const,
      titulo: 'Devolvido à locadora',
      descricao: liberado
        ? 'Todas as condições da RN-17 estão atendidas.'
        : 'Bloqueado enquanto houver pendência.',
      bloqueado: !liberado,
    },
    {
      valor: 'INATIVO' as const,
      titulo: 'Inativo',
      descricao: 'Contrato encerrado administrativamente, sem devolução registrada.',
      bloqueado: false,
    },
  ]

  return (
    <Dialog
      open={aberto}
      onOpenChange={(estaAberto) => {
        if (!estaAberto) {
          envio.reset()
          aoFechar()
        }
      }}
    >
      <DialogConteudo className="sm:max-w-lg">
        <DialogCabecalho>
          <DialogTitulo>Encerrar contrato</DialogTitulo>
          <DialogDescricao>
            O período do veículo e o do condutor são fechados na data informada; o histórico
            permanece consultável (RN-18).
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

          <div
            className="rounded-[var(--radius-base)] bg-fundo-alternativo px-3 py-2.5"
            aria-live="polite"
          >
            <p className="text-xs font-semibold uppercase tracking-wide text-texto-tenue">
              Verificação da RN-17
            </p>
            {verificacao.isPending ? (
              <p className="mt-1 text-sm text-texto-tenue">Verificando…</p>
            ) : pendencias.length === 0 ? (
              <p className="mt-1 flex items-center gap-2 text-sm text-texto-suave">
                <CheckCircle2 className="size-4 shrink-0 text-sucesso" aria-hidden="true" />
                Nada impede a devolução à locadora.
              </p>
            ) : (
              <ul className="mt-1 space-y-1">
                {pendencias.map((pendencia) => (
                  <li key={pendencia} className="flex items-start gap-2 text-sm text-texto">
                    <AlertCircle className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
                    {pendencia}
                  </li>
                ))}
              </ul>
            )}
          </div>

          <fieldset>
            <legend className="mb-2 text-sm font-medium text-texto">Situação final</legend>
            <div className="space-y-2">
              {opcoes.map((opcao) => (
                <label
                  key={opcao.valor}
                  className={`flex cursor-pointer items-start gap-2.5 rounded-[var(--radius-base)] border px-3 py-2.5 transition-colors ${
                    status === opcao.valor
                      ? 'border-marca bg-marca-suave'
                      : 'border-borda hover:bg-fundo-alternativo'
                  } ${opcao.bloqueado ? 'cursor-not-allowed opacity-60' : ''}`}
                >
                  <input
                    type="radio"
                    name="status-de-encerramento"
                    value={opcao.valor}
                    checked={status === opcao.valor}
                    disabled={opcao.bloqueado}
                    onChange={() => definirStatus(opcao.valor)}
                    className="mt-0.5 size-4 accent-[var(--marca)]"
                  />
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-texto">{opcao.titulo}</span>
                    <span className="block text-xs text-texto-suave">{opcao.descricao}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          <CampoDeFormulario
            id="data-de-encerramento"
            rotulo="Data do encerramento"
            dica="Em branco, vale a data de hoje."
          >
            <Input
              id="data-de-encerramento"
              type="date"
              value={data}
              onChange={(evento) => definirData(evento.target.value)}
            />
          </CampoDeFormulario>
        </DialogCorpo>

        <DialogRodape>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button disabled={envio.isPending} onClick={() => envio.mutate()}>
            {envio.isPending ? 'Encerrando…' : 'Encerrar contrato'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
