import { useQuery } from '@tanstack/react-query'
import {
  ArrowLeft,
  Building2,
  Car,
  Fuel,
  Gauge,
  Info,
  Radio,
  Sticker,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { EstadoDeErro } from '@/components/ui/estados'
import { MedidorDeCarregamento } from '@/components/ui/medidor-de-carregamento'
import {
  Tabela,
  TabelaCabecalho,
  TabelaCelula,
  TabelaColuna,
  TabelaCorpo,
  TabelaLinha,
} from '@/components/ui/tabela'
import { CartaoDeIndicador } from '@/features/painel/CartaoDeIndicador'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarDataHora, formatarMoeda, formatarNumero } from '@/lib/formatters'

/**
 * Painel de um veículo.
 *
 * Reúne o que hoje exige abrir duas planilhas: o cadastro do veículo e a linha da
 * tabela de preços que se aplica a ele. A grade tarifária é o coração da página —
 * responde "quanto custa este veículo por mês" e "quanto custa cada quilômetro que
 * passar da franquia", que são as duas perguntas que o gestor faz ao negociar um
 * contrato.
 *
 * As seções de contratos, quilometragem e ocorrências chegam nas Fases 2 a 4, quando
 * esses dados existirem. Elas aparecem aqui como marcadores honestos, não como caixas
 * vazias sem explicação.
 */
export function VeiculoPage() {
  const { id } = useParams<{ id: string }>()
  const veiculoId = Number(id)

  const consulta = useQuery({
    queryKey: ['painel', 'veiculo', veiculoId],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/painel/veiculos/{id}', { params: { path: { id: veiculoId } } }),
      ),
    enabled: Number.isFinite(veiculoId),
  })

  if (consulta.isPending) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <MedidorDeCarregamento rotulo="Carregando o veículo" />
      </div>
    )
  }

  if (consulta.isError) {
    return (
      <div className="mx-auto max-w-5xl px-6 py-8">
        <VoltarParaVeiculos />
        <EstadoDeErro
          erro={consulta.error}
          aoTentarNovamente={() => {
            void consulta.refetch()
          }}
        />
      </div>
    )
  }

  const { veiculo, grade, motivoSemGrade } = consulta.data
  const menorPacote = grade?.pacotes[0]

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <VoltarParaVeiculos />

      {/* Cabeçalho: a placa domina, porque é como o veículo é identificado na operação. */}
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4 border-b border-borda pb-6">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-mono text-3xl font-semibold tracking-tight text-texto">
              {veiculo.placaFormatada}
            </h1>
            <Badge variante={veiculo.status === 'DISPONIVEL' ? 'sucesso' : 'neutra'}>
              {veiculo.statusDescricao}
            </Badge>
          </div>
          <p className="mt-1.5 text-sm text-texto-suave">
            {veiculo.modelo}
            {veiculo.fabricante ? ` · ${veiculo.fabricante}` : ''}
            {veiculo.anoFabricacao ? ` · ${String(veiculo.anoFabricacao)}` : ''}
            {' · '}
            {veiculo.locadora.nome}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variante="neutra">{veiculo.categoriaDescricao}</Badge>
          {veiculo.exigeTesteFumacaPreta ? (
            <Badge variante="atencao">
              <Fuel className="size-3" aria-hidden="true" />
              Diesel
            </Badge>
          ) : null}
          {veiculo.possuiRastreador ? (
            <Badge variante="informativa">
              <Radio className="size-3" aria-hidden="true" />
              Rastreador
            </Badge>
          ) : null}
          {veiculo.possuiAdesivo ? (
            <Badge variante="neutra">
              <Sticker className="size-3" aria-hidden="true" />
              Adesivo
            </Badge>
          ) : null}
        </div>
      </header>

      <section aria-label="Indicadores do veículo" className="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <CartaoDeIndicador
          indice={0}
          rotulo="Menor mensalidade"
          valor={menorPacote ? formatarMoeda(menorPacote.valorMensal) : '—'}
          detalhe={
            menorPacote
              ? `Pacote de ${formatarNumero(menorPacote.pacoteKm)} km/mês`
              : 'Sem grade tarifária'
          }
          icone={Wallet}
          destaque
        />
        <CartaoDeIndicador
          indice={1}
          rotulo="KM excedente"
          valor={
            menorPacote?.valorKmExcedente != null ? formatarMoeda(menorPacote.valorKmExcedente) : '—'
          }
          detalhe={`Por km acima da franquia · categoria ${veiculo.categoriaDescricao}`}
          icone={Gauge}
        />
        <CartaoDeIndicador
          indice={2}
          rotulo="Grupo tarifário"
          valor={veiculo.grupoTarifario ?? '—'}
          detalhe={grade ? grade.veiculosDoGrupo : 'Não informado'}
          icone={Car}
        />
        <CartaoDeIndicador
          indice={3}
          rotulo="Obra"
          valor={veiculo.codigoInterno ?? '—'}
          detalhe="Código interno registrado no cadastro"
          icone={Building2}
        />
      </section>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {grade ? (
            <Card className="overflow-hidden">
              <header className="border-b border-borda px-5 py-4">
                <h2 className="text-sm font-semibold text-texto">
                  Grade tarifária · {grade.grupo} · {grade.anoVigencia}
                </h2>
                <p className="mt-0.5 text-xs text-texto-suave">
                  Preços da {veiculo.locadora.nome} para o grupo {grade.grupo} ({grade.veiculosDoGrupo}).
                  O KM excedente segue a categoria {veiculo.categoriaDescricao.toLowerCase()}.
                </p>
              </header>
              <Tabela legenda={`Preços por pacote de quilometragem do grupo ${grade.grupo}`}>
                <TabelaCabecalho>
                  <TabelaLinha>
                    <TabelaColuna>Franquia mensal</TabelaColuna>
                    <TabelaColuna numerica>Valor mensal</TabelaColuna>
                    <TabelaColuna numerica>KM excedente</TabelaColuna>
                    <TabelaColuna numerica>Custo de 500 km extras</TabelaColuna>
                  </TabelaLinha>
                </TabelaCabecalho>
                <TabelaCorpo>
                  {grade.pacotes.map((pacote, indice) => (
                    <TabelaLinha
                      key={pacote.pacoteKm}
                      className="surgir"
                      style={{ '--indice': indice } as React.CSSProperties}
                    >
                      <TabelaCelula className="font-medium text-texto">
                        {formatarNumero(pacote.pacoteKm)} km
                      </TabelaCelula>
                      <TabelaCelula numerica className="text-texto">
                        {formatarMoeda(pacote.valorMensal)}
                      </TabelaCelula>
                      <TabelaCelula numerica className="text-texto-suave">
                        {pacote.valorKmExcedente != null ? formatarMoeda(pacote.valorKmExcedente) : '—'}
                      </TabelaCelula>
                      {/*
                        500 km extras é a simulação que responde à pergunta prática:
                        vale mais a pena subir de pacote ou pagar o excedente?
                      */}
                      <TabelaCelula numerica className="text-texto-suave">
                        {pacote.valorKmExcedente != null
                          ? formatarMoeda(pacote.valorKmExcedente * 500)
                          : '—'}
                      </TabelaCelula>
                    </TabelaLinha>
                  ))}
                </TabelaCorpo>
              </Tabela>
            </Card>
          ) : (
            <Card className="flex items-start gap-3 p-5">
              <Info className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
              <div>
                <h2 className="text-sm font-semibold text-texto">Sem grade tarifária</h2>
                <p className="mt-1 text-sm text-texto-suave">{motivoSemGrade}</p>
              </div>
            </Card>
          )}
        </div>

        <div className="space-y-4">
          <Card className="p-5">
            <h2 className="mb-3 text-sm font-semibold text-texto">Cadastro</h2>
            <dl className="space-y-2.5 text-sm">
              <Linha rotulo="Combustível" valor={veiculo.combustivelDescricao} />
              <Linha rotulo="Categoria" valor={veiculo.categoriaDescricao} />
              <Linha rotulo="Locadora" valor={veiculo.locadora.nome} />
              <Linha
                rotulo="Rastreador"
                valor={veiculo.possuiRastreador ? (veiculo.fornecedorRastreador ?? 'Sim') : 'Não'}
              />
              <Linha rotulo="Cadastrado em" valor={formatarDataHora(veiculo.criadoEm)} />
              <Linha rotulo="Última alteração" valor={formatarDataHora(veiculo.atualizadoEm)} />
            </dl>
            {veiculo.observacoes ? (
              <p className="mt-4 whitespace-pre-wrap border-t border-borda pt-3 text-sm text-texto-suave">
                {veiculo.observacoes}
              </p>
            ) : null}
          </Card>

          {/*
            Marcadores honestos: dizem o que vai aparecer aqui e em que fase, em vez de
            mostrar uma caixa vazia que o usuário interpreta como erro.
          */}
          <Card className="p-5">
            <h2 className="mb-1 text-sm font-semibold text-texto">Histórico operacional</h2>
            <p className="text-xs leading-relaxed text-texto-suave">
              Contratos, substituições e trocas de condutor deste veículo aparecem aqui a partir
              da Fase 2. Quilometragem, abastecimentos e fechamento mensal, na Fase 3. Checklists,
              avarias e multas, na Fase 4.
            </p>
            <p className="mt-3 flex items-center gap-1.5 text-xs text-texto-tenue">
              <TrendingUp className="size-3.5" aria-hidden="true" />
              Nenhum número é exibido antes de existir de verdade.
            </p>
          </Card>
        </div>
      </div>
    </div>
  )
}

function VoltarParaVeiculos() {
  return (
    <Button variante="sutil" tamanho="pequeno" className="mb-4 -ml-2" asChild>
      <Link to="/cadastros/veiculos">
        <ArrowLeft aria-hidden="true" />
        Voltar para veículos
      </Link>
    </Button>
  )
}

function Linha({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="shrink-0 text-texto-tenue">{rotulo}</dt>
      <dd className="min-w-0 truncate text-right text-texto">{valor}</dd>
    </div>
  )
}
