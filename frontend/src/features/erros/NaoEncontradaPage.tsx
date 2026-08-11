import { Compass } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'

/** Rota inexistente dentro da aplicação autenticada. */
export function NaoEncontradaPage() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-16">
      <EstadoVazio
        icone={<Compass className="size-6" />}
        titulo="Página não encontrada"
        descricao="O endereço acessado não corresponde a nenhuma tela do sistema. Pode ser um link antigo ou uma área ainda não entregue."
        acao={
          <Button asChild variante="secundaria" tamanho="pequeno">
            <Link to="/">Voltar ao dashboard</Link>
          </Button>
        }
      />
    </div>
  )
}
