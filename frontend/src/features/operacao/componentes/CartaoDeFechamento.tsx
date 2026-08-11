import { AlertTriangle, CheckCircle2, Gauge, TrendingUp } from 'lucide-react'

import type { Fechamento } from '../tipos'
import { formatarMoeda } from '@/lib/formatters'

/**
 * O fechamento de uma competência (RN-06, RN-21).
 *
 * Todos os números vêm calculados do servidor a cada leitura — nenhum deles é editável,
 * e a tela não oferece nem sugere edição. É a tradução visual da RN-21: o que se confere
 * aqui são os lançamentos, e a única coisa que o gestor grava é o "conferi".
 *
 * O excedente de KM ganha destaque próprio quando existe. É a informação que a planilha
 * nunca deu a tempo — quando aparecia, a fatura já tinha chegado com a cobrança.
 */
export function CartaoDeFechamento({ fechamento }: { fechamento: Fechamento }) {
  const percentualDoPacote =
    fechamento.pacoteContratado && fechamento.pacoteContratado > 0
      ? Math.min(200, Math.round((fechamento.kmPercorrido / fechamento.pacoteContratado) * 100))
      : null

  return (
    <section className="rounded-[var(--radius-base)] border border-borda bg-superficie">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-borda px-4 py-3">
        <div>
          <h2 className="font-semibold text-texto">Fechamento de {fechamento.competencia}</h2>
          <p className="mt-0.5 text-sm text-texto-suave">
            Calculado a partir dos lançamentos. Nenhum valor aqui é digitado (RN-21).
          </p>
        </div>
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-medium ${
            fechamento.status === 'CONFERIDO'
              ? 'bg-sucesso-suave text-sucesso'
              : 'bg-fundo-alternativo text-texto-suave'
          }`}
        >
          {fechamento.status === 'CONFERIDO' ? 'Conferido' : 'Aberto'}
        </span>
      </header>

      <div className="p-4">
        {/* Quilometragem e franquia */}
        <div className="rounded-[var(--radius-base)] bg-fundo-alternativo px-3 py-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="flex items-center gap-2 text-sm font-medium text-texto">
              <Gauge className="size-4 text-marca-forte" aria-hidden="true" />
              {fechamento.kmPercorrido.toLocaleString('pt-BR')} km rodados
            </p>
            <p className="text-sm text-texto-suave">
              {fechamento.kmInicial != null
                ? `${fechamento.kmInicial.toLocaleString('pt-BR')} → ${(fechamento.kmFinal ?? 0).toLocaleString('pt-BR')}`
                : 'sem registros de KM no mês'}
              {fechamento.pacoteContratado
                ? ` · franquia ${fechamento.pacoteContratado.toLocaleString('pt-BR')} km`
                : ' · sem franquia contratada'}
            </p>
          </div>

          {percentualDoPacote != null ? (
            <div className="mt-2">
              <div
                className="h-2 overflow-hidden rounded-full bg-borda"
                role="img"
                aria-label={`${String(percentualDoPacote)}% da franquia mensal consumidos`}
              >
                <div
                  className={`h-full rounded-full transition-[width] duration-500 ${
                    fechamento.estourouOPacote ? 'bg-critico' : 'bg-sucesso'
                  }`}
                  style={{ width: `${String(Math.min(100, percentualDoPacote))}%` }}
                />
              </div>
              <p className="mt-1 text-xs text-texto-tenue">
                {percentualDoPacote}% da franquia
              </p>
            </div>
          ) : null}
        </div>

        {/* Excedente — a informação que a RN-06 manda sinalizar. */}
        {fechamento.estourouOPacote ? (
          <div className="mt-3 flex items-start gap-2.5 rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/40 px-3 py-2.5">
            <TrendingUp className="mt-0.5 size-4 shrink-0 text-critico" aria-hidden="true" />
            <div className="text-sm">
              <p className="font-medium text-texto">
                {fechamento.kmExcedente.toLocaleString('pt-BR')} km acima da franquia
              </p>
              <p className="text-texto-suave">
                {fechamento.vigenciaIndisponivel ? (
                  <>
                    O custo não pôde ser estimado: não há tabela de preços da locadora para
                    o ano desta competência (RN-14).
                  </>
                ) : (
                  <>
                    {fechamento.kmExcedente.toLocaleString('pt-BR')} km ×{' '}
                    {formatarMoeda(fechamento.valorDoKmExcedente)} ={' '}
                    <strong className="text-texto">
                      {formatarMoeda(fechamento.custoDoExcedente)}
                    </strong>{' '}
                    de custo estimado.
                  </>
                )}
              </p>
            </div>
          </div>
        ) : null}

        {/* Custos do mês */}
        <dl className="mt-3 grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
          {[
            {
              rotulo: 'Combustível',
              valor: fechamento.consumoTotal,
              detalhe: `${String(fechamento.quantidadeDeAbastecimentos)} abastecimento(s)`,
            },
            { rotulo: 'Lava-jato', valor: fechamento.custoDeLavaJato },
            { rotulo: 'Borracharia', valor: fechamento.custoDeBorracharia },
            { rotulo: 'Para-brisas', valor: fechamento.custoDeParaBrisas },
            {
              rotulo: 'KM excedente',
              valor: fechamento.custoDoExcedente,
              detalhe: fechamento.vigenciaIndisponivel ? 'sem vigência' : undefined,
            },
          ].map((item, indice) => (
            <div
              key={item.rotulo}
              className="surgir rounded-[var(--radius-base)] border border-borda px-3 py-2"
              style={{ '--indice': indice } as React.CSSProperties}
            >
              <dt className="text-[0.68rem] font-semibold uppercase tracking-wide text-texto-tenue">
                {item.rotulo}
              </dt>
              <dd className="mt-0.5 font-medium text-texto">{formatarMoeda(item.valor)}</dd>
              {item.detalhe ? (
                <dd className="text-[0.68rem] text-texto-tenue">{item.detalhe}</dd>
              ) : null}
            </div>
          ))}
        </dl>

        <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-borda pt-3">
          <p className="text-sm text-texto-suave">
            {fechamento.lancamentosNaoConformes > 0 ? (
              <span className="inline-flex items-center gap-1.5 text-atencao">
                <AlertTriangle className="size-4" aria-hidden="true" />
                {fechamento.lancamentosNaoConformes} lançamento(s) não conforme(s) no mês
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5">
                <CheckCircle2 className="size-4 text-sucesso" aria-hidden="true" />
                Nenhuma não conformidade no mês
              </span>
            )}
          </p>
          <p className="text-right">
            <span className="block text-[0.68rem] font-semibold uppercase tracking-wide text-texto-tenue">
              Custo total do mês
            </span>
            <span className="text-xl font-semibold text-texto">
              {formatarMoeda(fechamento.custoTotal)}
            </span>
          </p>
        </div>
      </div>
    </section>
  )
}
