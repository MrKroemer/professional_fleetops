import { AlertTriangle } from 'lucide-react'
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
import { mensagemDeErro } from '@/lib/api/problem'

/**
 * Confirmação de exclusão.
 *
 * O texto diz explicitamente que a exclusão é lógica e que o histórico é preservado —
 * quem vem das planilhas associa "excluir" a perda definitiva, e essa expectativa
 * errada é o que faz o usuário evitar a operação e deixar lixo no cadastro.
 */
export function DialogoDeExclusao({
  aberto,
  aoMudarAbertura,
  titulo,
  descricao,
  aoConfirmar,
}: {
  aberto: boolean
  aoMudarAbertura: (aberto: boolean) => void
  titulo: string
  descricao: string
  aoConfirmar: () => Promise<unknown>
}) {
  const [excluindo, definirExcluindo] = useState(false)
  const [erro, definirErro] = useState<unknown>(null)

  const confirmar = () => {
    definirExcluindo(true)
    definirErro(null)
    aoConfirmar()
      .then(() => {
        aoMudarAbertura(false)
      })
      .catch((falha: unknown) => {
        definirErro(falha)
      })
      .finally(() => {
        definirExcluindo(false)
      })
  }

  return (
    <Dialog
      open={aberto}
      onOpenChange={(proximo) => {
        if (!proximo) {
          definirErro(null)
        }
        aoMudarAbertura(proximo)
      }}
    >
      <DialogConteudo>
        <DialogCabecalho>
          <DialogTitulo>{titulo}</DialogTitulo>
          <DialogDescricao>{descricao}</DialogDescricao>
        </DialogCabecalho>
        <DialogCorpo>
          <div className="flex items-start gap-3 rounded-[var(--radius-base)] border border-atencao/30 bg-atencao-suave/40 px-3 py-2.5">
            <AlertTriangle className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
            <p className="text-sm text-texto-suave">
              A exclusão é lógica: o registro sai das listagens, mas o histórico e os
              lançamentos já vinculados a ele permanecem íntegros.
            </p>
          </div>
          {erro ? (
            <p role="alert" className="mt-3 text-sm font-medium text-critico">
              {mensagemDeErro(erro)}
            </p>
          ) : null}
        </DialogCorpo>
        <DialogRodape>
          <Button
            variante="secundaria"
            onClick={() => {
              aoMudarAbertura(false)
            }}
            disabled={excluindo}
          >
            Cancelar
          </Button>
          <Button variante="destrutiva" onClick={confirmar} carregando={excluindo}>
            {excluindo ? 'Excluindo…' : 'Excluir'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
