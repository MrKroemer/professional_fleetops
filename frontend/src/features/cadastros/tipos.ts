import type { components } from '@/lib/api/schema'

/**
 * Tipos dos cadastros, derivados da OpenAPI.
 *
 * Nenhum tipo é redigitado: qualquer mudança de contrato no backend aparece aqui como
 * erro de compilação, que é exatamente o comportamento desejado.
 */

export type Obra = components['schemas']['ObraResponse']
export type ObraRequest = components['schemas']['ObraRequest']
export type ObraResumo = components['schemas']['ObraResumoResponse']
export type StatusObra = Obra['status']

export type Locadora = components['schemas']['LocadoraResponse']
export type LocadoraRequest = components['schemas']['LocadoraRequest']
export type LocadoraResumo = components['schemas']['LocadoraResumoResponse']
export type TipoLocadora = Locadora['tipo']

export type Condutor = components['schemas']['CondutorResponse']
export type CondutorRequest = components['schemas']['CondutorRequest']
export type StatusCondutor = Condutor['status']

export type Veiculo = components['schemas']['VeiculoResponse']
export type VeiculoRequest = components['schemas']['VeiculoRequest']
export type CategoriaVeiculo = Veiculo['categoria']
export type Combustivel = Veiculo['combustivel']
export type StatusVeiculo = Veiculo['status']

export type Fornecedor = components['schemas']['FornecedorResponse']
export type FornecedorRequest = components['schemas']['FornecedorRequest']
export type TipoFornecedor = Fornecedor['tipo']
export type DiaDaSemana = components['schemas']['DadosDePosto']['diasAutorizados'] extends
  | Array<infer T>
  | undefined
  ? T
  : never

export type TabelaPreco = components['schemas']['TabelaPrecoResponse']
export type TabelaPrecoRequest = components['schemas']['TabelaPrecoRequest']

export type CredencialRevelada = components['schemas']['CredencialReveladaResponse']

/**
 * Rótulos em pt-BR das enumerações.
 *
 * O backend já devolve a descrição de cada valor nos detalhes, mas estas listas são
 * necessárias para montar os campos de seleção dos formulários, onde ainda não há
 * registro do qual extrair a descrição.
 */

export const STATUS_DE_OBRA: Record<StatusObra, string> = {
  ATIVA: 'Ativa',
  ENCERRADA: 'Encerrada',
}

export const TIPOS_DE_LOCADORA: Record<TipoLocadora, string> = {
  NACIONAL: 'Nacional',
  AVULSA: 'Avulsa / local',
}

export const STATUS_DE_CONDUTOR: Record<StatusCondutor, string> = {
  ATIVO: 'Ativo',
  INATIVO: 'Inativo',
}

export const CATEGORIAS_DE_VEICULO: Record<CategoriaVeiculo, string> = {
  PASSEIO: 'Passeio',
  SUV: 'SUV',
  QUATRO_X_QUATRO: '4x4',
  UTILITARIO: 'Utilitário',
}

export const COMBUSTIVEIS: Record<Combustivel, string> = {
  FLEX: 'Flex',
  GASOLINA: 'Gasolina',
  ETANOL: 'Etanol',
  DIESEL: 'Diesel',
  HIBRIDO: 'Híbrido',
  ELETRICO: 'Elétrico',
}

export const STATUS_DE_VEICULO: Record<StatusVeiculo, string> = {
  DISPONIVEL: 'Disponível',
  EM_USO: 'Em uso',
  EM_MANUTENCAO: 'Em manutenção',
  DEVOLVIDO: 'Devolvido à locadora',
}

export const TIPOS_DE_FORNECEDOR: Record<TipoFornecedor, string> = {
  POSTO: 'Posto de combustível',
  LAVA_JATO: 'Lava-jato',
  BORRACHARIA: 'Borracharia',
  PARA_BRISAS: 'Para-brisas',
  RASTREADOR: 'Rastreador',
  GRAFICA: 'Gráfica',
  OFICINA: 'Oficina',
}

/** Dias da semana na ordem em que são exibidos nos formulários de posto (RN-04). */
export const DIAS_DA_SEMANA: { valor: DiaDaSemana; rotulo: string; abreviado: string }[] = [
  { valor: 'SEG', rotulo: 'Segunda-feira', abreviado: 'Seg' },
  { valor: 'TER', rotulo: 'Terça-feira', abreviado: 'Ter' },
  { valor: 'QUA', rotulo: 'Quarta-feira', abreviado: 'Qua' },
  { valor: 'QUI', rotulo: 'Quinta-feira', abreviado: 'Qui' },
  { valor: 'SEX', rotulo: 'Sexta-feira', abreviado: 'Sex' },
  { valor: 'SAB', rotulo: 'Sábado', abreviado: 'Sáb' },
  { valor: 'DOM', rotulo: 'Domingo', abreviado: 'Dom' },
]

/** Siglas das unidades federativas, para os campos de UF. */
export const UNIDADES_FEDERATIVAS = [
  'AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG', 'MS', 'MT',
  'PA', 'PB', 'PE', 'PI', 'PR', 'RJ', 'RN', 'RO', 'RR', 'RS', 'SC', 'SE', 'SP', 'TO',
] as const
