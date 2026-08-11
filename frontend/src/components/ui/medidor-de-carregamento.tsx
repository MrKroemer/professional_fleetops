import { cn } from '@/lib/utils'

/**
 * Indicador de carregamento em forma de manômetro.
 *
 * Adaptado de `pages_fleet/loader.html`: um semicírculo com um ponteiro laranja que
 * oscila, como o marcador de combustível do painel de um veículo. Serve de indicador
 * global — o carregamento de listas continua usando esqueleto, que preserva o layout
 * e evita o salto de conteúdo.
 *
 * As cores literais do arquivo original foram substituídas por variáveis do tema, de
 * modo que o componente acompanhe claro e escuro.
 */
export function MedidorDeCarregamento({
  rotulo = 'Carregando',
  tamanho = 'medio',
  className,
}: {
  rotulo?: string
  tamanho?: 'pequeno' | 'medio'
  className?: string
}) {
  const escala = tamanho === 'pequeno' ? 0.6 : 1

  return (
    <div className={cn('flex flex-col items-center gap-3', className)} role="status" aria-live="polite">
      <span
        className="relative block overflow-hidden rounded-t-full bg-borda-forte"
        style={{ width: 96 * escala, height: 48 * escala }}
        aria-hidden="true"
      >
        {/* Ponteiro: oscila entre os extremos da escala, ancorado na base. */}
        <span
          className="absolute bottom-0 left-1/2 origin-bottom rounded-full bg-marca"
          style={{
            width: 4 * escala,
            height: 32 * escala,
            marginLeft: -2 * escala,
            animation: 'oscilar-ponteiro 2s linear infinite alternate',
          }}
        />
        {/* Cubo central, que esconde o pivô do ponteiro. */}
        <span
          className="absolute bottom-0 left-1/2 rounded-t-full bg-texto-tenue"
          style={{ width: 24 * escala, height: 12 * escala, marginLeft: -12 * escala }}
        />
      </span>
      <span className="text-sm text-texto-suave">{rotulo}…</span>

      <style>{`
        @keyframes oscilar-ponteiro {
          0%   { transform: rotate(-70deg) }
          20%  { transform: rotate(-12deg) }
          40%  { transform: rotate(-32deg) }
          55%  { transform: rotate(22deg) }
          75%  { transform: rotate(42deg) }
          100% { transform: rotate(70deg) }
        }
      `}</style>
    </div>
  )
}
