import type { ComponentProps } from 'react'

import './botao-de-entrada.css'
import { cn } from '@/lib/utils'

/**
 * Botão de entrada da tela de login.
 *
 * Reproduz `pages_fleet/login_enter_buttom.html` — inclusive a estrutura de três `div`
 * aninhadas, que não é decorativa: cada camada carrega uma parte do efeito. A externa
 * tem a escada de sombras sólidas, a do meio a face escura com a trama de pontos, e a
 * interna o rótulo que afunda ao ser pressionado. Achatar a árvore quebraria o efeito.
 *
 * Por isso o rótulo não é um `children` livre: a hierarquia é fixa, e o texto entra na
 * camada certa. É também o único botão do sistema com esse tratamento — a tela de entrada
 * é a única em que um gesto expressivo não disputa espaço com densidade de informação.
 *
 * A aparência está em `botao-de-entrada.css`, transcrita do arquivo original; o que vive
 * aqui é apenas o comportamento de formulário: envio, estado de carregamento e o aviso
 * para leitores de tela.
 */
export function BotaoDeEntrada({
  children,
  carregando,
  className,
  disabled,
  ...props
}: ComponentProps<'button'> & { carregando?: boolean }) {
  return (
    <button
      type="submit"
      aria-busy={carregando || undefined}
      disabled={disabled ?? carregando}
      className={cn('botao-de-entrada', className)}
      {...props}
    >
      <div>
        <div>
          <div>{children}</div>
        </div>
      </div>
    </button>
  )
}
