import { Pencil, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Campo,
  ListaDeCampos,
  PainelLateral,
  PainelLateralCabecalho,
  PainelLateralConteudo,
  PainelLateralCorpo,
  PainelLateralDescricao,
  PainelLateralRodape,
  PainelLateralTitulo,
} from '@/components/ui/painel-lateral'

interface CampoDoDetalhe {
  rotulo: string
  valor: ReactNode
  larguraTotal?: boolean
}

interface SecaoDoDetalhe {
  titulo?: string
  campos: CampoDoDetalhe[]
  colunas?: 1 | 2
}

/**
 * Detalhe de um registro, em painel lateral.
 *
 * Dirigido por dados em vez de escrito tela a tela: os cadastros mostram os mesmos
 * blocos — identificação, dados próprios, observações e auditoria — e repetir a
 * marcação seis vezes só multiplicaria as chances de uma delas divergir das outras.
 *
 * Seções cujos campos estão todos vazios são omitidas: um bloco "Observações" vazio
 * ocupa espaço e não informa nada.
 */
export function DetalheDoRegistro({
  titulo,
  subtitulo,
  selo,
  aviso,
  secoes,
  aoFechar,
  podeEditar,
  aoEditar,
  aoExcluir,
  tituloMonoespacado = false,
}: {
  titulo: string
  subtitulo?: string
  selo?: { texto: string; variante: 'sucesso' | 'neutra' | 'atencao' | 'critica' | 'marca' }
  /** Alerta exibido no topo do corpo, para condições que exigem atenção imediata. */
  aviso?: ReactNode
  secoes: SecaoDoDetalhe[]
  aoFechar: () => void
  podeEditar: boolean
  aoEditar: () => void
  aoExcluir: () => void
  /** Use em identificadores técnicos, como placa. */
  tituloMonoespacado?: boolean
}) {
  const visiveis = secoes
    .map((secao) => ({
      ...secao,
      campos: secao.campos.filter((campo) => campo.valor != null && campo.valor !== ''),
    }))
    .filter((secao) => secao.campos.length > 0)

  return (
    <PainelLateral
      open
      onOpenChange={(proximo) => {
        if (!proximo) {
          aoFechar()
        }
      }}
    >
      <PainelLateralConteudo aria-describedby={undefined}>
        <PainelLateralCabecalho>
          <div className="flex flex-wrap items-center gap-2.5">
            <PainelLateralTitulo className={tituloMonoespacado ? 'font-mono' : undefined}>
              {titulo}
            </PainelLateralTitulo>
            {selo ? <Badge variante={selo.variante}>{selo.texto}</Badge> : null}
          </div>
          {subtitulo ? <PainelLateralDescricao>{subtitulo}</PainelLateralDescricao> : null}
        </PainelLateralCabecalho>

        <PainelLateralCorpo>
          {aviso ? <div className="mb-5">{aviso}</div> : null}
          {visiveis.map((secao, indice) => (
            <ListaDeCampos key={secao.titulo ?? indice} titulo={secao.titulo} colunas={secao.colunas}>
              {secao.campos.map((campo) => (
                <Campo key={campo.rotulo} rotulo={campo.rotulo} larguraTotal={campo.larguraTotal}>
                  {campo.valor}
                </Campo>
              ))}
            </ListaDeCampos>
          ))}
        </PainelLateralCorpo>

        {podeEditar ? (
          <PainelLateralRodape>
            <Button variante="secundaria" onClick={aoExcluir}>
              <Trash2 aria-hidden="true" />
              Excluir
            </Button>
            <Button onClick={aoEditar}>
              <Pencil aria-hidden="true" />
              Editar
            </Button>
          </PainelLateralRodape>
        ) : null}
      </PainelLateralConteudo>
    </PainelLateral>
  )
}
