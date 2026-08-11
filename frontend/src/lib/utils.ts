import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Combina classes do Tailwind resolvendo conflitos: a última classe do mesmo grupo
 * vence. Sem isto, `cn('p-2', 'p-4')` deixaria as duas no DOM e o resultado
 * dependeria da ordem no CSS gerado.
 */
export function cn(...entradas: ClassValue[]): string {
  return twMerge(clsx(entradas))
}
