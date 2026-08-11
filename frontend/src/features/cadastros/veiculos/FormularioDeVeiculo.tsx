import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { CampoDeFormulario } from '@/components/ui/campo-de-formulario'
import {
  Dialog, DialogCabecalho, DialogConteudo, DialogCorpo, DialogDescricao, DialogRodape, DialogTitulo,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import {
  CATEGORIAS_DE_VEICULO, COMBUSTIVEIS, STATUS_DE_VEICULO,
  type Veiculo,
} from '@/features/cadastros/tipos'
import { api, exigirSucesso } from '@/lib/api/client'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'
import { normalizarPlaca } from '@/lib/formatters'

/** Aceita os formatos Mercosul (AAA9A99) e antigo (AAA9999), como a RN-02. */
const PADRAO_DE_PLACA = /^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$/

const esquema = z.object({
  placa: z
    .string()
    .min(1, 'Informe a placa')
    .refine((valor) => PADRAO_DE_PLACA.test(normalizarPlaca(valor)), 'Use o formato ABC1D23 ou ABC1234'),
  modelo: z.string().min(1, 'Informe o modelo').max(120),
  fabricante: z.string().max(120),
  anoFabricacao: z.string(),
  categoria: z.enum(['PASSEIO', 'SUV', 'QUATRO_X_QUATRO', 'UTILITARIO']),
  combustivel: z.enum(['FLEX', 'GASOLINA', 'ETANOL', 'DIESEL', 'HIBRIDO', 'ELETRICO']),
  locadoraId: z.string().min(1, 'Selecione a locadora'),
  grupoTarifario: z.string().max(20),
  codigoInterno: z.string().max(40),
  possuiRastreador: z.boolean(),
  fornecedorRastreador: z.string().max(160),
  possuiAdesivo: z.boolean(),
  status: z.enum(['DISPONIVEL', 'EM_USO', 'EM_MANUTENCAO', 'DEVOLVIDO']),
  observacoes: z.string().max(2000),
})

type DadosDoFormulario = z.infer<typeof esquema>

/** Valores iniciais derivados do registro; o formulário monta já preenchido. */
function valoresDe(veiculo: Veiculo | null): DadosDoFormulario {
  if (!veiculo) {
    return {
      placa: '', modelo: '', fabricante: '', anoFabricacao: '', categoria: 'PASSEIO',
      combustivel: 'FLEX', locadoraId: '', grupoTarifario: '', codigoInterno: '',
      possuiRastreador: false, fornecedorRastreador: '', possuiAdesivo: false,
      status: 'DISPONIVEL', observacoes: '',
    }
  }
  return {
    placa: veiculo.placaFormatada,
    modelo: veiculo.modelo,
    fabricante: veiculo.fabricante ?? '',
    anoFabricacao: veiculo.anoFabricacao ? String(veiculo.anoFabricacao) : '',
    categoria: veiculo.categoria,
    combustivel: veiculo.combustivel,
    locadoraId: String(veiculo.locadora.id),
    grupoTarifario: veiculo.grupoTarifario ?? '',
    codigoInterno: veiculo.codigoInterno ?? '',
    possuiRastreador: veiculo.possuiRastreador,
    fornecedorRastreador: veiculo.fornecedorRastreador ?? '',
    possuiAdesivo: veiculo.possuiAdesivo,
    status: veiculo.status,
    observacoes: veiculo.observacoes ?? '',
  }
}

export function FormularioDeVeiculo({
  veiculo,
  aoFechar,
}: {
  veiculo: Veiculo | null
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)
  const editando = veiculo !== null

  const locadoras = useQuery({
    queryKey: ['locadoras', 'selecao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/locadoras', {
          params: { query: { ativa: true, page: 0, size: 200, sort: ['nome,asc'] } },
        }),
      ),
  })

  const {
    register, control, handleSubmit, setError,
    formState: { errors, isSubmitting },
  } = useForm<DadosDoFormulario>({ resolver: zodResolver(esquema), defaultValues: valoresDe(veiculo) })


  const salvar = useMutation({
    mutationFn: async (dados: DadosDoFormulario) => {
      const corpo = {
        placa: normalizarPlaca(dados.placa),
        modelo: dados.modelo.trim(),
        fabricante: dados.fabricante.trim() || undefined,
        anoFabricacao: dados.anoFabricacao ? Number(dados.anoFabricacao) : undefined,
        categoria: dados.categoria,
        combustivel: dados.combustivel,
        locadoraId: Number(dados.locadoraId),
        grupoTarifario: dados.grupoTarifario.trim() || undefined,
        codigoInterno: dados.codigoInterno.trim() || undefined,
        possuiRastreador: dados.possuiRastreador,
        fornecedorRastreador: dados.possuiRastreador
          ? dados.fornecedorRastreador.trim() || undefined
          : undefined,
        possuiAdesivo: dados.possuiAdesivo,
        status: dados.status,
        observacoes: dados.observacoes.trim() || undefined,
      }
      if (veiculo) {
        return exigirSucesso(
          await api.PUT('/api/v1/veiculos/{id}', { params: { path: { id: veiculo.id } }, body: corpo }),
        )
      }
      return exigirSucesso(await api.POST('/api/v1/veiculos', { body: corpo }))
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['veiculos'] })
      aoFechar()
    },
  })

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await salvar.mutateAsync(dados)
    } catch (erro) {
      if (erro instanceof ErroDaApi && erro.codigo === 'RN-002-PLACA_DUPLICADA') {
        setError('placa', { message: 'Esta placa já está cadastrada em outro veículo' })
        return
      }
      if (erro instanceof ErroDaApi && erro.codigo === 'RN-002-PLACA_INVALIDA') {
        setError('placa', { message: 'Placa em formato inválido' })
        return
      }
      definirErroDeEnvio(erro)
    }
  })

  // `useWatch` no lugar de `watch`: o segundo devolve uma função que o React
  // Compiler não consegue memoizar, desativando a otimização do componente inteiro.
  const temRastreador = useWatch({ control, name: 'possuiRastreador' })
  const combustivel = useWatch({ control, name: 'combustivel' })

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
          <DialogTitulo>{editando ? 'Editar veículo' : 'Novo veículo'}</DialogTitulo>
          <DialogDescricao>
            A placa é normalizada automaticamente: pode ser digitada com ou sem hífen, em
            qualquer caixa.
          </DialogDescricao>
        </DialogCabecalho>

        <form onSubmit={(evento) => void enviar(evento)} noValidate className="contents">
          <DialogCorpo className="space-y-4">
            {erroDeEnvio ? (
              <p role="alert" className="rounded-[var(--radius-base)] bg-critico-suave/50 px-3 py-2 text-sm text-critico">
                {mensagemDeErro(erroDeEnvio)}
              </p>
            ) : null}

            <div className="grid gap-4 sm:grid-cols-3">
              <CampoDeFormulario id="vei-placa" rotulo="Placa" obrigatorio erro={errors.placa?.message} dica="Ex.: ABC1D23">
                <Input
                  id="vei-placa"
                  className="font-mono uppercase"
                  invalido={Boolean(errors.placa)}
                  {...register('placa')}
                />
              </CampoDeFormulario>
              <CampoDeFormulario id="vei-modelo" rotulo="Modelo" obrigatorio erro={errors.modelo?.message}>
                <Input id="vei-modelo" invalido={Boolean(errors.modelo)} {...register('modelo')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="vei-fabricante" rotulo="Fabricante" erro={errors.fabricante?.message}>
                <Input id="vei-fabricante" {...register('fabricante')} />
              </CampoDeFormulario>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <CampoDeFormulario id="vei-ano" rotulo="Ano de fabricação" erro={errors.anoFabricacao?.message}>
                <Input id="vei-ano" type="number" inputMode="numeric" min={1980} max={2100} {...register('anoFabricacao')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="vei-categoria" rotulo="Categoria" obrigatorio erro={errors.categoria?.message}>
                <Select id="vei-categoria" {...register('categoria')}>
                  {Object.entries(CATEGORIAS_DE_VEICULO).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>{rotulo}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
              <CampoDeFormulario
                id="vei-combustivel"
                rotulo="Combustível"
                obrigatorio
                erro={errors.combustivel?.message}
                dica={combustivel === 'DIESEL' ? 'Exigirá teste de fumaça preta na retirada' : undefined}
              >
                <Select id="vei-combustivel" {...register('combustivel')}>
                  {Object.entries(COMBUSTIVEIS).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>{rotulo}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <CampoDeFormulario id="vei-locadora" rotulo="Locadora" obrigatorio erro={errors.locadoraId?.message}>
                <Select id="vei-locadora" invalido={Boolean(errors.locadoraId)} {...register('locadoraId')}>
                  <option value="">Selecione</option>
                  {(locadoras.data?.conteudo ?? []).map((locadora) => (
                    <option key={locadora.id} value={locadora.id}>{locadora.nome}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
              <CampoDeFormulario id="vei-grupo" rotulo="Grupo tarifário" erro={errors.grupoTarifario?.message} dica="Ex.: AM">
                <Input id="vei-grupo" className="uppercase" {...register('grupoTarifario')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="vei-codigo" rotulo="Código interno" erro={errors.codigoInterno?.message}>
                <Input id="vei-codigo" {...register('codigoInterno')} />
              </CampoDeFormulario>
            </div>

            <fieldset className="space-y-3 rounded-[var(--radius-base)] border border-borda p-4">
              <legend className="px-1 text-sm font-semibold text-texto">Equipamentos</legend>
              <Switch
                id="vei-rastreador"
                rotulo="Possui rastreador"
                descricao="Veículos rastreados têm mensalidade de telemetria."
                {...register('possuiRastreador')}
              />
              {temRastreador ? (
                <CampoDeFormulario
                  id="vei-fornecedor-rastreador"
                  rotulo="Fornecedor do rastreador"
                  erro={errors.fornecedorRastreador?.message}
                >
                  <Input id="vei-fornecedor-rastreador" {...register('fornecedorRastreador')} />
                </CampoDeFormulario>
              ) : null}
              <Switch
                id="vei-adesivo"
                rotulo="Possui adesivo de identificação"
                descricao="Adesivo ou imã com a identificação visual da empresa."
                {...register('possuiAdesivo')}
              />
            </fieldset>

            <div className="grid gap-4 sm:grid-cols-2">
              <CampoDeFormulario id="vei-status" rotulo="Situação" obrigatorio erro={errors.status?.message}>
                <Select id="vei-status" {...register('status')}>
                  {Object.entries(STATUS_DE_VEICULO).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>{rotulo}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
            </div>

            <CampoDeFormulario id="vei-observacoes" rotulo="Observações" erro={errors.observacoes?.message}>
              <Textarea id="vei-observacoes" {...register('observacoes')} />
            </CampoDeFormulario>
          </DialogCorpo>

          <DialogRodape>
            <Button type="button" variante="secundaria" onClick={aoFechar} disabled={isSubmitting}>Cancelar</Button>
            <Button type="submit" carregando={isSubmitting}>{isSubmitting ? 'Salvando…' : 'Salvar'}</Button>
          </DialogRodape>
        </form>
      </DialogConteudo>
    </Dialog>
  )
}
