import '@tanstack/react-table'

/**
 * Metadados adicionais das colunas da tabela de dados.
 *
 * O TanStack Table declara `ColumnMeta` como uma interface vazia justamente para que
 * cada aplicação a estenda. Estes três campos cobrem o que a tabela precisa saber e
 * que não cabe na definição padrão da coluna.
 */
declare module '@tanstack/react-table' {
  /* eslint-disable-next-line @typescript-eslint/no-unused-vars --
     `TValue` compõe a assinatura publicada pela biblioteca: a augmentação precisa
     repetir os dois parâmetros, mesmo que estes metadados só usem `TData`. */
  interface ColumnMeta<TData extends RowData, TValue> {
    /** Alinha à direita e aplica numerais tabulares. */
    numerica?: boolean
    /** Rótulo textual, para o menu de colunas e o cabeçalho do CSV. */
    rotulo?: string
    /** Converte a linha em texto puro na exportação, quando a célula tem selos ou ícones. */
    exportar?: (linha: TData) => string
  }
}
