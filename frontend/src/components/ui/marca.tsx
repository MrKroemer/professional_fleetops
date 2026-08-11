import logoCompacto from '@/assets/logo2-compacto.png'
import logo from '@/assets/logo2.png'
import { cn } from '@/lib/utils'

/**
 * Logotipo da Proyfe Brasil.
 *
 * Duas resoluções servem a dois usos: a versão grande abre a tela de entrada, e a
 * compacta aparece na barra lateral. Carregar o arquivo de 512px dentro de um ícone de
 * 32px desperdiçaria banda e memória de textura em cada sessão.
 *
 * A arte é inteiramente laranja sobre transparência — os carros são recortes em negativo,
 * não desenhos pretos. Por isso ela assenta em qualquer fundo, claro ou escuro, sem
 * precisar de duas versões: o que aparece "dentro" dos veículos é a própria superfície.
 * É também o motivo de o logotipo dispensar a moldura branca que a versão anterior
 * exigia para não sumir no tema escuro.
 */
export function Marca({
  tamanho = 'compacto',
  className,
}: {
  tamanho?: 'compacto' | 'grande'
  className?: string
}) {
  const grande = tamanho === 'grande'
  return (
    <img
      src={grande ? logo : logoCompacto}
      alt="Proyfe Brasil"
      width={grande ? 512 : 128}
      height={grande ? 501 : 125}
      className={cn('select-none object-contain', className)}
      draggable={false}
    />
  )
}
