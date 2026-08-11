/**
 * Exportação de dados para planilha.
 *
 * O separador é ponto e vírgula e o arquivo leva BOM UTF-8: é o que faz o Excel em
 * português abrir o CSV com as colunas já separadas e os acentos corretos. Com vírgula
 * e sem BOM — o padrão de outras localidades — o usuário recebe tudo em uma coluna só
 * e com caracteres trocados, e conclui que a exportação está quebrada.
 */

const SEPARADOR = ';'
const BOM = '﻿'

/** Escapa um campo conforme o RFC 4180, adaptado ao separador brasileiro. */
function escapar(valor: string): string {
  const texto = valor.replace(/\r?\n/g, ' ').trim()
  if (texto.includes(SEPARADOR) || texto.includes('"')) {
    return `"${texto.replace(/"/g, '""')}"`
  }
  return texto
}

export function montarCsv(cabecalhos: string[], linhas: string[][]): string {
  const conteudo = [cabecalhos, ...linhas]
    .map((linha) => linha.map(escapar).join(SEPARADOR))
    .join('\r\n')
  return BOM + conteudo
}

/** Monta o CSV e dispara o download no navegador. */
export function baixarCsv({
  nomeDoArquivo,
  cabecalhos,
  linhas,
}: {
  nomeDoArquivo: string
  cabecalhos: string[]
  linhas: string[][]
}): void {
  const csv = montarCsv(cabecalhos, linhas)
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const elo = document.createElement('a')
  elo.href = url
  elo.download = `${nomeDoArquivo}-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(elo)
  elo.click()
  document.body.removeChild(elo)
  URL.revokeObjectURL(url)
}
