import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Camera, Check, Loader2, Plus, Trash2 } from 'lucide-react'
import { useRef, useState } from 'react'

import type { Evento, Foto, ItemPendente } from '../tipos'
import { Button } from '@/components/ui/button'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'
import { cn } from '@/lib/utils'

/**
 * Lado maior da imagem depois da compressão, em pixels.
 *
 * A Seção 6 pede compressão no cliente. 1600px preserva o detalhe que importa em um book
 * — um risco na lataria, o número do hodômetro — e reduz uma foto de celular de ~4 MB
 * para algumas centenas de KB. Enviar o original gastaria banda de 4G no pátio da
 * locadora, que é exatamente onde a conexão é pior.
 */
const LADO_MAXIMO = 1600

/** Qualidade do JPEG resultante. Acima disso o ganho visual não paga o tamanho. */
const QUALIDADE = 0.82

/**
 * Reduz a imagem antes do envio.
 *
 * Devolve o arquivo original quando a compressão falha — um navegador sem suporte a
 * `canvas.toBlob`, um HEIC que o decodificador não abriu. Falhar o envio inteiro por
 * causa da otimização seria trocar um problema de banda por um de operação.
 */
async function comprimir(arquivo: File): Promise<File> {
  if (!arquivo.type.startsWith('image/')) {
    return arquivo
  }
  try {
    const bitmap = await createImageBitmap(arquivo)
    const escala = Math.min(1, LADO_MAXIMO / Math.max(bitmap.width, bitmap.height))
    if (escala === 1 && arquivo.size < 1_000_000) {
      return arquivo
    }
    const tela = document.createElement('canvas')
    tela.width = Math.round(bitmap.width * escala)
    tela.height = Math.round(bitmap.height * escala)
    const contexto = tela.getContext('2d')
    if (!contexto) return arquivo
    contexto.drawImage(bitmap, 0, 0, tela.width, tela.height)
    const blob = await new Promise<Blob | null>((resolver) => {
      tela.toBlob(resolver, 'image/jpeg', QUALIDADE)
    })
    bitmap.close()
    if (!blob || blob.size >= arquivo.size) {
      return arquivo
    }
    return new File([blob], arquivo.name.replace(/\.\w+$/, '.jpg'), { type: 'image/jpeg' })
  } catch {
    return arquivo
  }
}

interface Props {
  evento: Evento
  itens: ItemPendente[]
  podeEditar: boolean
}

/**
 * Book fotográfico de um evento (RN-12).
 *
 * Mostra a grade completa dos ângulos, com ou sem foto. Uma lista só do que já foi
 * enviado esconderia justamente o que falta — e a RN-12 exige que o sistema "liste os
 * itens obrigatórios". Aqui o vazio é a informação principal.
 *
 * Cada ângulo envia sozinho. São oito fotos tiradas com o celular no pátio da locadora;
 * um envio único perderia as sete anteriores a cada oscilação de rede.
 */
export function BookFotografico({ evento, itens, podeEditar }: Props) {
  const clienteDeConsultas = useQueryClient()
  const [enviando, definirEnviando] = useState<string | null>(null)
  const [erro, definirErro] = useState<string | null>(null)
  const entradas = useRef<Record<string, HTMLInputElement | null>>({})

  const envio = useMutation({
    mutationFn: async ({ item, arquivo }: { item: string; arquivo: File }) => {
      const corpo = new FormData()
      corpo.append('arquivo', await comprimir(arquivo))
      return exigirSucesso(
        await api.POST('/api/v1/contratos/eventos/{eventoId}/fotos', {
          params: { path: { eventoId: evento.id }, query: { item } },
          body: corpo as never,
        }),
      )
    },
    onMutate: ({ item }) => {
      definirEnviando(item)
      definirErro(null)
    },
    onError: (falha) => {
      definirErro(mensagemDeErro(falha))
    },
    onSettled: async () => {
      definirEnviando(null)
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', evento.contratoId] })
    },
  })

  const remocao = useMutation({
    mutationFn: async (fotoId: number) =>
      exigirSucesso(
        await api.DELETE('/api/v1/contratos/eventos/{eventoId}/fotos/{fotoId}', {
          params: { path: { eventoId: evento.id, fotoId } },
        }),
      ),
    onSettled: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', evento.contratoId] })
    },
  })

  const porItem = new Map<string, Foto[]>()
  for (const foto of evento.fotos) {
    porItem.set(foto.item, [...(porItem.get(foto.item) ?? []), foto])
  }
  const faltantes = new Set(evento.itensFaltantes.map((item) => item.item))

  return (
    <div className="space-y-3">
      {erro ? (
        <p role="alert" className="text-sm text-critico">
          {erro}
        </p>
      ) : null}

      <ul className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-4">
        {itens.map((item) => {
          const fotos = porItem.get(item.item) ?? []
          const temFoto = fotos.length > 0
          const obrigatorioPendente = faltantes.has(item.item)
          const estaEnviando = enviando === item.item

          return (
            <li key={item.item}>
              <div
                className={cn(
                  'flex h-full flex-col rounded-[var(--radius-base)] border p-2.5 transition-colors',
                  temFoto
                    ? 'border-sucesso/40 bg-sucesso-suave/40'
                    : obrigatorioPendente
                      ? 'border-dashed border-atencao/50 bg-atencao-suave/30'
                      : 'border-dashed border-borda',
                )}
              >
                <div className="flex items-start justify-between gap-1">
                  <p className="text-xs font-medium leading-tight text-texto">{item.descricao}</p>
                  {temFoto ? (
                    <Check className="size-3.5 shrink-0 text-sucesso" aria-hidden="true" />
                  ) : obrigatorioPendente ? (
                    <span className="shrink-0 text-[0.6rem] font-semibold uppercase text-atencao">
                      falta
                    </span>
                  ) : (
                    <span className="shrink-0 text-[0.6rem] uppercase text-texto-tenue">
                      opcional
                    </span>
                  )}
                </div>

                <ul className="mt-1.5 flex-1 space-y-1">
                  {fotos.map((foto) => (
                    <li key={foto.id} className="flex items-center gap-1">
                      <span className="min-w-0 flex-1 truncate text-[0.68rem] text-texto-suave">
                        {foto.nomeDoArquivo}
                      </span>
                      {podeEditar ? (
                        <button
                          type="button"
                          onClick={() => {
                            remocao.mutate(foto.id)
                          }}
                          className="rounded p-0.5 text-texto-tenue hover:text-critico"
                          aria-label={`Remover a foto ${foto.itemDescricao}`}
                        >
                          <Trash2 className="size-3" aria-hidden="true" />
                        </button>
                      ) : null}
                    </li>
                  ))}
                </ul>

                {podeEditar ? (
                  <>
                    <input
                      ref={(elemento) => {
                        entradas.current[item.item] = elemento
                      }}
                      type="file"
                      accept="image/*"
                      capture="environment"
                      className="sr-only"
                      onChange={(evt) => {
                        const arquivo = evt.target.files?.[0]
                        if (arquivo) {
                          envio.mutate({ item: item.item, arquivo })
                        }
                        evt.target.value = ''
                      }}
                    />
                    <Button
                      variante="sutil"
                      tamanho="pequeno"
                      className="mt-1.5 w-full"
                      disabled={estaEnviando}
                      onClick={() => {
                        entradas.current[item.item]?.click()
                      }}
                    >
                      {estaEnviando ? (
                        <Loader2 className="animate-spin" aria-hidden="true" />
                      ) : temFoto ? (
                        <Plus aria-hidden="true" />
                      ) : (
                        <Camera aria-hidden="true" />
                      )}
                      {estaEnviando ? 'Enviando…' : temFoto ? 'Refazer' : 'Fotografar'}
                    </Button>
                  </>
                ) : null}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
