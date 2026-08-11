/**
 * Auxiliares de acessibilidade compartilhados pelos formulários.
 */

/**
 * Monta o valor de `aria-describedby` a partir dos identificadores existentes.
 *
 * Quando há erro, ele substitui a dica na descrição: anunciar as duas faria o
 * leitor de tela ler texto de ajuda antes da mensagem que realmente importa.
 */
export function descritoPor(
  id: string,
  temDica: boolean,
  temErro: boolean,
): string | undefined {
  if (temErro) return `${id}-erro`
  if (temDica) return `${id}-dica`
  return undefined
}
