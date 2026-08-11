import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle } from 'lucide-react'
import { useState } from 'react'

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
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'
import { useDebounce } from '@/lib/use-debounce'

/** Quantos candidatos a busca traz por vez — a lista fica dentro de um diálogo. */
const CANDIDATOS = 25

interface Props {
  contratoId: number
  tipo: 'VEICULO' | 'CONDUTOR'
  aberto: boolean
  aoFechar: () => void
}

/**
 * Substituição de veículo (RN-01) e troca de condutor (RN-18).
 *
 * Um componente para as duas porque o gesto é literalmente o mesmo: escolher quem entra,
 * dizer a partir de quando e por quê. Duplicá-lo faria as duas telas divergirem na
 * primeira correção feita em uma só.
 *
 * A data não tem valor padrão de "hoje". Uma troca costuma ser registrada dias depois de
 * acontecer, e um campo já preenchido com a data de hoje seria aceito sem leitura — o
 * histórico ganharia a data do lançamento em vez da data do fato, que é exatamente o
 * defeito que a RN-18 existe para evitar.
 *
 * O erro do servidor é exibido na íntegra, e não traduzido: as mensagens do domínio já
 * dizem qual é o limite ("a substituição precisa começar depois de 14/08/2025"), e
 * reescrevê-las aqui só produziria uma segunda versão para manter.
 */
export function DialogoDeTroca({ contratoId, tipo, aberto, aoFechar }: Props) {
  const clienteDeConsultas = useQueryClient()
  const ehVeiculo = tipo === 'VEICULO'

  const [termo, definirTermo] = useState('')
  const [escolhido, definirEscolhido] = useState('')
  const [aPartirDe, definirAPartirDe] = useState('')
  const [motivo, definirMotivo] = useState('')
  const termoAplicado = useDebounce(termo)

  const veiculos = useQuery({
    queryKey: ['veiculos', 'candidatos', termoAplicado],
    enabled: aberto && ehVeiculo,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/veiculos', {
          params: { query: { termo: termoAplicado || undefined, page: 0, size: CANDIDATOS } },
        }),
      ),
  })

  const condutores = useQuery({
    queryKey: ['condutores', 'candidatos', termoAplicado],
    enabled: aberto && !ehVeiculo,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/condutores', {
          params: { query: { termo: termoAplicado || undefined, page: 0, size: CANDIDATOS } },
        }),
      ),
  })

  const opcoes = ehVeiculo
    ? (veiculos.data?.conteudo ?? []).map((v) => ({
        id: v.id,
        rotulo: `${v.placaFormatada} — ${v.modelo}`,
      }))
    : (condutores.data?.conteudo ?? []).map((c) => ({
        id: c.id,
        rotulo: c.cnhVencida ? `${c.nome} (CNH vencida)` : c.nome,
      }))

  const envio = useMutation({
    mutationFn: async () => {
      const id = Number(escolhido)
      if (ehVeiculo) {
        return exigirSucesso(
          await api.POST('/api/v1/contratos/{id}/substituicoes', {
            params: { path: { id: contratoId } },
            body: { veiculoId: id, aPartirDe, motivo: motivo || undefined },
          }),
        )
      }
      return exigirSucesso(
        await api.POST('/api/v1/contratos/{id}/trocas-de-condutor', {
          params: { path: { id: contratoId } },
          body: { condutorId: id, aPartirDe, motivo: motivo || undefined },
        }),
      )
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', contratoId] })
      await clienteDeConsultas.invalidateQueries({ queryKey: ['contratos'] })
      limpar()
      aoFechar()
    },
  })

  function limpar() {
    definirTermo('')
    definirEscolhido('')
    definirAPartirDe('')
    definirMotivo('')
    envio.reset()
  }

  const podeEnviar = escolhido !== '' && aPartirDe !== '' && !envio.isPending

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
          <DialogTitulo>
            {ehVeiculo ? 'Substituir veículo' : 'Trocar condutor'}
          </DialogTitulo>
          <DialogDescricao>
            {ehVeiculo
              ? 'O período do veículo atual é encerrado na véspera, sem lacuna nem sobreposição (RN-01).'
              : 'O período do condutor atual é encerrado na véspera. O histórico anterior permanece consultável (RN-18).'}
          </DialogDescricao>
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

          <CampoDeFormulario id="busca-candidato" rotulo={ehVeiculo ? 'Buscar veículo' : 'Buscar condutor'}>
            <Input
              id="busca-candidato"
              value={termo}
              onChange={(evento) => {
                definirTermo(evento.target.value)
              }}
              placeholder={ehVeiculo ? 'Placa ou modelo' : 'Nome ou CPF'}
            />
          </CampoDeFormulario>

          <CampoDeFormulario
            id="candidato"
            rotulo={ehVeiculo ? 'Novo veículo' : 'Novo condutor'}
            obrigatorio
          >
            <Select
              id="candidato"
              value={escolhido}
              onChange={(evento) => {
                definirEscolhido(evento.target.value)
              }}
            >
              <option value="">Selecione…</option>
              {opcoes.map((opcao) => (
                <option key={opcao.id} value={opcao.id}>
                  {opcao.rotulo}
                </option>
              ))}
            </Select>
          </CampoDeFormulario>

          <CampoDeFormulario
            id="a-partir-de"
            rotulo="A partir de"
            obrigatorio
            dica="A data em que a troca aconteceu de fato, não a data do lançamento."
          >
            <Input
              id="a-partir-de"
              type="date"
              value={aPartirDe}
              onChange={(evento) => {
                definirAPartirDe(evento.target.value)
              }}
            />
          </CampoDeFormulario>

          <CampoDeFormulario id="motivo" rotulo="Motivo">
            <Input
              id="motivo"
              value={motivo}
              onChange={(evento) => {
                definirMotivo(evento.target.value)
              }}
              placeholder={ehVeiculo ? 'Ex.: manutenção prolongada' : 'Ex.: férias do condutor'}
              maxLength={300}
            />
          </CampoDeFormulario>
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
          <Button
            disabled={!podeEnviar}
            onClick={() => {
              envio.mutate()
            }}
          >
            {envio.isPending ? 'Registrando…' : 'Registrar'}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
