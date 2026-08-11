import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
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
import {
  STATUS_DE_OBRA,
  UNIDADES_FEDERATIVAS,
  type Obra,
} from '@/features/cadastros/tipos'
import { descritoPor } from '@/lib/acessibilidade'
import { api, exigirSucesso } from '@/lib/api/client'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'

/**
 * Esquema de validação, espelhando as regras do backend.
 *
 * A validação aqui existe para dar retorno imediato; a que vale é sempre a do domínio,
 * no servidor. Quando as duas divergirem, é o servidor que decide.
 */
const esquema = z
  .object({
    codigo: z.string().min(1, 'Informe o código da obra').max(20, 'No máximo 20 caracteres'),
    nome: z.string().min(1, 'Informe o nome da obra').max(180, 'No máximo 180 caracteres'),
    cliente: z.string().max(180, 'No máximo 180 caracteres'),
    cidade: z.string().min(1, 'Informe a cidade').max(120, 'No máximo 120 caracteres'),
    uf: z.string().length(2, 'Selecione a UF'),
    status: z.enum(['ATIVA', 'ENCERRADA']),
    dataInicio: z.string(),
    dataFim: z.string(),
    observacoes: z.string().max(2000, 'No máximo 2000 caracteres'),
  })
  .refine((dados) => !dados.dataInicio || !dados.dataFim || dados.dataFim >= dados.dataInicio, {
    message: 'O encerramento não pode ser anterior ao início',
    path: ['dataFim'],
  })

type DadosDoFormulario = z.infer<typeof esquema>

/**
 * Valores iniciais do formulário.
 *
 * Derivados diretamente do registro em vez de aplicados por efeito: o componente só
 * existe enquanto o diálogo está aberto, então montar já com os dados certos dispensa
 * qualquer sincronização posterior.
 */
function valoresDe(obra: Obra | null): DadosDoFormulario {
  if (!obra) {
    return {
      codigo: '', nome: '', cliente: '', cidade: '', uf: '',
      status: 'ATIVA', dataInicio: '', dataFim: '', observacoes: '',
    }
  }
  return {
    codigo: obra.codigo,
    nome: obra.nome,
    cliente: obra.cliente ?? '',
    cidade: obra.cidade,
    uf: obra.uf,
    status: obra.status,
    dataInicio: obra.dataInicio ?? '',
    dataFim: obra.dataFim ?? '',
    observacoes: obra.observacoes ?? '',
  }
}

export function FormularioDeObra({
  obra,
  aoFechar,
}: {
  obra: Obra | null
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)
  const editando = obra !== null

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DadosDoFormulario>({
    resolver: zodResolver(esquema),
    defaultValues: valoresDe(obra),
  })

  // Carrega os valores do registro ao abrir em modo de edição e limpa ao fechar,
  // para que o formulário nunca reapareça com dados do registro anterior.

  const salvar = useMutation({
    mutationFn: async (dados: DadosDoFormulario) => {
      const corpo = {
        codigo: dados.codigo.trim(),
        nome: dados.nome.trim(),
        cliente: dados.cliente.trim() || undefined,
        cidade: dados.cidade.trim(),
        uf: dados.uf,
        status: dados.status,
        dataInicio: dados.dataInicio || undefined,
        dataFim: dados.dataFim || undefined,
        observacoes: dados.observacoes.trim() || undefined,
      }
      if (obra) {
        return exigirSucesso(
          await api.PUT('/api/v1/obras/{id}', { params: { path: { id: obra.id } }, body: corpo }),
        )
      }
      return exigirSucesso(await api.POST('/api/v1/obras', { body: corpo }))
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['obras'] })
      aoFechar()
    },
  })

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await salvar.mutateAsync(dados)
    } catch (erro) {
      // Conflito de código é erro de campo, não da tela: mostrá-lo junto ao input
      // poupa o usuário de procurar qual dado precisa mudar.
      if (erro instanceof ErroDaApi && erro.codigo === 'CAD-001-CODIGO_OBRA_DUPLICADO') {
        setError('codigo', { message: 'Já existe uma obra com este código' })
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
          <DialogTitulo>{editando ? 'Editar obra' : 'Nova obra'}</DialogTitulo>
          <DialogDescricao>
            {editando
              ? 'Altere os dados da frente de trabalho. O código precisa continuar único.'
              : 'Cadastre uma frente de trabalho para alocar veículos e credenciar fornecedores.'}
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
              <CampoDeFormulario
                id="obra-codigo"
                rotulo="Código"
                obrigatorio
                erro={errors.codigo?.message}
                dica="Ex.: 24.019"
              >
                <Input
                  id="obra-codigo"
                  invalido={Boolean(errors.codigo)}
                  aria-describedby={descritoPor('obra-codigo', true, Boolean(errors.codigo))}
                  {...register('codigo')}
                />
              </CampoDeFormulario>

              <CampoDeFormulario
                id="obra-nome"
                rotulo="Nome"
                obrigatorio
                erro={errors.nome?.message}
                className="sm:col-span-2"
              >
                <Input id="obra-nome" invalido={Boolean(errors.nome)} {...register('nome')} />
              </CampoDeFormulario>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <CampoDeFormulario id="obra-cliente" rotulo="Cliente" erro={errors.cliente?.message}>
                <Input id="obra-cliente" invalido={Boolean(errors.cliente)} {...register('cliente')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="obra-cidade" rotulo="Cidade" obrigatorio erro={errors.cidade?.message}>
                <Input id="obra-cidade" invalido={Boolean(errors.cidade)} {...register('cidade')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="obra-uf" rotulo="UF" obrigatorio erro={errors.uf?.message}>
                <Select id="obra-uf" invalido={Boolean(errors.uf)} {...register('uf')}>
                  <option value="">Selecione</option>
                  {UNIDADES_FEDERATIVAS.map((uf) => (
                    <option key={uf} value={uf}>
                      {uf}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <CampoDeFormulario id="obra-status" rotulo="Situação" obrigatorio erro={errors.status?.message}>
                <Select id="obra-status" invalido={Boolean(errors.status)} {...register('status')}>
                  {Object.entries(STATUS_DE_OBRA).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>
                      {rotulo}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>

              <CampoDeFormulario id="obra-inicio" rotulo="Início" erro={errors.dataInicio?.message}>
                <Input id="obra-inicio" type="date" {...register('dataInicio')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="obra-fim" rotulo="Encerramento" erro={errors.dataFim?.message}>
                <Input
                  id="obra-fim"
                  type="date"
                  invalido={Boolean(errors.dataFim)}
                  {...register('dataFim')}
                />
              </CampoDeFormulario>
            </div>

            <CampoDeFormulario id="obra-observacoes" rotulo="Observações" erro={errors.observacoes?.message}>
              <Textarea
                id="obra-observacoes"
                invalido={Boolean(errors.observacoes)}
                {...register('observacoes')}
              />
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
