import { useMutation } from '@tanstack/react-query'
import { Copy, Eye, KeyRound, ShieldAlert } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogCabecalho,
  DialogConteudo,
  DialogCorpo,
  DialogDescricao,
  DialogRodape,
  DialogTitulo,
} from '@/components/ui/dialog'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'
import type { CredencialRevelada } from '@/features/cadastros/tipos'

/**
 * Revelação de credenciais de portal (RN-20).
 *
 * O valor em claro nunca vem junto da listagem: só é buscado quando o usuário pede,
 * e cada pedido é registrado no servidor com o solicitante. A interface reforça isso
 * explicitamente, porque quem opera precisa saber que o acesso deixa rastro.
 */
export function RevelarCredenciais({
  origem,
  id,
  nome,
  desabilitado,
}: {
  /** Recurso dono da credencial — a rota difere entre locadora e fornecedor. */
  origem: 'locadora' | 'fornecedor'
  id: number
  nome: string
  desabilitado?: boolean
}) {
  const [aberto, definirAberto] = useState(false)
  const [credencial, definirCredencial] = useState<CredencialRevelada | null>(null)
  const [copiado, definirCopiado] = useState<'login' | 'senha' | null>(null)

  const revelacao = useMutation({
    mutationFn: async () => {
      if (origem === 'locadora') {
        return exigirSucesso(
          await api.GET('/api/v1/locadoras/{id}/credenciais', { params: { path: { id } } }),
        )
      }
      return exigirSucesso(
        await api.GET('/api/v1/fornecedores/{id}/credenciais', { params: { path: { id } } }),
      )
    },
    onSuccess: (dados) => {
      definirCredencial(dados)
    },
  })

  const abrir = () => {
    definirCredencial(null)
    definirCopiado(null)
    revelacao.reset()
    definirAberto(true)
  }

  const copiar = (valor: string | undefined, campo: 'login' | 'senha') => {
    if (!valor) return
    void navigator.clipboard?.writeText(valor).then(() => {
      definirCopiado(campo)
    })
  }

  return (
    <>
      <Button
        variante="sutil"
        tamanho="icone"
        onClick={abrir}
        disabled={desabilitado}
        aria-label={`Revelar credenciais de ${nome}`}
      >
        <KeyRound aria-hidden="true" />
      </Button>

      <Dialog
        open={aberto}
        onOpenChange={(proximo) => {
          definirAberto(proximo)
          if (!proximo) {
            // O valor em claro não permanece em memória depois de fechado.
            definirCredencial(null)
          }
        }}
      >
        <DialogConteudo>
          <DialogCabecalho>
            <DialogTitulo>Credenciais do portal</DialogTitulo>
            <DialogDescricao>{nome}</DialogDescricao>
          </DialogCabecalho>

          <DialogCorpo className="space-y-4">
            <div className="flex items-start gap-3 rounded-[var(--radius-base)] border border-atencao/30 bg-atencao-suave/40 px-3 py-2.5">
              <ShieldAlert className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
              <p className="text-sm text-texto-suave">
                Este acesso é registrado com o seu usuário. Revele apenas quando precisar
                usar o portal e não compartilhe o valor por mensagem.
              </p>
            </div>

            {credencial ? (
              <dl className="space-y-3">
                <LinhaDeCredencial
                  rotulo="Login"
                  valor={credencial.login}
                  copiado={copiado === 'login'}
                  aoCopiar={() => {
                    copiar(credencial.login, 'login')
                  }}
                />
                <LinhaDeCredencial
                  rotulo="Senha"
                  valor={credencial.senha}
                  copiado={copiado === 'senha'}
                  aoCopiar={() => {
                    copiar(credencial.senha, 'senha')
                  }}
                />
              </dl>
            ) : revelacao.isError ? (
              <p role="alert" className="text-sm font-medium text-critico">
                {mensagemDeErro(revelacao.error)}
              </p>
            ) : (
              <p className="text-sm text-texto-suave">
                As credenciais permanecem cifradas no banco. Clique em “Revelar” para
                consultá-las.
              </p>
            )}
          </DialogCorpo>

          <DialogRodape>
            <Button
              variante="secundaria"
              onClick={() => {
                definirAberto(false)
              }}
            >
              Fechar
            </Button>
            {!credencial ? (
              <Button
                onClick={() => {
                  revelacao.mutate()
                }}
                carregando={revelacao.isPending}
              >
                <Eye aria-hidden="true" />
                {revelacao.isPending ? 'Revelando…' : 'Revelar'}
              </Button>
            ) : null}
          </DialogRodape>
        </DialogConteudo>
      </Dialog>
    </>
  )
}

function LinhaDeCredencial({
  rotulo,
  valor,
  copiado,
  aoCopiar,
}: {
  rotulo: string
  valor: string | undefined
  copiado: boolean
  aoCopiar: () => void
}) {
  return (
    <div className="flex items-center gap-2 rounded-[var(--radius-base)] border border-borda bg-fundo-alternativo/50 px-3 py-2">
      <div className="min-w-0 flex-1">
        <dt className="text-xs font-medium uppercase tracking-wide text-texto-tenue">{rotulo}</dt>
        <dd className="truncate font-mono text-sm text-texto">{valor || '—'}</dd>
      </div>
      <Button
        variante="sutil"
        tamanho="icone"
        onClick={aoCopiar}
        disabled={!valor}
        aria-label={`Copiar ${rotulo.toLowerCase()}`}
      >
        <Copy aria-hidden="true" />
      </Button>
      {copiado ? <span className="text-xs text-sucesso">Copiado</span> : null}
    </div>
  )
}
