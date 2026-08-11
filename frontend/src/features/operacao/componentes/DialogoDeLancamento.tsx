import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, AlertTriangle, CheckCircle2 } from 'lucide-react'
import { useState } from 'react'

import { TIPOS_DE_SERVICO } from '../tipos'
import { Button } from '@/components/ui/button'
import { CampoDeFormulario } from '@/components/ui/campo-de-formulario'
import {
  Dialog,
  DialogCabecalho,
  DialogConteudo,
  DialogCorpo,
  DialogDescricao,
  DialogRodape,
  DialogTitulo,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'

export type TipoDeLancamento = 'KM' | 'ABASTECIMENTO' | 'SERVICO'

interface Props {
  contratoId: number
  tipo: TipoDeLancamento
  aberto: boolean
  aoFechar: () => void
}

const TITULOS: Record<TipoDeLancamento, { titulo: string; descricao: string }> = {
  KM: {
    titulo: 'Lançar quilometragem',
    descricao:
      'O KM inicial não pode ser menor que o final do registro anterior, nem o final maior que o inicial do seguinte (RN-03).',
  },
  ABASTECIMENTO: {
    titulo: 'Lançar abastecimento',
    descricao:
      'Um por dia. Fora de posto credenciado da obra ou de dia autorizado, o lançamento exige justificativa e entra marcado como não conforme (RN-04).',
  },
  SERVICO: {
    titulo: 'Lançar serviço',
    descricao:
      'Lava-jato tem limite de um por semana; o segundo exige justificativa (RN-05). Borracharia e para-brisas não têm limite.',
  },
}

/**
 * Formulário de lançamento da operação mensal.
 *
 * Um diálogo para os três tipos porque o gesto é o mesmo — data, valor, quem prestou — e
 * a diferença está nas regras, que o servidor aplica. Três formulários separados
 * divergiriam na primeira correção feita em um só.
 *
 * O ponto de desenho é a **conformidade antecipada** do abastecimento: assim que o posto
 * e a data estão escolhidos, a tela consulta o servidor e mostra o que está fora da regra,
 * junto com o campo de justificativa que a RN-04 vai exigir. Sem isso, o usuário preenche
 * tudo, envia, e só então descobre que aquele posto não atende à obra — tendo de reabrir
 * o formulário para escrever uma justificativa que ninguém avisou que seria necessária.
 */
export function DialogoDeLancamento({ contratoId, tipo, aberto, aoFechar }: Props) {
  const clienteDeConsultas = useQueryClient()

  const [data, definirData] = useState('')
  const [kmInicial, definirKmInicial] = useState('')
  const [kmFinal, definirKmFinal] = useState('')
  const [origem, definirOrigem] = useState('')
  const [destino, definirDestino] = useState('')
  const [postoId, definirPostoId] = useState('')
  const [valor, definirValor] = useState('')
  const [litros, definirLitros] = useState('')
  const [km, definirKm] = useState('')
  const [tipoDeServico, definirTipoDeServico] = useState('LAVA_JATO')
  const [fornecedorId, definirFornecedorId] = useState('')
  const [descricao, definirDescricao] = useState('')
  const [justificativa, definirJustificativa] = useState('')

  const postos = useQuery({
    queryKey: ['fornecedores', 'postos'],
    enabled: aberto && tipo === 'ABASTECIMENTO',
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/fornecedores', {
          params: { query: { tipo: 'POSTO', page: 0, size: 200 } },
        }),
      ),
  })

  const prestadores = useQuery({
    queryKey: ['fornecedores', 'servicos', tipoDeServico],
    enabled: aberto && tipo === 'SERVICO',
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/fornecedores', {
          params: {
            query: {
              tipo: tipoDeServico as 'LAVA_JATO' | 'BORRACHARIA' | 'PARA_BRISAS',
              page: 0,
              size: 200,
            },
          },
        }),
      ),
  })

  /**
   * Avaliação prévia da RN-04.
   *
   * Só dispara com posto e data preenchidos — antes disso não há o que avaliar, e um
   * aviso prematuro acusaria uma irregularidade que ninguém cometeu.
   */
  const conformidade = useQuery({
    queryKey: ['operacao', 'conformidade', contratoId, postoId, data],
    enabled: aberto && tipo === 'ABASTECIMENTO' && postoId !== '' && data !== '',
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/abastecimentos/conformidade', {
          params: { path: { contratoId }, query: { postoId: Number(postoId), data } },
        }),
      ),
  })

  const envio = useMutation({
    mutationFn: async () => {
      if (tipo === 'KM') {
        return exigirSucesso(
          await api.POST('/api/v1/operacao/contratos/{contratoId}/km', {
            params: { path: { contratoId } },
            body: {
              data,
              kmInicial: Number(kmInicial),
              kmFinal: Number(kmFinal),
              origem: origem || undefined,
              destino: destino || undefined,
            },
          }),
        )
      }
      if (tipo === 'ABASTECIMENTO') {
        return exigirSucesso(
          await api.POST('/api/v1/operacao/contratos/{contratoId}/abastecimentos', {
            params: { path: { contratoId } },
            body: {
              postoId: Number(postoId),
              data,
              valor: Number(valor),
              litros: litros ? Number(litros) : undefined,
              km: km ? Number(km) : undefined,
              justificativa: justificativa || undefined,
            },
          }),
        )
      }
      return exigirSucesso(
        await api.POST('/api/v1/operacao/contratos/{contratoId}/servicos', {
          params: { path: { contratoId } },
          body: {
            tipo: tipoDeServico,
            fornecedorId: fornecedorId ? Number(fornecedorId) : undefined,
            data,
            valor: Number(valor),
            descricao: descricao || undefined,
            justificativa: justificativa || undefined,
          },
        }),
      )
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
      limpar()
      aoFechar()
    },
  })

  function limpar() {
    definirData('')
    definirKmInicial('')
    definirKmFinal('')
    definirOrigem('')
    definirDestino('')
    definirPostoId('')
    definirValor('')
    definirLitros('')
    definirKm('')
    definirFornecedorId('')
    definirDescricao('')
    definirJustificativa('')
    envio.reset()
  }

  const foraDaRegra = conformidade.data && !conformidade.data.conforme
  const podeEnviar =
    data !== '' &&
    !envio.isPending &&
    (tipo === 'KM'
      ? kmInicial !== '' && kmFinal !== ''
      : tipo === 'ABASTECIMENTO'
        ? postoId !== '' && valor !== '' && (!foraDaRegra || justificativa.trim() !== '')
        : valor !== '')

  const { titulo, descricao: explicacao } = TITULOS[tipo]

  return (
    <Dialog
      open={aberto}
      onOpenChange={(estaAberto) => {
        if (!estaAberto) {
          limpar()
          aoFechar()
        }
      }}
    >
      <DialogConteudo className="sm:max-w-lg">
        <DialogCabecalho>
          <DialogTitulo>{titulo}</DialogTitulo>
          <DialogDescricao>{explicacao}</DialogDescricao>
        </DialogCabecalho>

        <DialogCorpo className="space-y-4">
          {envio.isError ? (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/50 px-3 py-2.5"
            >
              <AlertCircle className="mt-0.5 size-4 shrink-0 text-critico" aria-hidden="true" />
              <p className="text-sm text-texto">{mensagemDeErro(envio.error)}</p>
            </div>
          ) : null}

          <CampoDeFormulario id="data-do-lancamento" rotulo="Data" obrigatorio>
            <Input
              id="data-do-lancamento"
              type="date"
              value={data}
              onChange={(evento) => definirData(evento.target.value)}
            />
          </CampoDeFormulario>

          {tipo === 'KM' ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <CampoDeFormulario id="km-inicial" rotulo="KM inicial" obrigatorio>
                  <Input
                    id="km-inicial"
                    type="number"
                    min={0}
                    value={kmInicial}
                    onChange={(evento) => definirKmInicial(evento.target.value)}
                  />
                </CampoDeFormulario>
                <CampoDeFormulario id="km-final" rotulo="KM final" obrigatorio>
                  <Input
                    id="km-final"
                    type="number"
                    min={0}
                    value={kmFinal}
                    onChange={(evento) => definirKmFinal(evento.target.value)}
                  />
                </CampoDeFormulario>
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <CampoDeFormulario id="origem" rotulo="Origem">
                  <Input
                    id="origem"
                    value={origem}
                    onChange={(evento) => definirOrigem(evento.target.value)}
                    maxLength={180}
                  />
                </CampoDeFormulario>
                <CampoDeFormulario id="destino" rotulo="Destino">
                  <Input
                    id="destino"
                    value={destino}
                    onChange={(evento) => definirDestino(evento.target.value)}
                    maxLength={180}
                  />
                </CampoDeFormulario>
              </div>
            </>
          ) : null}

          {tipo === 'ABASTECIMENTO' ? (
            <>
              <CampoDeFormulario
                id="posto"
                rotulo="Posto"
                obrigatorio
                dica="Um abastecimento sem posto é registro incompleto, não irregularidade."
              >
                <Select
                  id="posto"
                  value={postoId}
                  onChange={(evento) => definirPostoId(evento.target.value)}
                >
                  <option value="">Selecione…</option>
                  {(postos.data?.conteudo ?? []).map((posto) => (
                    <option key={posto.id} value={posto.id}>
                      {posto.nome}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>

              {/* A RN-04 avisada antes do envio, não descoberta no erro. */}
              {conformidade.data ? (
                <div
                  aria-live="polite"
                  className={`flex items-start gap-2.5 rounded-[var(--radius-base)] px-3 py-2.5 text-sm ${
                    foraDaRegra ? 'bg-atencao-suave/50' : 'bg-sucesso-suave/40'
                  }`}
                >
                  {foraDaRegra ? (
                    <AlertTriangle className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
                  ) : (
                    <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-sucesso" aria-hidden="true" />
                  )}
                  <span className="text-texto">
                    {foraDaRegra
                      ? conformidade.data.motivos.join(' ')
                      : 'Posto credenciado e dia autorizado.'}
                  </span>
                </div>
              ) : null}

              <div className="grid gap-4 sm:grid-cols-3">
                <CampoDeFormulario id="valor" rotulo="Valor (R$)" obrigatorio>
                  <Input
                    id="valor"
                    type="number"
                    step="0.01"
                    min={0}
                    value={valor}
                    onChange={(evento) => definirValor(evento.target.value)}
                  />
                </CampoDeFormulario>
                <CampoDeFormulario id="litros" rotulo="Litros">
                  <Input
                    id="litros"
                    type="number"
                    step="0.001"
                    min={0}
                    value={litros}
                    onChange={(evento) => definirLitros(evento.target.value)}
                  />
                </CampoDeFormulario>
                <CampoDeFormulario id="km-do-abastecimento" rotulo="KM">
                  <Input
                    id="km-do-abastecimento"
                    type="number"
                    min={0}
                    value={km}
                    onChange={(evento) => definirKm(evento.target.value)}
                  />
                </CampoDeFormulario>
              </div>
            </>
          ) : null}

          {tipo === 'SERVICO' ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <CampoDeFormulario id="tipo-de-servico" rotulo="Tipo" obrigatorio>
                  <Select
                    id="tipo-de-servico"
                    value={tipoDeServico}
                    onChange={(evento) => {
                      definirTipoDeServico(evento.target.value)
                      definirFornecedorId('')
                    }}
                  >
                    {Object.entries(TIPOS_DE_SERVICO).map(([valorDoTipo, rotulo]) => (
                      <option key={valorDoTipo} value={valorDoTipo}>
                        {rotulo}
                      </option>
                    ))}
                  </Select>
                </CampoDeFormulario>
                <CampoDeFormulario id="valor-do-servico" rotulo="Valor (R$)" obrigatorio>
                  <Input
                    id="valor-do-servico"
                    type="number"
                    step="0.01"
                    min={0}
                    value={valor}
                    onChange={(evento) => definirValor(evento.target.value)}
                  />
                </CampoDeFormulario>
              </div>
              <CampoDeFormulario id="fornecedor-do-servico" rotulo="Fornecedor">
                <Select
                  id="fornecedor-do-servico"
                  value={fornecedorId}
                  onChange={(evento) => definirFornecedorId(evento.target.value)}
                >
                  <option value="">Não informado</option>
                  {(prestadores.data?.conteudo ?? []).map((prestador) => (
                    <option key={prestador.id} value={prestador.id}>
                      {prestador.nome}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>
              <CampoDeFormulario id="descricao-do-servico" rotulo="Descrição">
                <Input
                  id="descricao-do-servico"
                  value={descricao}
                  onChange={(evento) => definirDescricao(evento.target.value)}
                  maxLength={300}
                />
              </CampoDeFormulario>
            </>
          ) : null}

          {/* A justificativa aparece quando a regra vai pedi-la — no abastecimento, assim
              que a avaliação reprova; no serviço, sempre, porque o limite semanal só é
              conhecido pelo servidor. */}
          {tipo !== 'KM' ? (
            <CampoDeFormulario
              id="justificativa"
              rotulo="Justificativa"
              obrigatorio={Boolean(foraDaRegra)}
              dica={
                tipo === 'ABASTECIMENTO'
                  ? 'Obrigatória quando o lançamento está fora das condições autorizadas.'
                  : 'Obrigatória se já houver lava-jato nesta semana.'
              }
            >
              <Textarea
                id="justificativa"
                rows={2}
                value={justificativa}
                onChange={(evento) => definirJustificativa(evento.target.value)}
              />
            </CampoDeFormulario>
          ) : null}
        </DialogCorpo>

        <DialogRodape>
          <Button
            variante="secundaria"
            onClick={() => {
              limpar()
              aoFechar()
            }}
          >
            Cancelar
          </Button>
          <Button disabled={!podeEnviar} onClick={() => envio.mutate()}>
            {envio.isPending ? 'Lançando…' : 'Lançar'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
