import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { z } from 'zod'

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
import { CATEGORIAS_DE_VEICULO, type TabelaPreco } from '@/features/cadastros/tipos'
import { api, exigirSucesso } from '@/lib/api/client'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'

const CATEGORIAS = ['PASSEIO', 'SUV', 'QUATRO_X_QUATRO', 'UTILITARIO'] as const

const esquemaDePacote = z.object({
  pacoteKm: z.string().min(1, 'Informe o pacote'),
  valorMensal: z.string().min(1, 'Informe o valor'),
})

const esquema = z.object({
  locadoraId: z.string().min(1, 'Selecione a locadora'),
  anoVigencia: z.string().min(4, 'Informe o ano'),
  observacoes: z.string().max(2000),
  grupos: z
    .array(
      z.object({
        codigo: z.string().min(1, 'Informe o código'),
        veiculosDoGrupo: z.string().min(1, 'Informe os veículos do grupo'),
        categoria: z.enum(CATEGORIAS),
        pacotes: z.array(esquemaDePacote).min(1, 'Informe ao menos um pacote'),
      }),
    )
    .min(1, 'Cadastre ao menos um grupo tarifário'),
  kmExcedente: z.array(
    z.object({
      categoria: z.enum(CATEGORIAS),
      pacoteKm: z.string(),
      valorKm: z.string().min(1, 'Informe o valor por KM'),
    }),
  ),
})

type DadosDoFormulario = z.infer<typeof esquema>

const GRUPO_VAZIO = {
  codigo: '',
  veiculosDoGrupo: '',
  categoria: 'PASSEIO' as const,
  pacotes: [{ pacoteKm: '3000', valorMensal: '' }],
}

/** Valores iniciais derivados da vigência; o formulário monta já com a grade carregada. */
function valoresDe(tabela: TabelaPreco | null): DadosDoFormulario {
  if (!tabela) {
    return {
      locadoraId: '',
      anoVigencia: String(new Date().getFullYear()),
      observacoes: '',
      grupos: [GRUPO_VAZIO],
      kmExcedente: [],
    }
  }
  return {
    locadoraId: String(tabela.locadora.id),
    anoVigencia: String(tabela.anoVigencia),
    observacoes: tabela.observacoes ?? '',
    grupos: tabela.grupos.map((grupo) => ({
      codigo: grupo.codigo,
      veiculosDoGrupo: grupo.veiculosDoGrupo,
      categoria: grupo.categoria,
      pacotes: grupo.pacotes.map((pacote) => ({
        pacoteKm: String(pacote.pacoteKm),
        valorMensal: String(pacote.valorMensal),
      })),
    })),
    kmExcedente: tabela.kmExcedente.map((preco) => ({
      categoria: preco.categoria,
      pacoteKm: preco.pacoteKm ? String(preco.pacoteKm) : '',
      valorKm: String(preco.valorKm),
    })),
  }
}

function paraNumero(texto: string): number {
  return Number(texto.replace(',', '.').trim())
}

/**
 * Formulário de vigência de tabela de preços (RN-14).
 *
 * A grade é enviada por inteiro: grupos e pacotes removidos aqui deixam de existir na
 * vigência. É o comportamento adequado ao insumo real — a locadora envia uma planilha
 * completa a cada reajuste, e não um conjunto de alterações pontuais.
 */
export function FormularioDeTabelaDePreco({
  tabela,
  aoFechar,
}: {
  tabela: TabelaPreco | null
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)
  const editando = tabela !== null

  const locadoras = useQuery({
    queryKey: ['locadoras', 'selecao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/locadoras', {
          params: { query: { page: 0, size: 200, sort: ['nome,asc'] } },
        }),
      ),
  })

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DadosDoFormulario>({ resolver: zodResolver(esquema), defaultValues: valoresDe(tabela) })

  const grupos = useFieldArray({ control, name: 'grupos' })
  const excedentes = useFieldArray({ control, name: 'kmExcedente' })


  const salvar = useMutation({
    mutationFn: async (dados: DadosDoFormulario) => {
      const corpo = {
        locadoraId: Number(dados.locadoraId),
        anoVigencia: Number(dados.anoVigencia),
        observacoes: dados.observacoes.trim() || undefined,
        grupos: dados.grupos.map((grupo) => ({
          codigo: grupo.codigo.trim().toUpperCase(),
          veiculosDoGrupo: grupo.veiculosDoGrupo.trim(),
          categoria: grupo.categoria,
          pacotes: grupo.pacotes.map((pacote) => ({
            pacoteKm: paraNumero(pacote.pacoteKm),
            valorMensal: paraNumero(pacote.valorMensal),
          })),
        })),
        kmExcedente: dados.kmExcedente.map((preco) => ({
          categoria: preco.categoria,
          // Vazio significa "vale para todos os pacotes" (RN-06).
          pacoteKm: preco.pacoteKm ? paraNumero(preco.pacoteKm) : undefined,
          valorKm: paraNumero(preco.valorKm),
        })),
      }
      if (tabela) {
        return exigirSucesso(
          await api.PUT('/api/v1/tabelas-preco/{id}', {
            params: { path: { id: tabela.id } },
            body: corpo,
          }),
        )
      }
      return exigirSucesso(await api.POST('/api/v1/tabelas-preco', { body: corpo }))
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['tabelas-preco'] })
      aoFechar()
    },
  })

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await salvar.mutateAsync(dados)
    } catch (erro) {
      if (erro instanceof ErroDaApi && erro.codigo === 'RN-014-VIGENCIA_DUPLICADA') {
        setError('anoVigencia', { message: 'Esta locadora já tem tabela para este ano' })
        return
      }
      definirErroDeEnvio(erro)
    }
  })

  return (
    <Dialog
      open
      onOpenChange={(proximo) => {
        if (!proximo) {
          aoFechar()
        }
      }}
    >
      <DialogConteudo largura="grande">
        <DialogCabecalho>
          <DialogTitulo>{editando ? 'Editar vigência' : 'Nova vigência'}</DialogTitulo>
          <DialogDescricao>
            Reproduza a grade enviada pela locadora. A grade é substituída por inteiro ao salvar.
          </DialogDescricao>
        </DialogCabecalho>

        <form onSubmit={(evento) => void enviar(evento)} noValidate className="contents">
          <DialogCorpo className="space-y-5">
            {erroDeEnvio ? (
              <p
                role="alert"
                className="rounded-[var(--radius-base)] bg-critico-suave/50 px-3 py-2 text-sm text-critico"
              >
                {mensagemDeErro(erroDeEnvio)}
              </p>
            ) : null}

            <div className="grid gap-4 sm:grid-cols-2">
              <CampoDeFormulario
                id="tab-locadora"
                rotulo="Locadora"
                obrigatorio
                erro={errors.locadoraId?.message}
              >
                <Select
                  id="tab-locadora"
                  disabled={editando}
                  invalido={Boolean(errors.locadoraId)}
                  {...register('locadoraId')}
                >
                  <option value="">Selecione</option>
                  {(locadoras.data?.conteudo ?? []).map((locadora) => (
                    <option key={locadora.id} value={locadora.id}>
                      {locadora.nome}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>

              <CampoDeFormulario
                id="tab-ano"
                rotulo="Ano de vigência"
                obrigatorio
                erro={errors.anoVigencia?.message}
              >
                <Input
                  id="tab-ano"
                  type="number"
                  min={2000}
                  max={2100}
                  invalido={Boolean(errors.anoVigencia)}
                  {...register('anoVigencia')}
                />
              </CampoDeFormulario>
            </div>

            <fieldset className="space-y-3 rounded-[var(--radius-base)] border border-borda p-4">
              <legend className="px-1 text-sm font-semibold text-texto">Grupos tarifários</legend>
              {errors.grupos?.message ? (
                <p role="alert" className="text-xs font-medium text-critico">
                  {errors.grupos.message}
                </p>
              ) : null}

              {grupos.fields.map((campo, indice) => (
                <LinhaDeGrupo
                  key={campo.id}
                  indice={indice}
                  control={control}
                  register={register}
                  erros={errors}
                  removivel={grupos.fields.length > 1}
                  aoRemover={() => {
                    grupos.remove(indice)
                  }}
                />
              ))}

              <Button
                type="button"
                variante="secundaria"
                tamanho="pequeno"
                onClick={() => {
                  grupos.append(GRUPO_VAZIO)
                }}
              >
                <Plus aria-hidden="true" />
                Adicionar grupo
              </Button>
            </fieldset>

            <fieldset className="space-y-3 rounded-[var(--radius-base)] border border-borda p-4">
              <legend className="px-1 text-sm font-semibold text-texto">KM excedente</legend>
              <p className="text-xs text-texto-suave">
                Deixe o pacote em branco quando o valor valer para todos — é como a Unidas
                cobra. A Localiza diferencia por pacote; nesse caso, informe um valor para cada.
              </p>

              {excedentes.fields.map((campo, indice) => (
                <div key={campo.id} className="grid gap-3 sm:grid-cols-[1fr_1fr_1fr_auto]">
                  <CampoDeFormulario id={`exc-cat-${String(indice)}`} rotulo="Categoria">
                    <Select id={`exc-cat-${String(indice)}`} {...register(`kmExcedente.${indice}.categoria`)}>
                      {Object.entries(CATEGORIAS_DE_VEICULO).map(([valor, rotulo]) => (
                        <option key={valor} value={valor}>
                          {rotulo}
                        </option>
                      ))}
                    </Select>
                  </CampoDeFormulario>
                  <CampoDeFormulario id={`exc-pac-${String(indice)}`} rotulo="Pacote (km)" dica="Vazio = todos">
                    <Input
                      id={`exc-pac-${String(indice)}`}
                      inputMode="numeric"
                      placeholder="Todos"
                      {...register(`kmExcedente.${indice}.pacoteKm`)}
                    />
                  </CampoDeFormulario>
                  <CampoDeFormulario
                    id={`exc-val-${String(indice)}`}
                    rotulo="Valor por KM"
                    erro={errors.kmExcedente?.[indice]?.valorKm?.message}
                  >
                    <Input
                      id={`exc-val-${String(indice)}`}
                      inputMode="decimal"
                      placeholder="0,60"
                      {...register(`kmExcedente.${indice}.valorKm`)}
                    />
                  </CampoDeFormulario>
                  <div className="flex items-end pb-1">
                    <Button
                      type="button"
                      variante="sutil"
                      tamanho="icone"
                      aria-label={`Remover preço de KM excedente ${String(indice + 1)}`}
                      onClick={() => {
                        excedentes.remove(indice)
                      }}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  </div>
                </div>
              ))}

              <Button
                type="button"
                variante="secundaria"
                tamanho="pequeno"
                onClick={() => {
                  excedentes.append({ categoria: 'PASSEIO', pacoteKm: '', valorKm: '' })
                }}
              >
                <Plus aria-hidden="true" />
                Adicionar valor de KM excedente
              </Button>
            </fieldset>

            <CampoDeFormulario id="tab-observacoes" rotulo="Observações" erro={errors.observacoes?.message}>
              <Textarea id="tab-observacoes" {...register('observacoes')} />
            </CampoDeFormulario>
          </DialogCorpo>

          <DialogRodape>
            <Button type="button" variante="secundaria" onClick={aoFechar} disabled={isSubmitting}>
              Cancelar
            </Button>
            <Button type="submit" carregando={isSubmitting}>
              {isSubmitting ? 'Salvando…' : 'Salvar'}
            </Button>
          </DialogRodape>
        </form>
      </DialogConteudo>
    </Dialog>
  )
}

type ControleDoFormulario = ReturnType<typeof useForm<DadosDoFormulario>>

/** Um grupo tarifário com sua lista aninhada de pacotes de quilometragem. */
function LinhaDeGrupo({
  indice,
  control,
  register,
  erros,
  removivel,
  aoRemover,
}: {
  indice: number
  control: ControleDoFormulario['control']
  register: ControleDoFormulario['register']
  erros: ControleDoFormulario['formState']['errors']
  removivel: boolean
  aoRemover: () => void
}) {
  const pacotes = useFieldArray({ control, name: `grupos.${indice}.pacotes` })
  const errosDoGrupo = erros.grupos?.[indice]

  return (
    <div className="space-y-3 rounded-[var(--radius-base)] border border-borda bg-fundo-alternativo/40 p-3">
      <div className="grid gap-3 sm:grid-cols-[120px_1fr_180px_auto]">
        <CampoDeFormulario
          id={`grp-cod-${String(indice)}`}
          rotulo="Código"
          erro={errosDoGrupo?.codigo?.message}
        >
          <Input
            id={`grp-cod-${String(indice)}`}
            className="font-mono uppercase"
            placeholder="AM"
            invalido={Boolean(errosDoGrupo?.codigo)}
            {...register(`grupos.${indice}.codigo`)}
          />
        </CampoDeFormulario>
        <CampoDeFormulario
          id={`grp-vei-${String(indice)}`}
          rotulo="Veículos do grupo"
          erro={errosDoGrupo?.veiculosDoGrupo?.message}
        >
          <Input
            id={`grp-vei-${String(indice)}`}
            placeholder="KWID/Mobi"
            invalido={Boolean(errosDoGrupo?.veiculosDoGrupo)}
            {...register(`grupos.${indice}.veiculosDoGrupo`)}
          />
        </CampoDeFormulario>
        <CampoDeFormulario id={`grp-cat-${String(indice)}`} rotulo="Categoria">
          <Select id={`grp-cat-${String(indice)}`} {...register(`grupos.${indice}.categoria`)}>
            {Object.entries(CATEGORIAS_DE_VEICULO).map(([valor, rotulo]) => (
              <option key={valor} value={valor}>
                {rotulo}
              </option>
            ))}
          </Select>
        </CampoDeFormulario>
        <div className="flex items-end pb-1">
          <Button
            type="button"
            variante="sutil"
            tamanho="icone"
            disabled={!removivel}
            aria-label={`Remover grupo ${String(indice + 1)}`}
            onClick={aoRemover}
          >
            <Trash2 aria-hidden="true" />
          </Button>
        </div>
      </div>

      <div className="space-y-2 border-t border-borda pt-3">
        <p className="text-xs font-medium uppercase tracking-wide text-texto-tenue">
          Pacotes de quilometragem
        </p>
        {pacotes.fields.map((campo, indiceDoPacote) => (
          <div key={campo.id} className="grid gap-3 sm:grid-cols-[160px_180px_auto]">
            <CampoDeFormulario
              id={`pac-km-${String(indice)}-${String(indiceDoPacote)}`}
              rotulo="Pacote (km)"
              erro={errosDoGrupo?.pacotes?.[indiceDoPacote]?.pacoteKm?.message}
            >
              <Input
                id={`pac-km-${String(indice)}-${String(indiceDoPacote)}`}
                inputMode="numeric"
                placeholder="3000"
                {...register(`grupos.${indice}.pacotes.${indiceDoPacote}.pacoteKm`)}
              />
            </CampoDeFormulario>
            <CampoDeFormulario
              id={`pac-val-${String(indice)}-${String(indiceDoPacote)}`}
              rotulo="Valor mensal"
              erro={errosDoGrupo?.pacotes?.[indiceDoPacote]?.valorMensal?.message}
            >
              <Input
                id={`pac-val-${String(indice)}-${String(indiceDoPacote)}`}
                inputMode="decimal"
                placeholder="2606,08"
                {...register(`grupos.${indice}.pacotes.${indiceDoPacote}.valorMensal`)}
              />
            </CampoDeFormulario>
            <div className="flex items-end pb-1">
              <Button
                type="button"
                variante="sutil"
                tamanho="icone"
                disabled={pacotes.fields.length <= 1}
                aria-label={`Remover pacote ${String(indiceDoPacote + 1)} do grupo ${String(indice + 1)}`}
                onClick={() => {
                  pacotes.remove(indiceDoPacote)
                }}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            </div>
          </div>
        ))}
        <Button
          type="button"
          variante="sutil"
          tamanho="pequeno"
          onClick={() => {
            pacotes.append({ pacoteKm: '', valorMensal: '' })
          }}
        >
          <Plus aria-hidden="true" />
          Adicionar pacote
        </Button>
      </div>
    </div>
  )
}
