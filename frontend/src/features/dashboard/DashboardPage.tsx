import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Building2, Car, Fuel, IdCard, Radio, Store, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { EstadoDeErro } from '@/components/ui/estados'
import { MedidorDeCarregamento } from '@/components/ui/medidor-de-carregamento'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { CardDeVeiculo } from '@/features/painel/CardDeVeiculo'
import { CartaoDeIndicador } from '@/features/painel/CartaoDeIndicador'
import { CentralDePendencias } from '@/features/painel/CentralDePendencias'
import { DistribuicaoEmBarras } from '@/features/painel/DistribuicaoEmBarras'
import { BarrasDaMatriz } from '@/features/painel/graficos/BarrasDaMatriz'
import { LinhasDePreco } from '@/features/painel/graficos/LinhasDePreco'
import { RoscaDaFrota } from '@/features/painel/graficos/RoscaDaFrota'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarMoeda, formatarNumero } from '@/lib/formatters'

/** Quantos veículos aparecem antes do usuário pedir para ver todos. */
const CARDS_INICIAIS = 12

/**
 * Painel inicial.
 *
 * A leitura é vertical e em três tempos: primeiro os números que resumem a operação,
 * depois o que exige ação, e só então a análise que explica os padrões. Os veículos
 * ativos vêm logo abaixo, um card por veículo, como porta de entrada para o painel
 * individual.
 *
 * Todos os números derivam dos cadastros. Onde há estimativa, o cartão diz. Séries
 * temporais — evolução de custo mês a mês — dependem dos fechamentos e entram na
 * Fase 3; a curva de preço aqui é real, mas é uma curva de tabela, não de histórico.
 */
export function DashboardPage() {
  const { usuario } = useAutenticacao()

  const indicadores = useQuery({
    queryKey: ['painel', 'indicadores'],
    queryFn: async () => exigirSucesso(await api.GET('/api/v1/painel/indicadores', {})),
  })
  const pendencias = useQuery({
    queryKey: ['painel', 'pendencias'],
    queryFn: async () => exigirSucesso(await api.GET('/api/v1/painel/pendencias', {})),
  })
  const analises = useQuery({
    queryKey: ['painel', 'analises'],
    queryFn: async () => exigirSucesso(await api.GET('/api/v1/painel/analises', {})),
  })
  // Traz veículo, obra e condutor de uma vez: os cards precisam dos três, e buscá-los
  // separadamente por card seriam três consultas por veículo.
  const ativos = useQuery({
    queryKey: ['painel', 'veiculos-em-operacao'],
    queryFn: async () => exigirSucesso(await api.GET('/api/v1/painel/veiculos-em-operacao', {})),
  })

  const carregando =
    indicadores.isPending || pendencias.isPending || analises.isPending || ativos.isPending

  if (carregando) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <MedidorDeCarregamento rotulo="Apurando indicadores da frota" />
      </div>
    )
  }

  if (indicadores.isError || pendencias.isError || analises.isError) {
    return (
      <div className="mx-auto max-w-7xl px-6 py-8">
        <EstadoDeErro
          erro={indicadores.error ?? pendencias.error ?? analises.error}
          aoTentarNovamente={() => {
            void indicadores.refetch()
            void pendencias.refetch()
            void analises.refetch()
          }}
        />
      </div>
    )
  }

  const dados = indicadores.data
  const analise = analises.data
  const veiculosAtivos = ativos.data ?? []
  const percentualEmUso =
    dados.veiculosNaFrota > 0 ? Math.round((dados.veiculosEmUso / dados.veiculosNaFrota) * 100) : 0
  const percentualRastreado =
    dados.veiculosNaFrota > 0
      ? Math.round((dados.veiculosComRastreador / dados.veiculosNaFrota) * 100)
      : 0

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      {/* Abertura com o laranja da marca: dá o tom antes de qualquer número. */}
      <header className="surgir relative mb-8 overflow-hidden rounded-[calc(var(--radius-base)*2)] border border-borda bg-superficie p-6">
        <span
          aria-hidden="true"
          className="pointer-events-none absolute -right-24 -top-32 size-80 rounded-full opacity-[0.10]"
          style={{ background: 'radial-gradient(circle, var(--marca) 0%, transparent 68%)' }}
        />
        <div className="relative flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wider text-marca-forte">
              Gestão de frotas · {dados.anoDeReferencia}
            </p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight text-texto">
              Olá, {primeiroNome(usuario?.nome)}
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-relaxed text-texto-suave">
              {formatarNumero(dados.veiculosNaFrota)} veículos de {formatarNumero(dados.obras)} obras,
              distribuídos entre {analise.matrizDaFrota.locadoras.length} parceiras. Tudo apurado dos
              cadastros — nada estimado por amostragem.
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs uppercase tracking-wide text-texto-tenue">Custo mensal estimado</p>
            <p className="mt-0.5 text-4xl font-semibold tabular-nums tracking-tight text-marca-forte">
              {formatarMoeda(dados.custoMensalEstimado)}
            </p>
          </div>
        </div>
      </header>

      <section aria-label="Indicadores da frota" className="mb-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <CartaoDeIndicador
          indice={0}
          rotulo="Veículos em uso"
          valor={formatarNumero(dados.veiculosEmUso)}
          detalhe={`${String(percentualEmUso)}% da frota · ${formatarNumero(dados.veiculosNaFrota - dados.veiculosEmUso)} devolvidos ou disponíveis`}
          icone={Car}
          destaque
        />
        <CartaoDeIndicador
          indice={1}
          rotulo="Obras ativas"
          valor={formatarNumero(dados.obrasAtivas)}
          detalhe={`de ${formatarNumero(dados.obras)} obras cadastradas`}
          icone={Building2}
        />
        <CartaoDeIndicador
          indice={2}
          rotulo="Condutores ativos"
          valor={formatarNumero(dados.condutoresAtivos)}
          detalhe={`de ${formatarNumero(dados.condutores)} no cadastro`}
          icone={IdCard}
        />
        <CartaoDeIndicador
          indice={3}
          rotulo="Reajuste médio"
          valor={
            analise.vigencias.variacaoMedia != null
              ? `${analise.vigencias.variacaoMedia.toFixed(1).replace('.', ',')}%`
              : '—'
          }
          detalhe={
            analise.vigencias.anoAnterior != null
              ? `Tabelas de ${String(analise.vigencias.anoAnterior)} para ${String(analise.vigencias.anoAtual)}`
              : 'Sem duas vigências para comparar'
          }
          icone={TrendingUp}
        />
      </section>

      <section aria-label="Equipamentos e rede credenciada" className="mb-8 grid gap-3 sm:grid-cols-3">
        <CartaoDeIndicador
          indice={4}
          rotulo="Com rastreador"
          valor={formatarNumero(dados.veiculosComRastreador)}
          detalhe={`${String(percentualRastreado)}% da frota rastreada`}
          icone={Radio}
        />
        <CartaoDeIndicador
          indice={5}
          rotulo="Veículos a diesel"
          valor={formatarNumero(dados.veiculosADiesel)}
          detalhe="Exigem teste de fumaça preta na retirada (RN-09)"
          icone={Fuel}
        />
        <CartaoDeIndicador
          indice={6}
          rotulo="Fornecedores ativos"
          valor={formatarNumero(dados.fornecedoresAtivos)}
          detalhe="Postos, lava-jatos, borracharias e demais credenciados"
          icone={Store}
        />
      </section>

      <div className="mb-8 grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <CentralDePendencias central={pendencias.data} />
        </div>
        <CustoPorLocadora custos={dados.custoPorLocadora} ano={dados.anoDeReferencia} />
      </div>

      {/* Veículos em operação: um card por veículo, cada um levando ao seu painel. */}
      <section aria-labelledby="titulo-veiculos" className="mb-8">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="titulo-veiculos" className="text-lg font-semibold tracking-tight text-texto">
              Veículos em operação
            </h2>
            <p className="mt-0.5 text-sm text-texto-suave">
              {formatarNumero(veiculosAtivos.length)} veículos com contrato ativo, com condutor e obra.
              Clique em um card para abrir o painel completo do veículo.
            </p>
          </div>
          <Button variante="secundaria" tamanho="pequeno" asChild>
            <Link to="/cadastros/veiculos">
              Ver a frota inteira
              <ArrowRight aria-hidden="true" />
            </Link>
          </Button>
        </div>

        {veiculosAtivos.length === 0 ? (
          <Card className="p-8 text-center">
            <p className="text-sm text-texto-suave">Nenhum veículo em uso no momento.</p>
          </Card>
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {veiculosAtivos.slice(0, CARDS_INICIAIS).map((veiculo, indice) => (
                <CardDeVeiculo key={veiculo.veiculoId} veiculo={veiculo} indice={indice} />
              ))}
            </div>
            {veiculosAtivos.length > CARDS_INICIAIS ? (
              <details className="group mt-3">
                <summary className="cursor-pointer list-none rounded-[var(--radius-base)] border border-borda bg-superficie px-4 py-2.5 text-center text-sm text-texto-suave transition-colors hover:border-borda-forte hover:text-texto">
                  Mostrar os outros {formatarNumero(veiculosAtivos.length - CARDS_INICIAIS)} veículos
                  <span className="hidden group-open:inline"> — recolher</span>
                </summary>
                <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                  {veiculosAtivos.slice(CARDS_INICIAIS).map((veiculo, indice) => (
                    <CardDeVeiculo key={veiculo.veiculoId} veiculo={veiculo} indice={indice} />
                  ))}
                </div>
              </details>
            ) : null}
          </>
        )}
      </section>

      {/* Análise: cruzamentos que nenhum eixo isolado revela. */}
      <section aria-labelledby="titulo-analise">
        <h2 id="titulo-analise" className="mb-4 text-lg font-semibold tracking-tight text-texto">
          Análise
        </h2>

        <div className="mb-4 grid gap-4 lg:grid-cols-2">
          <RoscaDaFrota fatias={dados.veiculosPorCategoria} />
          <BarrasDaMatriz matriz={analise.matrizDaFrota} />
        </div>

        <div className="mb-4 grid gap-4 lg:grid-cols-2">
          <LinhasDePreco curvas={analise.curvasDePreco} ano={analise.anoDeReferencia} />
          <ReajustePorGrupo vigencias={analise.vigencias} />
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <DistribuicaoEmBarras
            titulo="Obras por estado"
            descricao="Dispersão geográfica das frentes de trabalho."
            fatias={dados.obrasPorUf}
            unidade="obra"
            limite={8}
          />
          <DistribuicaoEmBarras
            titulo="Rede credenciada por tipo"
            descricao="Fornecedores ativos disponíveis para lançamento."
            fatias={dados.fornecedoresPorTipo}
            unidade="fornecedor"
          />
        </div>
      </section>
    </div>
  )
}

/** Maiores reajustes entre as duas vigências — barras horizontais, uma cor só. */
function ReajustePorGrupo({
  vigencias,
}: {
  vigencias: {
    anoAnterior?: number
    anoAtual?: number
    variacaoMedia?: number
    reajustes: {
      locadora: string
      grupo: string
      pacoteKm: number
      valorAnterior: number
      valorAtual: number
      variacaoPercentual: number
    }[]
  }
}) {
  const maiores = vigencias.reajustes.slice(0, 6)
  const maiorVariacao = maiores[0]?.variacaoPercentual ?? 1

  return (
    <Card className="p-5">
      <h3 className="text-sm font-semibold text-texto">
        Maiores reajustes · {vigencias.anoAnterior ?? '—'} → {vigencias.anoAtual ?? '—'}
      </h3>
      <p className="mt-0.5 text-xs leading-relaxed text-texto-suave">
        {vigencias.variacaoMedia != null
          ? `A média subiu ${vigencias.variacaoMedia.toFixed(2).replace('.', ',')}%, mas os grupos abaixo subiram bem mais — são os que merecem renegociação.`
          : 'Sem duas vigências cadastradas para comparar.'}
      </p>

      {maiores.length === 0 ? (
        <p className="py-8 text-center text-sm text-texto-tenue">
          Cadastre duas vigências da mesma locadora para ver a variação.
        </p>
      ) : (
        <ul className="mt-4 space-y-3">
          {maiores.map((reajuste, indice) => (
            <li
              key={`${reajuste.locadora}-${reajuste.grupo}-${String(reajuste.pacoteKm)}`}
              className="surgir"
              style={{ '--indice': indice } as React.CSSProperties}
            >
              <div className="flex items-baseline justify-between gap-2">
                <span className="min-w-0 truncate text-sm text-texto">
                  <span className="font-mono font-medium">{reajuste.grupo}</span>
                  <span className="text-texto-tenue">
                    {' '}
                    · {reajuste.locadora} · {formatarNumero(reajuste.pacoteKm)} km
                  </span>
                </span>
                <span className="shrink-0 text-sm font-semibold tabular-nums text-texto">
                  +{reajuste.variacaoPercentual.toFixed(2).replace('.', ',')}%
                </span>
              </div>
              <div className="mt-1 flex items-center gap-2">
                <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-fundo-alternativo">
                  <span
                    className="block h-full rounded-full bg-marca transition-[width] duration-700 ease-out"
                    style={{
                      width: `${String(Math.max((reajuste.variacaoPercentual / maiorVariacao) * 100, 3))}%`,
                    }}
                  />
                </div>
                <span className="shrink-0 text-xs tabular-nums text-texto-tenue">
                  {formatarMoeda(reajuste.valorAnterior)} → {formatarMoeda(reajuste.valorAtual)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** Custo estimado por locadora — poucos itens, então uma lista lê melhor que um gráfico. */
function CustoPorLocadora({
  custos,
  ano,
}: {
  custos: { locadora: string; veiculos: number; custoMensalEstimado: number }[]
  ano: number
}) {
  const total = custos.reduce((soma, custo) => soma + custo.custoMensalEstimado, 0)

  return (
    <Card className="p-5">
      <h3 className="text-sm font-semibold text-texto">Custo por locadora</h3>
      <p className="mt-0.5 text-xs text-texto-suave">
        Estimativa mensal na vigência de {ano}. Usa o pacote mais barato de cada grupo — o valor
        real depende do pacote contratado.
      </p>

      {custos.length === 0 ? (
        <p className="py-8 text-center text-sm text-texto-tenue">
          Nenhuma locadora com tabela de preços vigente.
        </p>
      ) : (
        <ul className="mt-4 space-y-3">
          {custos.map((custo, indice) => {
            const participacao = total > 0 ? (custo.custoMensalEstimado / total) * 100 : 0
            return (
              <li key={custo.locadora} className="surgir" style={{ '--indice': indice } as React.CSSProperties}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className="truncate text-sm font-medium text-texto">{custo.locadora}</span>
                  <span className="shrink-0 text-sm font-semibold tabular-nums text-texto">
                    {formatarMoeda(custo.custoMensalEstimado)}
                  </span>
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-fundo-alternativo">
                    <span
                      className="block h-full rounded-full bg-marca transition-[width] duration-700 ease-out"
                      style={{ width: `${String(participacao)}%` }}
                    />
                  </div>
                  <span className="shrink-0 text-xs tabular-nums text-texto-tenue">
                    {formatarNumero(custo.veiculos)} veíc.
                  </span>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}

function primeiroNome(nome: string | undefined): string {
  if (!nome) {
    return 'bem-vindo'
  }
  return nome.split(' ')[0] ?? nome
}
