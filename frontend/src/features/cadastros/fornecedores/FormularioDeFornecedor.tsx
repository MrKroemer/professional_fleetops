import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
import {
  DIAS_DA_SEMANA,
  TIPOS_DE_FORNECEDOR,
  UNIDADES_FEDERATIVAS,
  type DiaDaSemana,
  type Fornecedor,
} from '@/features/cadastros/tipos'
import { api, exigirSucesso } from '@/lib/api/client'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'

/**
 * Formulário de fornecedor credenciado.
 *
 * Os campos específicos aparecem conforme o tipo escolhido, e apenas o bloco
 * correspondente é enviado. O backend recusa dados de outro tipo em vez de descartá-los
 * silenciosamente — daí a interface não deixar preencher o que não será aceito.
 */
const esquema = z.object({
  tipo: z.enum(['POSTO', 'LAVA_JATO', 'BORRACHARIA', 'PARA_BRISAS', 'RASTREADOR', 'GRAFICA', 'OFICINA']),
  nome: z.string().min(1, 'Informe o nome').max(180, 'No máximo 180 caracteres'),
  cidade: z.string().max(120),
  uf: z.string(),
  endereco: z.string().max(300),
  telefone: z.string().max(120),
  email: z.union([z.literal(''), z.string().email('Informe um e-mail válido')]),
  responsavel: z.string().max(160),
  funcionamento: z.string().max(200),
  formaFaturamento: z.string().max(200),
  formaPagamento: z.string().max(200),
  credenciadoEm: z.string(),
  ativo: z.boolean(),
  observacoes: z.string().max(2000),

  acessoFaturas: z.string().max(120),
  servicosPorSemana: z.string(),
  precoPasseio: z.string(),
  precoSuv: z.string(),
  precoQuatroXQuatro: z.string(),
  mensalidade: z.string(),
  custoInstalacao: z.string(),
  custoDesinstalacao: z.string(),
  equipadora: z.string().max(180),
  portalUrl: z.string().max(400),
  portalLogin: z.string().max(200),
  portalSenha: z.string().max(200),
  tamanhoAdesivo: z.string().max(40),
  precoAdesivo: z.string(),
  tamanhoIma: z.string().max(40),
  precoIma: z.string(),
})

type DadosDoFormulario = z.infer<typeof esquema>

/** Valores iniciais derivados do registro; credenciais nascem vazias (RN-20). */
function valoresDe(fornecedor: Fornecedor | null): DadosDoFormulario {
  const vazio: DadosDoFormulario = {
    tipo: 'POSTO', nome: '', cidade: '', uf: '', endereco: '', telefone: '', email: '',
    responsavel: '', funcionamento: '', formaFaturamento: '', formaPagamento: '',
    credenciadoEm: '', ativo: true, observacoes: '', acessoFaturas: '',
    servicosPorSemana: '1', precoPasseio: '', precoSuv: '', precoQuatroXQuatro: '',
    mensalidade: '', custoInstalacao: '', custoDesinstalacao: '', equipadora: '',
    portalUrl: '', portalLogin: '', portalSenha: '', tamanhoAdesivo: '', precoAdesivo: '',
    tamanhoIma: '', precoIma: '',
  }
  if (!fornecedor) {
    return vazio
  }
  return {
    ...vazio,
    tipo: fornecedor.tipo,
    nome: fornecedor.nome,
    cidade: fornecedor.cidade ?? '',
    uf: fornecedor.uf ?? '',
    endereco: fornecedor.endereco ?? '',
    telefone: fornecedor.telefone ?? '',
    email: fornecedor.email ?? '',
    responsavel: fornecedor.responsavel ?? '',
    funcionamento: fornecedor.funcionamento ?? '',
    formaFaturamento: fornecedor.formaFaturamento ?? '',
    formaPagamento: fornecedor.formaPagamento ?? '',
    credenciadoEm: fornecedor.credenciadoEm ?? '',
    ativo: fornecedor.ativo,
    observacoes: fornecedor.observacoes ?? '',
    acessoFaturas: fornecedor.posto?.acessoFaturas ?? '',
    servicosPorSemana: String(fornecedor.lavaJato?.servicosPorSemana ?? 1),
    precoPasseio: fornecedor.lavaJato?.precoPasseio?.toString() ?? '',
    precoSuv: fornecedor.lavaJato?.precoSuv?.toString() ?? '',
    precoQuatroXQuatro: fornecedor.lavaJato?.precoQuatroXQuatro?.toString() ?? '',
    mensalidade: fornecedor.rastreador?.mensalidade?.toString() ?? '',
    custoInstalacao: fornecedor.rastreador?.custoInstalacao?.toString() ?? '',
    custoDesinstalacao: fornecedor.rastreador?.custoDesinstalacao?.toString() ?? '',
    equipadora: fornecedor.rastreador?.equipadora ?? '',
    portalUrl: fornecedor.rastreador?.portalUrl ?? '',
    tamanhoAdesivo: fornecedor.grafica?.tamanhoAdesivo ?? '',
    precoAdesivo: fornecedor.grafica?.precoAdesivo?.toString() ?? '',
    tamanhoIma: fornecedor.grafica?.tamanhoIma ?? '',
    precoIma: fornecedor.grafica?.precoIma?.toString() ?? '',
  }
}

/** Converte texto do formulário em número; vazio vira ausência de valor, não zero. */
function paraNumero(texto: string): number | undefined {
  const limpo = texto.replace(',', '.').trim()
  if (!limpo) {
    return undefined
  }
  const valor = Number(limpo)
  return Number.isFinite(valor) ? valor : undefined
}

export function FormularioDeFornecedor({
  fornecedor,
  aoFechar,
}: {
  fornecedor: Fornecedor | null
  aoFechar: () => void
}) {
  const clienteDeConsultas = useQueryClient()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)
  // Seleções múltiplas ficam fora do react-hook-form: são listas, não campos.
  const [diasAutorizados, definirDiasAutorizados] = useState<DiaDaSemana[]>(
    fornecedor?.posto?.diasAutorizados ?? [],
  )
  const [obrasSelecionadas, definirObrasSelecionadas] = useState<number[]>(
    fornecedor?.obras.map((obra) => obra.id) ?? [],
  )
  const editando = fornecedor !== null

  const obras = useQuery({
    queryKey: ['obras', 'selecao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/obras', {
          params: { query: { page: 0, size: 200, sort: ['codigo,asc'] } },
        }),
      ),
  })

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DadosDoFormulario>({ resolver: zodResolver(esquema), defaultValues: valoresDe(fornecedor) })


  const salvar = useMutation({
    mutationFn: async (dados: DadosDoFormulario) => {
      const tipo = dados.tipo
      const corpo = {
        tipo,
        nome: dados.nome.trim(),
        cidade: dados.cidade.trim() || undefined,
        uf: dados.uf || undefined,
        endereco: dados.endereco.trim() || undefined,
        telefone: dados.telefone.trim() || undefined,
        email: dados.email.trim() || undefined,
        responsavel: dados.responsavel.trim() || undefined,
        funcionamento: dados.funcionamento.trim() || undefined,
        formaFaturamento: dados.formaFaturamento.trim() || undefined,
        formaPagamento: dados.formaPagamento.trim() || undefined,
        credenciadoEm: dados.credenciadoEm || undefined,
        ativo: dados.ativo,
        observacoes: dados.observacoes.trim() || undefined,
        obrasIds: obrasSelecionadas,
        ...(tipo === 'POSTO'
          ? { posto: { diasAutorizados, acessoFaturas: dados.acessoFaturas.trim() || undefined } }
          : {}),
        ...(tipo === 'LAVA_JATO'
          ? {
              lavaJato: {
                servicosPorSemana: paraNumero(dados.servicosPorSemana) ?? 1,
                precoPasseio: paraNumero(dados.precoPasseio),
                precoSuv: paraNumero(dados.precoSuv),
                precoQuatroXQuatro: paraNumero(dados.precoQuatroXQuatro),
              },
            }
          : {}),
        ...(tipo === 'RASTREADOR'
          ? {
              rastreador: {
                mensalidade: paraNumero(dados.mensalidade),
                custoInstalacao: paraNumero(dados.custoInstalacao),
                custoDesinstalacao: paraNumero(dados.custoDesinstalacao),
                equipadora: dados.equipadora.trim() || undefined,
                portalUrl: dados.portalUrl.trim() || undefined,
                // Omitir preserva a credencial atual (RN-20).
                ...(dados.portalLogin ? { portalLogin: dados.portalLogin } : {}),
                ...(dados.portalSenha ? { portalSenha: dados.portalSenha } : {}),
              },
            }
          : {}),
        ...(tipo === 'GRAFICA'
          ? {
              grafica: {
                tamanhoAdesivo: dados.tamanhoAdesivo.trim() || undefined,
                precoAdesivo: paraNumero(dados.precoAdesivo),
                tamanhoIma: dados.tamanhoIma.trim() || undefined,
                precoIma: paraNumero(dados.precoIma),
              },
            }
          : {}),
      }

      if (fornecedor) {
        return exigirSucesso(
          await api.PUT('/api/v1/fornecedores/{id}', {
            params: { path: { id: fornecedor.id } },
            body: corpo,
          }),
        )
      }
      return exigirSucesso(await api.POST('/api/v1/fornecedores', { body: corpo }))
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['fornecedores'] })
      aoFechar()
    },
  })

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await salvar.mutateAsync(dados)
    } catch (erro) {
      if (erro instanceof ErroDaApi && erro.codigo === 'CAD-005-FORNECEDOR_DUPLICADO') {
        setError('nome', { message: 'Já existe um fornecedor deste tipo com o mesmo nome' })
        return
      }
      definirErroDeEnvio(erro)
    }
  })

  // `useWatch` no lugar de `watch`: o segundo devolve uma função que o React
  // Compiler não consegue memoizar, desativando a otimização do componente inteiro.
  const tipo = useWatch({ control, name: 'tipo' })

  const alternarDia = (dia: DiaDaSemana) => {
    definirDiasAutorizados((atuais) =>
      atuais.includes(dia) ? atuais.filter((valor) => valor !== dia) : [...atuais, dia],
    )
  }

  const alternarObra = (id: number) => {
    definirObrasSelecionadas((atuais) =>
      atuais.includes(id) ? atuais.filter((valor) => valor !== id) : [...atuais, id],
    )
  }

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
          <DialogTitulo>{editando ? 'Editar fornecedor' : 'Novo fornecedor'}</DialogTitulo>
          <DialogDescricao>
            {editando
              ? 'O tipo não pode ser alterado — cadastre um novo registro se o fornecedor mudar de categoria.'
              : 'Escolha o tipo para ver os campos específicos daquele credenciamento.'}
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
              <CampoDeFormulario id="for-tipo" rotulo="Tipo" obrigatorio erro={errors.tipo?.message}>
                <Select id="for-tipo" disabled={editando} {...register('tipo')}>
                  {Object.entries(TIPOS_DE_FORNECEDOR).map(([valor, rotulo]) => (
                    <option key={valor} value={valor}>
                      {rotulo}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>

              <CampoDeFormulario id="for-nome" rotulo="Nome" obrigatorio erro={errors.nome?.message}>
                <Input id="for-nome" invalido={Boolean(errors.nome)} {...register('nome')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-cidade" rotulo="Cidade" erro={errors.cidade?.message}>
                <Input id="for-cidade" {...register('cidade')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-uf" rotulo="UF" erro={errors.uf?.message}>
                <Select id="for-uf" {...register('uf')}>
                  <option value="">Não informada</option>
                  {UNIDADES_FEDERATIVAS.map((uf) => (
                    <option key={uf} value={uf}>
                      {uf}
                    </option>
                  ))}
                </Select>
              </CampoDeFormulario>

              <CampoDeFormulario
                id="for-endereco"
                rotulo="Endereço"
                className="sm:col-span-2"
                erro={errors.endereco?.message}
              >
                <Input id="for-endereco" {...register('endereco')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-telefone" rotulo="Telefone" erro={errors.telefone?.message}>
                <Input id="for-telefone" {...register('telefone')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-email" rotulo="E-mail" erro={errors.email?.message}>
                <Input id="for-email" type="email" invalido={Boolean(errors.email)} {...register('email')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-responsavel" rotulo="Responsável" erro={errors.responsavel?.message}>
                <Input id="for-responsavel" {...register('responsavel')} />
              </CampoDeFormulario>

              <CampoDeFormulario
                id="for-funcionamento"
                rotulo="Funcionamento"
                erro={errors.funcionamento?.message}
                dica="Ex.: 24h, Seg a sex 08–17h"
              >
                <Input id="for-funcionamento" {...register('funcionamento')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-faturamento" rotulo="Faturamento" erro={errors.formaFaturamento?.message}>
                <Input id="for-faturamento" {...register('formaFaturamento')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-pagamento" rotulo="Pagamento" erro={errors.formaPagamento?.message}>
                <Input id="for-pagamento" {...register('formaPagamento')} />
              </CampoDeFormulario>

              <CampoDeFormulario id="for-credenciado" rotulo="Credenciado em" erro={errors.credenciadoEm?.message}>
                <Input id="for-credenciado" type="date" {...register('credenciadoEm')} />
              </CampoDeFormulario>
            </div>

            <fieldset className="space-y-2 rounded-[var(--radius-base)] border border-borda p-4">
              <legend className="px-1 text-sm font-semibold text-texto">Obras atendidas</legend>
              <p className="text-xs text-texto-suave">
                Um mesmo fornecedor costuma atender várias frentes próximas.
              </p>
              <div className="flex flex-wrap gap-2 pt-1">
                {(obras.data?.conteudo ?? []).map((obra) => (
                  <label
                    key={obra.id}
                    className="flex cursor-pointer items-center gap-2 rounded-[var(--radius-base)] border border-borda-forte px-2.5 py-1.5 text-sm"
                  >
                    <input
                      type="checkbox"
                      className="size-4 accent-[var(--marca)]"
                      checked={obrasSelecionadas.includes(obra.id)}
                      onChange={() => {
                        alternarObra(obra.id)
                      }}
                    />
                    <span className="text-texto">{obra.codigo}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            {tipo === 'POSTO' ? (
              <fieldset className="space-y-3 rounded-[var(--radius-base)] border border-borda p-4">
                <legend className="px-1 text-sm font-semibold text-texto">Dados do posto</legend>
                <p className="text-xs text-texto-suave">
                  Nenhum dia marcado significa sem restrição. Com dias marcados, abastecer fora
                  deles exigirá justificativa e entrará no relatório de não conformidades.
                </p>
                <div className="flex flex-wrap gap-2">
                  {DIAS_DA_SEMANA.map((dia) => (
                    <label
                      key={dia.valor}
                      className="flex cursor-pointer items-center gap-2 rounded-[var(--radius-base)] border border-borda-forte px-2.5 py-1.5 text-sm"
                    >
                      <input
                        type="checkbox"
                        className="size-4 accent-[var(--marca)]"
                        checked={diasAutorizados.includes(dia.valor)}
                        onChange={() => {
                          alternarDia(dia.valor)
                        }}
                      />
                      <span className="text-texto">{dia.abreviado}</span>
                    </label>
                  ))}
                </div>
                <CampoDeFormulario
                  id="for-acesso-faturas"
                  rotulo="Acesso às faturas"
                  erro={errors.acessoFaturas?.message}
                >
                  <Input id="for-acesso-faturas" {...register('acessoFaturas')} />
                </CampoDeFormulario>
              </fieldset>
            ) : null}

            {tipo === 'LAVA_JATO' ? (
              <fieldset className="grid gap-4 rounded-[var(--radius-base)] border border-borda p-4 sm:grid-cols-4">
                <legend className="px-1 text-sm font-semibold text-texto">Dados do lava-jato</legend>
                <CampoDeFormulario
                  id="for-frequencia"
                  rotulo="Serviços por semana"
                  erro={errors.servicosPorSemana?.message}
                >
                  <Input id="for-frequencia" type="number" min={1} max={7} {...register('servicosPorSemana')} />
                </CampoDeFormulario>
                <CampoDeFormulario id="for-preco-passeio" rotulo="Preço passeio">
                  <Input id="for-preco-passeio" inputMode="decimal" placeholder="0,00" {...register('precoPasseio')} />
                </CampoDeFormulario>
                <CampoDeFormulario id="for-preco-suv" rotulo="Preço SUV">
                  <Input id="for-preco-suv" inputMode="decimal" placeholder="0,00" {...register('precoSuv')} />
                </CampoDeFormulario>
                <CampoDeFormulario id="for-preco-4x4" rotulo="Preço 4x4">
                  <Input
                    id="for-preco-4x4"
                    inputMode="decimal"
                    placeholder="0,00"
                    {...register('precoQuatroXQuatro')}
                  />
                </CampoDeFormulario>
              </fieldset>
            ) : null}

            {tipo === 'RASTREADOR' ? (
              <fieldset className="space-y-4 rounded-[var(--radius-base)] border border-borda p-4">
                <legend className="px-1 text-sm font-semibold text-texto">Dados do rastreador</legend>
                <div className="grid gap-4 sm:grid-cols-3">
                  <CampoDeFormulario id="for-mensalidade" rotulo="Mensalidade">
                    <Input id="for-mensalidade" inputMode="decimal" placeholder="0,00" {...register('mensalidade')} />
                  </CampoDeFormulario>
                  <CampoDeFormulario id="for-instalacao" rotulo="Instalação">
                    <Input
                      id="for-instalacao"
                      inputMode="decimal"
                      placeholder="0,00"
                      {...register('custoInstalacao')}
                    />
                  </CampoDeFormulario>
                  <CampoDeFormulario id="for-desinstalacao" rotulo="Desinstalação">
                    <Input
                      id="for-desinstalacao"
                      inputMode="decimal"
                      placeholder="0,00"
                      {...register('custoDesinstalacao')}
                    />
                  </CampoDeFormulario>
                  <CampoDeFormulario id="for-equipadora" rotulo="Equipadora">
                    <Input id="for-equipadora" {...register('equipadora')} />
                  </CampoDeFormulario>
                  <CampoDeFormulario id="for-portal" rotulo="URL do portal" className="sm:col-span-2">
                    <Input id="for-portal" type="url" placeholder="https://" {...register('portalUrl')} />
                  </CampoDeFormulario>
                </div>
                <p className="text-xs text-texto-suave">
                  Credenciais armazenadas cifradas e exibidas mascaradas. Em edição, deixe em
                  branco para preservar as atuais.
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <CampoDeFormulario id="for-login" rotulo="Login do portal">
                    <Input id="for-login" autoComplete="off" {...register('portalLogin')} />
                  </CampoDeFormulario>
                  <CampoDeFormulario id="for-senha" rotulo="Senha do portal">
                    <Input id="for-senha" type="password" autoComplete="new-password" {...register('portalSenha')} />
                  </CampoDeFormulario>
                </div>
              </fieldset>
            ) : null}

            {tipo === 'GRAFICA' ? (
              <fieldset className="grid gap-4 rounded-[var(--radius-base)] border border-borda p-4 sm:grid-cols-4">
                <legend className="px-1 text-sm font-semibold text-texto">Dados da gráfica</legend>
                <CampoDeFormulario id="for-tam-adesivo" rotulo="Tamanho do adesivo" dica="Ex.: 40x20">
                  <Input id="for-tam-adesivo" {...register('tamanhoAdesivo')} />
                </CampoDeFormulario>
                <CampoDeFormulario id="for-preco-adesivo" rotulo="Preço do adesivo">
                  <Input id="for-preco-adesivo" inputMode="decimal" placeholder="0,00" {...register('precoAdesivo')} />
                </CampoDeFormulario>
                <CampoDeFormulario id="for-tam-ima" rotulo="Tamanho do imã">
                  <Input id="for-tam-ima" {...register('tamanhoIma')} />
                </CampoDeFormulario>
                <CampoDeFormulario id="for-preco-ima" rotulo="Preço do imã">
                  <Input id="for-preco-ima" inputMode="decimal" placeholder="0,00" {...register('precoIma')} />
                </CampoDeFormulario>
              </fieldset>
            ) : null}

            <CampoDeFormulario id="for-observacoes" rotulo="Observações" erro={errors.observacoes?.message}>
              <Textarea id="for-observacoes" {...register('observacoes')} />
            </CampoDeFormulario>

            <Switch
              id="for-ativo"
              rotulo="Fornecedor ativo"
              descricao="Fornecedores inativos não recebem novos lançamentos."
              {...register('ativo')}
            />
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
