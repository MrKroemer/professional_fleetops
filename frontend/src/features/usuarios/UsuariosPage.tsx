import { useQuery } from '@tanstack/react-query'
import { Search, Users } from 'lucide-react'
import { useEffect, useState } from 'react'

import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { EstadoDeErro, EstadoVazio } from '@/components/ui/estados'
import { Input } from '@/components/ui/input'
import { SkeletonDeTabela } from '@/components/ui/skeleton'
import type { Perfil, Usuario } from '@/features/auth/tipos'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData, formatarTempoRelativo } from '@/lib/formatters'

/**
 * Administração de usuários (RN-19 — exclusiva do perfil ADMIN).
 *
 * A listagem é paginada no servidor: mesmo com poucos usuários hoje, o padrão de
 * paginação e filtro do lado do servidor já fica estabelecido para as telas de
 * contratos e lançamentos, que serão volumosas.
 */
export function UsuariosPage() {
  const [termo, definirTermo] = useState('')
  const termoAplicado = useTermoComAtraso(termo, 350)

  const consulta = useQuery({
    queryKey: ['usuarios', { termo: termoAplicado }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/usuarios', {
          params: {
            query: {
              ...(termoAplicado ? { termo: termoAplicado } : {}),
              page: 0,
              size: 50,
              sort: ['nome,asc'],
            },
          },
        }),
      ),
  })

  const usuarios = consulta.data?.conteudo ?? []
  const buscando = termoAplicado.length > 0

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-texto">Usuários</h1>
        <p className="mt-1.5 text-sm text-texto-suave">
          Quem tem acesso ao FleetOps e com qual perfil. A criação e a edição de usuários entram na
          Fase 1, junto dos demais cadastros.
        </p>
      </header>

      <div className="relative mb-4 max-w-sm">
        <Search
          className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-texto-tenue"
          aria-hidden="true"
        />
        <Input
          type="search"
          value={termo}
          onChange={(evento) => {
            definirTermo(evento.target.value)
          }}
          placeholder="Buscar por nome ou e-mail"
          className="pl-8"
          aria-label="Buscar usuários por nome ou e-mail"
        />
      </div>

      {consulta.isPending ? (
        <SkeletonDeTabela linhas={4} colunas={4} />
      ) : consulta.isError ? (
        <EstadoDeErro
          erro={consulta.error}
          aoTentarNovamente={() => {
            void consulta.refetch()
          }}
        />
      ) : usuarios.length === 0 ? (
        <EstadoVazio
          icone={<Users className="size-6" />}
          titulo={buscando ? 'Nenhum usuário encontrado' : 'Nenhum usuário cadastrado'}
          descricao={
            buscando
              ? `A busca por “${termoAplicado}” não retornou resultados. Verifique a grafia ou limpe o filtro.`
              : 'Nenhum usuário está cadastrado no sistema no momento.'
          }
        />
      ) : (
        <TabelaDeUsuarios usuarios={usuarios} total={consulta.data?.totalElementos ?? 0} />
      )}
    </div>
  )
}

function TabelaDeUsuarios({ usuarios, total }: { usuarios: Usuario[]; total: number }) {
  return (
    <Card className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <caption className="sr-only">
            Lista de usuários com acesso ao sistema, com perfil e último acesso
          </caption>
          <thead>
            <tr className="border-b border-borda bg-fundo-alternativo/60 text-left">
              <th scope="col" className="px-4 py-2.5 font-medium text-texto-suave">
                Usuário
              </th>
              <th scope="col" className="px-4 py-2.5 font-medium text-texto-suave">
                Perfil
              </th>
              <th scope="col" className="px-4 py-2.5 font-medium text-texto-suave">
                Situação
              </th>
              <th scope="col" className="px-4 py-2.5 font-medium text-texto-suave">
                Último acesso
              </th>
              <th scope="col" className="px-4 py-2.5 font-medium text-texto-suave">
                Cadastrado em
              </th>
            </tr>
          </thead>
          <tbody>
            {usuarios.map((usuario) => (
              <tr key={usuario.id} className="border-b border-borda last:border-0">
                <td className="px-4 py-3">
                  <p className="font-medium text-texto">{usuario.nome}</p>
                  <p className="text-xs text-texto-suave">{usuario.email}</p>
                </td>
                <td className="px-4 py-3">
                  <Badge variante={varianteDoPerfil(usuario.perfil)}>{usuario.perfilDescricao}</Badge>
                </td>
                <td className="px-4 py-3">
                  <Badge variante={usuario.ativo ? 'sucesso' : 'neutra'}>
                    {usuario.ativo ? 'Ativo' : 'Inativo'}
                  </Badge>
                </td>
                <td className="px-4 py-3 text-texto-suave">
                  {usuario.ultimoAcessoEm ? formatarTempoRelativo(usuario.ultimoAcessoEm) : 'Nunca'}
                </td>
                <td className="px-4 py-3 text-texto-suave">{formatarData(usuario.criadoEm)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="border-t border-borda px-4 py-2.5 text-xs text-texto-tenue">
        {total === 1 ? '1 usuário' : `${String(total)} usuários`}
      </p>
    </Card>
  )
}

function varianteDoPerfil(perfil: Perfil): 'critica' | 'marca' | 'neutra' {
  if (perfil === 'ADMIN') return 'critica'
  if (perfil === 'GESTOR_FROTA') return 'marca'
  return 'neutra'
}

/** Adia a busca enquanto o usuário ainda digita, evitando uma chamada por tecla. */
function useTermoComAtraso(valor: string, atrasoMs: number): string {
  const [adiado, definirAdiado] = useState(valor)

  useEffect(() => {
    const temporizador = setTimeout(() => {
      definirAdiado(valor)
    }, atrasoMs)
    return () => {
      clearTimeout(temporizador)
    }
  }, [valor, atrasoMs])

  return adiado
}
