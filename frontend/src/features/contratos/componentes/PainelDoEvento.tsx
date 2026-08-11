import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, FileText, Gauge, Loader2, ShieldCheck, Upload } from 'lucide-react'
import { useRef, useState } from 'react'

import type { Evento } from '../tipos'
import { BookFotografico } from './BookFotografico'
import { DialogoDeFumacaPreta } from './DialogoDeFumacaPreta'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'
import { formatarDataHora } from '@/lib/formatters'

interface Props {
  evento: Evento
  podeEditar: boolean
}

/**
 * Preenchimento de uma retirada ou devolução (RN-12).
 *
 * A regra aparece como uma lista de condições, não como um botão que falha. O botão de
 * concluir fica desabilitado enquanto falta algo, e ao lado dele está o que falta — a
 * RN-12 exige que o sistema "liste os itens obrigatórios", e um erro só depois do clique
 * cumpriria a letra e falharia o propósito.
 *
 * Mesmo assim a conclusão pode ser recusada pelo servidor: a RN-09 só é verificada lá,
 * porque depende do teste de fumaça preta do veículo, que muda fora desta tela. O erro
 * volta com o texto do domínio, que já diz se falta fazer o teste ou trocar o carro.
 */
export function PainelDoEvento({ evento, podeEditar }: Props) {
  const clienteDeConsultas = useQueryClient()
  const entradaDoCrlv = useRef<HTMLInputElement>(null)
  const [erro, definirErro] = useState<string | null>(null)
  const [testando, definirTestando] = useState(false)

  /**
   * Situação da RN-09 do veículo do evento.
   *
   * Consultada mesmo em devoluções, onde a regra não se aplica: o resultado alimenta o
   * bloco de condições, e mostrar "não exigido" para um carro a gasolina é informação —
   * esconder a linha deixaria a dúvida de se o sistema verificou.
   */
  const fumaca = useQuery({
    queryKey: ['fumaca-preta', evento.veiculoId],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/fumaca-preta/veiculos/{veiculoId}', {
          params: { path: { veiculoId: evento.veiculoId } },
        }),
      ),
  })

  const itens = useQuery({
    queryKey: ['book', 'itens'],
    staleTime: Infinity,
    queryFn: async () => exigirSucesso(await api.GET('/api/v1/contratos/book/itens', {})),
  })

  const invalidar = async () => {
    await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', evento.contratoId] })
  }

  const crlv = useMutation({
    mutationFn: async (arquivo: File) => {
      const corpo = new FormData()
      corpo.append('arquivo', arquivo)
      return exigirSucesso(
        await api.POST('/api/v1/contratos/eventos/{eventoId}/crlv', {
          params: { path: { eventoId: evento.id } },
          body: corpo as never,
        }),
      )
    },
    onError: (falha) => {
      definirErro(mensagemDeErro(falha))
    },
    onSettled: invalidar,
  })

  const aceite = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/contratos/eventos/{eventoId}/aceite-das-regras', {
          params: { path: { eventoId: evento.id } },
        }),
      ),
    onSettled: invalidar,
  })

  const conclusao = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/contratos/eventos/{eventoId}/conclusao', {
          params: { path: { eventoId: evento.id } },
        }),
      ),
    onMutate: () => {
      definirErro(null)
    },
    onError: (falha) => {
      definirErro(mensagemDeErro(falha))
    },
    onSettled: invalidar,
  })

  const reabertura = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/contratos/eventos/{eventoId}/reabertura', {
          params: { path: { eventoId: evento.id } },
        }),
      ),
    onSettled: invalidar,
  })

  const concluido = evento.situacao === 'CONCLUIDO'
  const editavel = podeEditar && !concluido

  return (
    <section className="rounded-[var(--radius-base)] border border-borda bg-superficie">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-borda px-4 py-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold text-texto">{evento.tipoDescricao}</h3>
            <Badge variante={concluido ? 'sucesso' : 'atencao'}>{evento.situacaoDescricao}</Badge>
          </div>
          <p className="mt-0.5 text-sm text-texto-suave">
            <span className="font-mono">{evento.placa}</span> · {evento.modelo}
            {evento.condutor ? ` · ${evento.condutor}` : ''}
            {evento.km != null ? ` · ${evento.km.toLocaleString('pt-BR')} km` : ''}
          </p>
        </div>

        {podeEditar ? (
          concluido ? (
            <Button
              variante="secundaria"
              tamanho="pequeno"
              onClick={() => {
                reabertura.mutate()
              }}
            >
              Reabrir para correção
            </Button>
          ) : (
            <Button
              disabled={!evento.completo || conclusao.isPending}
              onClick={() => {
                conclusao.mutate()
              }}
            >
              {conclusao.isPending ? (
                <Loader2 className="animate-spin" aria-hidden="true" />
              ) : (
                <CheckCircle2 aria-hidden="true" />
              )}
              Concluir {evento.tipoDescricao.toLowerCase()}
            </Button>
          )
        ) : null}
      </header>

      <div className="space-y-4 p-4">
        {erro ? (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/50 px-3 py-2.5"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0 text-critico" aria-hidden="true" />
            <p className="text-sm text-texto">{erro}</p>
          </div>
        ) : null}

        {/* O que falta, antes do botão falhar. */}
        {!concluido ? (
          <div className="rounded-[var(--radius-base)] bg-fundo-alternativo px-3 py-2.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-texto-tenue">
              Para concluir (RN-12)
            </p>
            <ul className="mt-1.5 space-y-1 text-sm">
              <li className="flex items-center gap-2">
                {evento.itensFaltantes.length === 0 ? (
                  <CheckCircle2 className="size-4 shrink-0 text-sucesso" aria-hidden="true" />
                ) : (
                  <AlertCircle className="size-4 shrink-0 text-atencao" aria-hidden="true" />
                )}
                <span className={evento.itensFaltantes.length === 0 ? 'text-texto-suave' : 'text-texto'}>
                  {evento.itensFaltantes.length === 0
                    ? 'Book fotográfico completo'
                    : `Faltam ${String(evento.itensFaltantes.length)} foto(s): ${evento.itensFaltantes.map((i) => i.descricao).join(', ')}`}
                </span>
              </li>
              {evento.tipo === 'RETIRADA' && fumaca.data?.exigido ? (
                <li className="flex items-center gap-2">
                  {fumaca.data.liberado ? (
                    <CheckCircle2 className="size-4 shrink-0 text-sucesso" aria-hidden="true" />
                  ) : (
                    <AlertCircle className="size-4 shrink-0 text-critico" aria-hidden="true" />
                  )}
                  <span className={fumaca.data.liberado ? 'text-texto-suave' : 'text-texto'}>
                    {fumaca.data.liberado
                      ? `Fumaça preta aprovada (${fumaca.data.ultimoTeste?.padraoDescricao ?? ''})`
                      : fumaca.data.pendente
                        ? 'Veículo a diesel sem teste de fumaça preta (RN-09)'
                        : `Fumaça preta reprovada: ${fumaca.data.ultimoTeste?.justificativa ?? ''}`}
                  </span>
                </li>
              ) : null}
              <li className="flex items-center gap-2">
                {evento.crlvAnexoId ? (
                  <CheckCircle2 className="size-4 shrink-0 text-sucesso" aria-hidden="true" />
                ) : (
                  <AlertCircle className="size-4 shrink-0 text-atencao" aria-hidden="true" />
                )}
                <span className={evento.crlvAnexoId ? 'text-texto-suave' : 'text-texto'}>
                  {evento.crlvAnexoId ? `CRLV anexado (${evento.crlvNomeDoArquivo})` : 'CRLV não anexado'}
                </span>
              </li>
            </ul>
          </div>
        ) : (
          <p className="text-sm text-texto-suave">
            Concluído em {formatarDataHora(evento.concluidoEm)}.
          </p>
        )}

        {/* CRLV e aceite das regras */}
        <div className="flex flex-wrap gap-2">
          {editavel ? (
            <>
              <input
                ref={entradaDoCrlv}
                type="file"
                accept="image/*,application/pdf"
                className="sr-only"
                onChange={(evt) => {
                  const arquivo = evt.target.files?.[0]
                  if (arquivo) crlv.mutate(arquivo)
                  evt.target.value = ''
                }}
              />
              <Button
                variante="secundaria"
                tamanho="pequeno"
                disabled={crlv.isPending}
                onClick={() => entradaDoCrlv.current?.click()}
              >
                {crlv.isPending ? (
                  <Loader2 className="animate-spin" aria-hidden="true" />
                ) : evento.crlvAnexoId ? (
                  <FileText aria-hidden="true" />
                ) : (
                  <Upload aria-hidden="true" />
                )}
                {evento.crlvAnexoId ? 'Substituir CRLV' : 'Anexar CRLV'}
              </Button>

              {fumaca.data?.exigido ? (
                <Button variante="secundaria" tamanho="pequeno" onClick={() => definirTestando(true)}>
                  <Gauge aria-hidden="true" />
                  Registrar fumaça preta
                </Button>
              ) : null}

              <Button
                variante="secundaria"
                tamanho="pequeno"
                disabled={aceite.isPending || evento.regrasAceitasEm != null}
                onClick={() => {
                  aceite.mutate()
                }}
              >
                <ShieldCheck aria-hidden="true" />
                {evento.regrasAceitasEm
                  ? `Regras aceitas em ${formatarDataHora(evento.regrasAceitasEm)}`
                  : 'Registrar aceite das regras'}
              </Button>
            </>
          ) : null}
        </div>

        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-texto-tenue">
            Book fotográfico
          </p>
          {itens.isPending ? (
            <p className="text-sm text-texto-tenue">Carregando os ângulos…</p>
          ) : (
            <BookFotografico evento={evento} itens={itens.data ?? []} podeEditar={editavel} />
          )}
        </div>

        {evento.checklistDaLocadora ? (
          <div>
            <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-texto-tenue">
              Checklist da locadora
            </p>
            <p className="whitespace-pre-line text-sm text-texto-suave">
              {evento.checklistDaLocadora}
            </p>
          </div>
        ) : null}
      </div>

      {testando ? (
        <DialogoDeFumacaPreta
          veiculoId={evento.veiculoId}
          contratoId={evento.contratoId}
          aberto
          aoFechar={() => definirTestando(false)}
        />
      ) : null}
    </section>
  )
}
