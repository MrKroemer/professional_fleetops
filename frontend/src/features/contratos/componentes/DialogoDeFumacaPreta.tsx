import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, XCircle } from 'lucide-react'
import { useState } from 'react'

import { EscalaDeRingelmann } from './EscalaDeRingelmann'
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
import { formatarData } from '@/lib/formatters'

/** Acima desta altitude o critério da Seção 3.4 afrouxa um padrão. */
const ALTITUDE_DE_CORTE = 500

interface Props {
  veiculoId: number
  contratoId: number
  aberto: boolean
  aoFechar: () => void
}

/**
 * Registro de teste de fumaça preta — FOR.MA.01 (RN-09).
 *
 * O veredito aparece <strong>enquanto</strong> o avaliador escolhe, não depois de gravar.
 * O limite muda com a altitude, e quem está no pátio precisa saber que o mesmo Padrão 3
 * aprova em Uibaí e reprova em Recife antes de confirmar — não como uma surpresa na
 * resposta do servidor.
 *
 * A regra é reproduzida aqui apenas para essa antecipação. A decisão que vale é a coluna
 * gerada no banco, que nenhuma tela contorna.
 */
export function DialogoDeFumacaPreta({ veiculoId, contratoId, aberto, aoFechar }: Props) {
  const clienteDeConsultas = useQueryClient()
  const [padrao, definirPadrao] = useState<number | null>(null)
  const [altitude, definirAltitude] = useState('0')
  const [data, definirData] = useState('')
  const [observacoes, definirObservacoes] = useState('')

  const historico = useQuery({
    queryKey: ['fumaca-preta', veiculoId],
    enabled: aberto,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/fumaca-preta/veiculos/{veiculoId}/historico', {
          params: { path: { veiculoId } },
        }),
      ),
  })

  const envio = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/fumaca-preta', {
          body: {
            veiculoId,
            contratoId,
            dataDoTeste: data,
            padrao: padrao ?? 0,
            altitudeEmMetros: Number(altitude) || 0,
            observacoes: observacoes || undefined,
          },
        }),
      ),
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['fumaca-preta', veiculoId] })
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', contratoId] })
      limpar()
      aoFechar()
    },
  })

  function limpar() {
    definirPadrao(null)
    definirAltitude('0')
    definirData('')
    definirObservacoes('')
    envio.reset()
  }

  const metros = Number(altitude) || 0
  const limite = metros > ALTITUDE_DE_CORTE ? 3 : 2
  const aprovado = padrao != null && padrao <= limite

  return (
    <Dialog
      open={aberto}
      onOpenChange={(estaAberto) => {
        if (!estaAberto) {
          limpar()
          aoFechar()
        }
      }}
    >
      <DialogConteudo className="sm:max-w-lg">
        <DialogCabecalho>
          <DialogTitulo>Teste de fumaça preta</DialogTitulo>
          <DialogDescricao>
            FOR.MA.01. Compare a fumaça do escapamento com a cartela e escolha o padrão
            correspondente. Veículo a diesel só é liberado com teste aprovado (RN-09).
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

          <EscalaDeRingelmann
            valor={padrao}
            aoEscolher={definirPadrao}
            limite={limite}
            desabilitado={envio.isPending}
          />

          {padrao != null ? (
            <p
              aria-live="polite"
              className={`flex items-center gap-2 rounded-[var(--radius-base)] px-3 py-2.5 text-sm ${
                aprovado ? 'bg-sucesso-suave text-texto' : 'bg-critico-suave text-texto'
              }`}
            >
              {aprovado ? (
                <CheckCircle2 className="size-4 shrink-0 text-sucesso" aria-hidden="true" />
              ) : (
                <XCircle className="size-4 shrink-0 text-critico" aria-hidden="true" />
              )}
              {aprovado
                ? `Aprovado — o limite é o Padrão ${String(limite)} ${metros > ALTITUDE_DE_CORTE ? 'acima de 500 m' : 'até 500 m'} de altitude.`
                : `Reprovado — ultrapassa o Padrão ${String(limite)}, o limite ${metros > ALTITUDE_DE_CORTE ? 'acima de 500 m' : 'até 500 m'} de altitude. A retirada exigirá outro veículo.`}
            </p>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <CampoDeFormulario id="data-do-teste" rotulo="Data do teste" obrigatorio>
              <Input
                id="data-do-teste"
                type="date"
                value={data}
                onChange={(evento) => definirData(evento.target.value)}
              />
            </CampoDeFormulario>

            <CampoDeFormulario
              id="altitude"
              rotulo="Altitude (m)"
              dica="Acima de 500 m o limite passa a ser o Padrão 3."
            >
              <Input
                id="altitude"
                type="number"
                min={0}
                value={altitude}
                onChange={(evento) => definirAltitude(evento.target.value)}
              />
            </CampoDeFormulario>
          </div>

          <CampoDeFormulario id="obs-fumaca" rotulo="Observações">
            <Input
              id="obs-fumaca"
              value={observacoes}
              onChange={(evento) => definirObservacoes(evento.target.value)}
              placeholder="Condições da medição, avaliador, etc."
            />
          </CampoDeFormulario>

          {(historico.data ?? []).length > 0 ? (
            <div>
              <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-texto-tenue">
                Testes anteriores deste veículo
              </p>
              <ul className="space-y-1">
                {(historico.data ?? []).slice(0, 4).map((teste) => (
                  <li key={teste.id} className="flex items-center gap-2 text-sm">
                    {teste.conforme ? (
                      <CheckCircle2 className="size-3.5 shrink-0 text-sucesso" aria-hidden="true" />
                    ) : (
                      <XCircle className="size-3.5 shrink-0 text-critico" aria-hidden="true" />
                    )}
                    <span className="text-texto-suave">
                      {formatarData(teste.dataDoTeste)} · {teste.padraoDescricao} ·{' '}
                      {teste.altitudeEmMetros} m
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </DialogCorpo>

        <DialogRodape>
          <Button
            variante="secundaria"
            onClick={() => {
              limpar()
              aoFechar()
            }}
          >
            Cancelar
          </Button>
          <Button
            disabled={padrao == null || !data || envio.isPending}
            onClick={() => envio.mutate()}
          >
            {envio.isPending ? 'Registrando…' : 'Registrar teste'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
