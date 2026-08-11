import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
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
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { TIPOS_DE_LOCADORA, type Locadora } from '@/features/cadastros/tipos'
import { api, exigirSucesso } from '@/lib/api/client'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'

const esquema = z.object({
  nome: z.string().min(1, 'Informe o nome da locadora').max(160, 'No máximo 160 caracteres'),
  tipo: z.enum(['NACIONAL', 'AVULSA']),
  consultor: z.string().max(160, 'No máximo 160 caracteres'),
  telefone: z.string().max(60, 'No máximo 60 caracteres'),
  email: z.union([z.literal(''), z.string().email('Informe um e-mail válido')]),
  portalUrl: z.string().max(400, 'No máximo 400 caracteres'),
  portalLogin: z.string().max(200, 'No máximo 200 caracteres'),
  portalSenha: z.string().max(200, 'No máximo 200 caracteres'),
  reservas: z.string().max(200),
  manutencao: z.string().max(200),
  guinchoSinistro: z.string().max(200),
  assistencia24h: z.string().max(200),
  financeiro: z.string().max(200),
  suporte: z.string().max(200),
  telemetria: z.string().max(200),
  observacoes: z.string().max(2000, 'No máximo 2000 caracteres'),
  ativa: z.boolean(),
})

type DadosDoFormulario = z.infer<typeof esquema>

/**
 * Valores iniciais derivados do registro.
 *
 * Os campos de credencial nascem vazios mesmo em edição: em branco significa
 * "preservar a atual" (RN-20), e trazer o valor cifrado para a tela seria inútil e
 * perigoso.
 */
function valoresDe(locadora: Locadora | null): DadosDoFormulario {
  if (!locadora) {
    return {
      nome: '', tipo: 'NACIONAL', consultor: '', telefone: '', email: '', portalUrl: '',
      portalLogin: '', portalSenha: '', reservas: '', manutencao: '', guinchoSinistro: '',
      assistencia24h: '', financeiro: '', suporte: '', telemetria: '', observacoes: '', ativa: true,
    }
  }
  return {
    nome: locadora.nome,
    tipo: locadora.tipo,
    consultor: locadora.consultor ?? '',
    telefone: locadora.telefone ?? '',
    email: locadora.email ?? '',
    portalUrl: locadora.portalUrl ?? '',
    portalLogin: '',
    portalSenha: '',
    reservas: locadora.canais?.reservas ?? '',
    manutencao: locadora.canais?.manutencao ?? '',
    guinchoSinistro: locadora.canais?.guinchoSinistro ?? '',
    assistencia24h: locadora.canais?.assistencia24h ?? '',
    financeiro: locadora.canais?.financeiro ?? '',
    suporte: locadora.canais?.suporte ?? '',
    telemetria: locadora.canais?.telemetria ?? '',
    observacoes: locadora.observacoes ?? '',
    ativa: locadora.ativa,
  }
}

/**
 * Formulário de locadora.
 *
 * Sobre as credenciais (RN-20): em modo de edição os campos ficam vazios e são enviados
 * apenas se preenchidos. Preencher com espaço em branco não remove nada — a remoção
 * exige a ação explícita do botão dedicado, para que ninguém apague uma senha sem querer.
 */
export function FormularioDeLocadora({
  locadora,
  aoFechar,
}: {
  locadora: Locadora | null
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)
  const [removerCredenciais, definirRemoverCredenciais] = useState(false)
  const editando = locadora !== null

  const {
    register, control, handleSubmit, setError,
    formState: { errors, isSubmitting },
  } = useForm<DadosDoFormulario>({ resolver: zodResolver(esquema), defaultValues: valoresDe(locadora) })


  const salvar = useMutation({
    mutationFn: async (dados: DadosDoFormulario) => {
      // Omitir a credencial preserva a atual; string vazia a remove. Por isso o campo
      // só é incluído quando o usuário digitou algo ou pediu a remoção explicitamente.
      const credenciais = removerCredenciais
        ? { portalLogin: '', portalSenha: '' }
        : {
            ...(dados.portalLogin ? { portalLogin: dados.portalLogin } : {}),
            ...(dados.portalSenha ? { portalSenha: dados.portalSenha } : {}),
          }

      const corpo = {
        nome: dados.nome.trim(),
        tipo: dados.tipo,
        consultor: dados.consultor.trim() || undefined,
        telefone: dados.telefone.trim() || undefined,
        email: dados.email.trim() || undefined,
        portalUrl: dados.portalUrl.trim() || undefined,
        ...credenciais,
        canais: {
          reservas: dados.reservas.trim() || undefined,
          manutencao: dados.manutencao.trim() || undefined,
          guinchoSinistro: dados.guinchoSinistro.trim() || undefined,
          assistencia24h: dados.assistencia24h.trim() || undefined,
          financeiro: dados.financeiro.trim() || undefined,
          suporte: dados.suporte.trim() || undefined,
          telemetria: dados.telemetria.trim() || undefined,
        },
        observacoes: dados.observacoes.trim() || undefined,
        ativa: dados.ativa,
      }
      if (locadora) {
        return exigirSucesso(
          await api.PUT('/api/v1/locadoras/{id}', { params: { path: { id: locadora.id } }, body: corpo }),
        )
      }
      return exigirSucesso(await api.POST('/api/v1/locadoras', { body: corpo }))
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['locadoras'] })
      aoFechar()
    },
  })

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await salvar.mutateAsync(dados)
    } catch (erro) {
      if (erro instanceof ErroDaApi && erro.codigo === 'CAD-002-NOME_LOCADORA_DUPLICADO') {
        setError('nome', { message: 'Já existe uma locadora com este nome' })
        return
      }
      definirErroDeEnvio(erro)
    }
  })

  // `useWatch` no lugar de `watch`: o segundo devolve uma função que o React
  // Compiler não consegue memoizar, desativando a otimização do componente inteiro.
  const tipoSelecionado = useWatch({ control, name: 'tipo' })
  const ehNacional = tipoSelecionado === 'NACIONAL'

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
          <DialogTitulo>{editando ? 'Editar locadora' : 'Nova locadora'}</DialogTitulo>
          <DialogDescricao>
            {editando
              ? 'Altere os dados da locadora. Deixe as credenciais em branco para manter as atuais.'
              : 'Cadastre a locadora, seus canais de atendimento e, se houver, o acesso ao portal.'}
          </DialogDescricao>
        </DialogCabecalho>

        <form onSubmit={(evento) => void enviar(evento)} noValidate className="contents">
          <DialogCorpo className="space-y-5">
            {erroDeEnvio ? (
              <p role="alert" className="rounded-[var(--radius-base)] bg-critico-suave/50 px-3 py-2 text-sm text-critico">
                {mensagemDeErro(erroDeEnvio)}
              </p>
            ) : null}

            <div className="grid gap-4 sm:grid-cols-2">
              <CampoDeFormulario id="loc-nome" rotulo="Nome" obrigatorio erro={errors.nome?.message}>
                <Input id="loc-nome" invalido={Boolean(errors.nome)} {...register('nome')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="loc-tipo" rotulo="Tipo" obrigatorio erro={errors.tipo?.message}>
                <Select id="loc-tipo" {...register('tipo')}>
                  {Object.entries(TIPOS_DE_LOCADORA).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>{rotulo}</option>
                  ))}
                </Select>
              </CampoDeFormulario>
              <CampoDeFormulario id="loc-consultor" rotulo="Consultor" erro={errors.consultor?.message}>
                <Input id="loc-consultor" {...register('consultor')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="loc-telefone" rotulo="Telefone" erro={errors.telefone?.message}>
                <Input id="loc-telefone" {...register('telefone')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="loc-email" rotulo="E-mail" erro={errors.email?.message}>
                <Input id="loc-email" type="email" invalido={Boolean(errors.email)} {...register('email')} />
              </CampoDeFormulario>
              <CampoDeFormulario id="loc-portal" rotulo="URL do portal" erro={errors.portalUrl?.message}>
                <Input id="loc-portal" type="url" placeholder="https://" {...register('portalUrl')} />
              </CampoDeFormulario>
            </div>

            <fieldset className="space-y-3 rounded-[var(--radius-base)] border border-borda p-4">
              <legend className="px-1 text-sm font-semibold text-texto">Credenciais do portal</legend>
              <p className="text-xs text-texto-suave">
                Armazenadas cifradas e exibidas mascaradas. Em edição, deixe em branco para
                preservar as credenciais já cadastradas.
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                <CampoDeFormulario id="loc-login" rotulo="Login" erro={errors.portalLogin?.message}>
                  <Input
                    id="loc-login"
                    autoComplete="off"
                    disabled={removerCredenciais}
                    {...register('portalLogin')}
                  />
                </CampoDeFormulario>
                <CampoDeFormulario id="loc-senha" rotulo="Senha" erro={errors.portalSenha?.message}>
                  <Input
                    id="loc-senha"
                    type="password"
                    autoComplete="new-password"
                    disabled={removerCredenciais}
                    {...register('portalSenha')}
                  />
                </CampoDeFormulario>
              </div>
              {editando && locadora.possuiCredenciais ? (
                <Switch
                  id="loc-remover-credenciais"
                  rotulo="Remover as credenciais cadastradas"
                  descricao="O acesso ao portal ficará sem login e senha registrados."
                  checked={removerCredenciais}
                  onChange={(evento) => { definirRemoverCredenciais(evento.target.checked) }}
                />
              ) : null}
            </fieldset>

            {ehNacional ? (
              <fieldset className="space-y-3 rounded-[var(--radius-base)] border border-borda p-4">
                <legend className="px-1 text-sm font-semibold text-texto">Canais de atendimento</legend>
                <p className="text-xs text-texto-suave">
                  Telefones e canais que o condutor aciona em campo. Aparecem no plano de
                  viagem e no controle de KM.
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <CampoDeFormulario id="loc-reservas" rotulo="Reservas"><Input id="loc-reservas" {...register('reservas')} /></CampoDeFormulario>
                  <CampoDeFormulario id="loc-manutencao" rotulo="Manutenção"><Input id="loc-manutencao" {...register('manutencao')} /></CampoDeFormulario>
                  <CampoDeFormulario id="loc-guincho" rotulo="Guincho / sinistro"><Input id="loc-guincho" {...register('guinchoSinistro')} /></CampoDeFormulario>
                  <CampoDeFormulario id="loc-assistencia" rotulo="Assistência 24h"><Input id="loc-assistencia" {...register('assistencia24h')} /></CampoDeFormulario>
                  <CampoDeFormulario id="loc-financeiro" rotulo="Financeiro"><Input id="loc-financeiro" {...register('financeiro')} /></CampoDeFormulario>
                  <CampoDeFormulario id="loc-suporte" rotulo="Suporte ao cliente"><Input id="loc-suporte" {...register('suporte')} /></CampoDeFormulario>
                  <CampoDeFormulario id="loc-telemetria" rotulo="Telemetria" className="sm:col-span-2"><Input id="loc-telemetria" {...register('telemetria')} /></CampoDeFormulario>
                </div>
              </fieldset>
            ) : null}

            <CampoDeFormulario id="loc-observacoes" rotulo="Observações" erro={errors.observacoes?.message}>
              <Textarea id="loc-observacoes" {...register('observacoes')} />
            </CampoDeFormulario>

            <Switch
              id="loc-ativa"
              rotulo="Locadora ativa"
              descricao="Locadoras inativas não recebem novos contratos."
              {...register('ativa')}
            />
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
