import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { CampoDeFormulario } from '@/components/ui/campo-de-formulario'
import {
  Dialog, DialogCabecalho, DialogConteudo, DialogCorpo, DialogDescricao, DialogRodape, DialogTitulo,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { STATUS_DE_CONDUTOR, type Condutor } from '@/features/cadastros/tipos'
import { api, exigirSucesso } from '@/lib/api/client'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'

/** Valida os dígitos verificadores do CPF, espelhando a regra do domínio no servidor. */
function cpfEValido(bruto: string): boolean {
  const digitos = bruto.replace(/\D/g, '')
  if (digitos.length !== 11 || new Set(digitos).size === 1) return false
  const verificador = (ate: number): number => {
    let soma = 0
    let peso = ate + 1
    for (let i = 0; i < ate; i++) soma += Number(digitos[i]) * peso--
    const resto = soma % 11
    return resto < 2 ? 0 : 11 - resto
  }
  return verificador(9) === Number(digitos[9]) && verificador(10) === Number(digitos[10])
}

const esquema = z.object({
  nome: z.string().min(1, 'Informe o nome').max(180, 'No máximo 180 caracteres'),
  cpf: z.string().min(1, 'Informe o CPF').refine(cpfEValido, 'CPF inválido'),
  cargo: z.string().max(120),
  telefone: z.string().max(60),
  email: z.union([z.literal(''), z.string().email('Informe um e-mail válido')]),
  cnhNumero: z.string().max(20),
  cnhCategoria: z
    .string()
    .refine((valor) => valor === '' || /^[A-Za-z]{1,4}$/.test(valor), 'Use letras de A a E, ex.: AB'),
  cnhValidade: z.string(),
  obraAtualId: z.string(),
  status: z.enum(['ATIVO', 'INATIVO']),
  observacoes: z.string().max(2000),
})

type DadosDoFormulario = z.infer<typeof esquema>

/** Valores iniciais derivados do registro; o formulário monta já preenchido. */
function valoresDe(condutor: Condutor | null): DadosDoFormulario {
  if (!condutor) {
    return {
      nome: '', cpf: '', cargo: '', telefone: '', email: '', cnhNumero: '',
      cnhCategoria: '', cnhValidade: '', obraAtualId: '', status: 'ATIVO', observacoes: '',
    }
  }
  return {
    nome: condutor.nome,
    cpf: condutor.cpfFormatado,
    cargo: condutor.cargo ?? '',
    telefone: condutor.telefone ?? '',
    email: condutor.email ?? '',
    cnhNumero: condutor.cnhNumero ?? '',
    cnhCategoria: condutor.cnhCategoria ?? '',
    cnhValidade: condutor.cnhValidade ?? '',
    obraAtualId: condutor.obraAtual ? String(condutor.obraAtual.id) : '',
    status: condutor.status,
    observacoes: condutor.observacoes ?? '',
  }
}

export function FormularioDeCondutor({
  condutor,
  aoFechar,
}: {
  condutor: Condutor | null
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)
  const editando = condutor !== null

  // As obras alimentam o campo de alocação.
  const obras = useQuery({
    queryKey: ['obras', 'selecao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/obras', {
          params: { query: { status: 'ATIVA', page: 0, size: 200, sort: ['codigo,asc'] } },
        }),
      ),
  })

  const {
    register, handleSubmit, setError,
    formState: { errors, isSubmitting },
  } = useForm<DadosDoFormulario>({ resolver: zodResolver(esquema), defaultValues: valoresDe(condutor) })


  const salvar = useMutation({
    mutationFn: async (dados: DadosDoFormulario) => {
      const corpo = {
        nome: dados.nome.trim(),
        cpf: dados.cpf.replace(/\D/g, ''),
        cargo: dados.cargo.trim() || undefined,
        telefone: dados.telefone.trim() || undefined,
        email: dados.email.trim() || undefined,
        cnhNumero: dados.cnhNumero.trim() || undefined,
        cnhCategoria: dados.cnhCategoria.trim() || undefined,
        cnhValidade: dados.cnhValidade || undefined,
        obraAtualId: dados.obraAtualId ? Number(dados.obraAtualId) : undefined,
        status: dados.status,
        observacoes: dados.observacoes.trim() || undefined,
      }
      if (condutor) {
        return exigirSucesso(
          await api.PUT('/api/v1/condutores/{id}', { params: { path: { id: condutor.id } }, body: corpo }),
        )
      }
      return exigirSucesso(await api.POST('/api/v1/condutores', { body: corpo }))
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['condutores'] })
      aoFechar()
    },
  })

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await salvar.mutateAsync(dados)
    } catch (erro) {
      if (erro instanceof ErroDaApi && erro.codigo === 'CAD-003-CPF_DUPLICADO') {
        setError('cpf', { message: 'Já existe um condutor com este CPF' })
        return
      }
      if (erro instanceof ErroDaApi && erro.codigo === 'CAD-004-CPF_INVALIDO') {
        setError('cpf', { message: 'CPF inválido' })
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
          <DialogTitulo>{editando ? 'Editar condutor' : 'Novo condutor'}</DialogTitulo>
          <DialogDescricao>
            A validade da CNH alimenta os alertas de 60 e 30 dias e bloqueia o vínculo a
            contrato quando vencida.
          </DialogDescricao>
        </DialogCabecalho>

        <form onSubmit={(evento) => void enviar(evento)} noValidate className="contents">
          <DialogCorpo className="space-y-4">
            {erroDeEnvio ? (
              <p role="alert" className="rounded-[var(--radius-base)] bg-critico-suave/50 px-3 py-2 text-sm text-critico">
                {mensagemDeErro(erroDeEnvio)}
              </p>
            ) : null}

            <div className="grid gap-4 sm:grid-cols-2">
              <CampoDeFormulario id="cond-nome" rotulo="Nome" obrigatorio erro={errors.nome?.message}>
                <Input id="cond-nome" invalido={Boolean(errors.nome)} {...register('nome')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-cpf" rotulo="CPF" obrigatorio erro={errors.cpf?.message} dica="Com ou sem pontuação">
                <Input id="cond-cpf" inputMode="numeric" invalido={Boolean(errors.cpf)} {...register('cpf')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-cargo" rotulo="Cargo / função" erro={errors.cargo?.message}>
                <Input id="cond-cargo" {...register('cargo')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-obra" rotulo="Obra atual" erro={errors.obraAtualId?.message}>
                <Select id="cond-obra" {...register('obraAtualId')}>
                  <option value="">Não alocado</option>
                  {(obras.data?.conteudo ?? []).map((obra) => (
                    <option key={obra.id} value={obra.id}>{obra.codigo} — {obra.nome}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-telefone" rotulo="Telefone" erro={errors.telefone?.message}>
                <Input id="cond-telefone" {...register('telefone')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-email" rotulo="E-mail" erro={errors.email?.message}>
                <Input id="cond-email" type="email" invalido={Boolean(errors.email)} {...register('email')} />
              </CampoDeFormulario>
            </div>

            <fieldset className="grid gap-4 rounded-[var(--radius-base)] border border-borda p-4 sm:grid-cols-3">
              <legend className="px-1 text-sm font-semibold text-texto">Habilitação</legend>
              <CampoDeFormulario id="cond-cnh-numero" rotulo="Número da CNH" erro={errors.cnhNumero?.message}>
                <Input id="cond-cnh-numero" inputMode="numeric" {...register('cnhNumero')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-cnh-categoria" rotulo="Categoria" erro={errors.cnhCategoria?.message} dica="Ex.: AB">
                <Input
                  id="cond-cnh-categoria"
                  maxLength={4}
                  invalido={Boolean(errors.cnhCategoria)}
                  {...register('cnhCategoria')}
                />
              </CampoDeFormulario>
              <CampoDeFormulario id="cond-cnh-validade" rotulo="Validade" erro={errors.cnhValidade?.message}>
                <Input id="cond-cnh-validade" type="date" {...register('cnhValidade')} />
              </CampoDeFormulario>
            </fieldset>

            <div className="grid gap-4 sm:grid-cols-2">
              <CampoDeFormulario id="cond-status" rotulo="Situação" obrigatorio erro={errors.status?.message}>
                <Select id="cond-status" {...register('status')}>
                  {Object.entries(STATUS_DE_CONDUTOR).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>{rotulo}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
            </div>

            <CampoDeFormulario id="cond-observacoes" rotulo="Observações" erro={errors.observacoes?.message}>
              <Textarea id="cond-observacoes" {...register('observacoes')} />
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
