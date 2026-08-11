import { useMutation, useQuery } from '@tanstack/react-query'
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
import { Textarea } from '@/components/ui/textarea'
import { api, exigirSucesso } from '@/lib/api/client'
import { mensagemDeErro } from '@/lib/api/problem'

interface Props {
  contratoId: number
  tipo: 'RETIRADA' | 'DEVOLUCAO'
  aberto: boolean
  aoFechar: () => void | Promise<void>
}

/**
 * Abertura de uma retirada ou devolução.
 *
 * Coleta só o cabeçalho — veículo, condutor, data, km, local e o checklist da locadora.
 * As fotos e o CRLV ficam para o painel do evento, depois de criado: são oito envios
 * que precisam sobreviver a uma queda de rede, e nenhum deles cabe em um diálogo modal
 * que se perde ao fechar sem querer.
 *
 * A lista de veículos vem do contrato, não do cadastro inteiro: a devolução é quase
 * sempre de um veículo já substituído, e caçá-lo entre 367 placas seria absurdo. Os
 * candidatos são os veículos que passaram por este contrato.
 */
export function DialogoDeNovoEvento({ contratoId, tipo, aberto, aoFechar }: Props) {
  const [veiculoId, definirVeiculoId] = useState('')
  const [condutorId, definirCondutorId] = useState('')
  const [dataDoEvento, definirData] = useState('')
  const [km, definirKm] = useState('')
  const [local, definirLocal] = useState('')
  const [checklist, definirChecklist] = useState('')

  /**
   * Candidatos tirados da própria linha do tempo.
   *
   * Os marcos de veículo carregam a placa formatada, mas não o identificador do veículo —
   * `referenciaId` aponta para o período. Por isso a lista completa de veículos do
   * contrato vem da consulta de veículos filtrada pelas placas do histórico.
   */
  const linhaDoTempo = useQuery({
    queryKey: ['contrato', contratoId, 'linha-do-tempo'],
    enabled: aberto,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/contratos/{id}/linha-do-tempo', {
          params: { path: { id: contratoId } },
        }),
      ),
  })

  const placas = [
    ...new Set(
      (linhaDoTempo.data?.marcos ?? [])
        .filter((marco) => marco.tipo.includes('VEICULO') || marco.tipo.includes('SUBSTITUICAO'))
        .map((marco) => marco.rotulo),
    ),
  ]

  const veiculos = useQuery({
    queryKey: ['veiculos', 'do-contrato', placas],
    enabled: aberto && placas.length > 0,
    queryFn: async () => {
      const encontrados = await Promise.all(
        placas.map(async (placa) =>
          exigirSucesso(
            await api.GET('/api/v1/veiculos', {
              params: { query: { termo: placa.replace('-', ''), page: 0, size: 1 } },
            }),
          ),
        ),
      )
      return encontrados.flatMap((pagina) => pagina.conteudo)
    },
  })

  const condutores = useQuery({
    queryKey: ['condutores', 'ativos-para-evento'],
    enabled: aberto,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/condutores', { params: { query: { page: 0, size: 200 } } }),
      ),
  })

  const envio = useMutation({
    mutationFn: async () =>
      exigirSucesso(
        await api.POST('/api/v1/contratos/{id}/eventos', {
          params: { path: { id: contratoId } },
          body: {
            tipo,
            veiculoId: Number(veiculoId),
            condutorId: condutorId ? Number(condutorId) : undefined,
            dataDoEvento,
            km: km ? Number(km) : undefined,
            localDoEvento: local || undefined,
            checklistDaLocadora: checklist || undefined,
          },
        }),
      ),
    onSuccess: async () => {
      limpar()
      await aoFechar()
    },
  })

  function limpar() {
    definirVeiculoId('')
    definirCondutorId('')
    definirData('')
    definirKm('')
    definirLocal('')
    definirChecklist('')
    envio.reset()
  }

  const rotulo = tipo === 'RETIRADA' ? 'retirada' : 'devolução'

  return (
    <Dialog
      open={aberto}
      onOpenChange={(estaAberto) => {
        if (!estaAberto) {
          limpar()
          void aoFechar()
        }
      }}
    >
      <DialogConteudo className="sm:max-w-xl">
        <DialogCabecalho>
          <DialogTitulo>Registrar {rotulo}</DialogTitulo>
          <DialogDescricao>
            O evento nasce em preenchimento. As fotos do book e o CRLV são enviados na
            sequência, e a conclusão só é liberada quando estiverem completos (RN-12).
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

          <div className="grid gap-4 sm:grid-cols-2">
            <CampoDeFormulario id="veiculo-do-evento" rotulo="Veículo" obrigatorio>
              <Select
                id="veiculo-do-evento"
                value={veiculoId}
                onChange={(evento) => definirVeiculoId(evento.target.value)}
              >
                <option value="">Selecione…</option>
                {(veiculos.data ?? []).map((veiculo) => (
                  <option key={veiculo.id} value={veiculo.id}>
                    {veiculo.placaFormatada} — {veiculo.modelo}
                  </option>
                ))}
              </Select>
            </CampoDeFormulario>

            <CampoDeFormulario id="condutor-do-evento" rotulo="Condutor">
              <Select
                id="condutor-do-evento"
                value={condutorId}
                onChange={(evento) => definirCondutorId(evento.target.value)}
              >
                <option value="">Sem condutor</option>
                {(condutores.data?.conteudo ?? []).map((condutor) => (
                  <option key={condutor.id} value={condutor.id}>
                    {condutor.nome}
                  </option>
                ))}
              </Select>
            </CampoDeFormulario>

            <CampoDeFormulario id="data-do-evento" rotulo={`Data da ${rotulo}`} obrigatorio>
              <Input
                id="data-do-evento"
                type="date"
                value={dataDoEvento}
                onChange={(evento) => definirData(evento.target.value)}
              />
            </CampoDeFormulario>

            <CampoDeFormulario id="km-do-evento" rotulo="Quilometragem">
              <Input
                id="km-do-evento"
                type="number"
                min={0}
                value={km}
                onChange={(evento) => definirKm(evento.target.value)}
                placeholder="Hodômetro no momento"
              />
            </CampoDeFormulario>
          </div>

          <CampoDeFormulario id="local-do-evento" rotulo="Local">
            <Input
              id="local-do-evento"
              value={local}
              onChange={(evento) => definirLocal(evento.target.value)}
              maxLength={180}
              placeholder="Ex.: pátio da Unidas — Recife/PE"
            />
          </CampoDeFormulario>

          <CampoDeFormulario
            id="checklist-do-evento"
            rotulo="Checklist da locadora"
            dica="Transcreva o que a locadora conferiu e apontou no ato."
          >
            <Textarea
              id="checklist-do-evento"
              rows={3}
              value={checklist}
              onChange={(evento) => definirChecklist(evento.target.value)}
            />
          </CampoDeFormulario>
        </DialogCorpo>

        <DialogRodape>
          <Button
            variante="secundaria"
            onClick={() => {
              limpar()
              void aoFechar()
            }}
          >
            Cancelar
          </Button>
          <Button
            disabled={!veiculoId || !dataDoEvento || envio.isPending}
            onClick={() => envio.mutate()}
          >
            {envio.isPending ? 'Abrindo…' : `Abrir ${rotulo}`}
          </Button>
        </DialogRodape>
      </DialogConteudo>
    </Dialog>
  )
}
