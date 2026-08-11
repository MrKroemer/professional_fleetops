import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { useAutenticacao } from './use-autenticacao'
import { BotaoDeEntrada } from '@/components/ui/botao-de-entrada'
import { CampoDeFormulario } from '@/components/ui/campo-de-formulario'
import { Input } from '@/components/ui/input'
import { Marca } from '@/components/ui/marca'
import { descritoPor } from '@/lib/acessibilidade'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'

/**
 * Esquema de validação espelhando as regras do backend.
 *
 * A validação no cliente existe para dar retorno imediato, nunca como garantia:
 * a regra que vale é sempre a do domínio, no servidor.
 */
const esquemaDeLogin = z.object({
  email: z
    .string()
    .min(1, 'Informe o e-mail')
    .email('Informe um e-mail válido')
    .max(180, 'O e-mail deve ter no máximo 180 caracteres'),
  senha: z
    .string()
    .min(1, 'Informe a senha')
    .max(200, 'A senha deve ter no máximo 200 caracteres'),
})

type DadosDeLogin = z.infer<typeof esquemaDeLogin>

export function LoginPage() {
  const { estado, entrar } = useAutenticacao()
  const navegar = useNavigate()
  const localizacao = useLocation()
  const [erroDeEnvio, definirErroDeEnvio] = useState<unknown>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<DadosDeLogin>({
    resolver: zodResolver(esquemaDeLogin),
    defaultValues: { email: '', senha: '' },
  })

  if (estado === 'autenticado') {
    const destino = (localizacao.state as { de?: string } | null)?.de ?? '/'
    return <Navigate to={destino} replace />
  }

  const enviar = handleSubmit(async (dados) => {
    definirErroDeEnvio(null)
    try {
      await entrar(dados.email, dados.senha)
      const destino = (localizacao.state as { de?: string } | null)?.de ?? '/'
      void navegar(destino, { replace: true })
    } catch (erro) {
      definirErroDeEnvio(erro)
    }
  })

  const credenciaisRecusadas = erroDeEnvio instanceof ErroDaApi && erroDeEnvio.status === 401

  return (
    <div className="grid min-h-full lg:grid-cols-2">
      {/*
        Painel de marca — some no mobile para não roubar espaço do formulário.

        O bloco de cor chapada saiu: a marca agora se apresenta sozinha, em escala. Como
        a arte é laranja recortada sobre transparência, ela precisa de uma superfície
        neutra por trás — sobre o próprio laranja, os veículos em negativo desapareceriam.
      */}
      <aside className="relative hidden overflow-hidden border-r border-borda bg-superficie lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          className="pointer-events-none absolute inset-0 opacity-60"
          style={{
            backgroundImage:
              'radial-gradient(circle at 22% 18%, var(--marca-suave) 0, transparent 55%),' +
              'radial-gradient(circle at 78% 82%, var(--marca-suave) 0, transparent 50%)',
          }}
          aria-hidden="true"
        />

        {/*
          A logo ocupa metade da tela — que é a largura deste painel. `50vw` a dimensiona
          pela janela, e não pelo contêiner, para que "metade da tela" continue valendo
          literalmente em qualquer resolução; o `max-h` impede que ela transborde na
          vertical em telas baixas e largas.
        */}
        <div className="relative flex flex-1 items-center justify-center">
          <Marca
            tamanho="grande"
            className="h-auto max-h-[70vh] w-[50vw] max-w-none drop-shadow-[0_10px_40px_var(--marca-suave)]"
          />
        </div>

        <p className="relative text-center text-xs text-texto-tenue">
          Uso interno — Proyfe Brasil Projetos &amp; Consultoria Ltda.
        </p>
      </aside>

      {/* Formulário */}
      <main className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          {/* No mobile o painel lateral não existe, então a marca aparece aqui — sozinha,
              como no resto do sistema. */}
          <Marca tamanho="grande" className="mb-8 h-16 w-auto lg:hidden" />

          <h1 className="text-2xl font-semibold tracking-tight text-texto">Entrar</h1>
          <p className="mt-1.5 text-sm text-texto-suave">
            Use seu e-mail corporativo para acessar o sistema.
          </p>

          <form onSubmit={(evento) => void enviar(evento)} className="mt-8 space-y-4" noValidate>
            {erroDeEnvio ? (
              <div
                role="alert"
                className="flex items-start gap-2.5 rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/50 px-3 py-2.5"
              >
                <AlertCircle className="mt-0.5 size-4 shrink-0 text-critico" aria-hidden="true" />
                <div className="text-sm">
                  <p className="font-medium text-texto">
                    {credenciaisRecusadas ? 'Não foi possível entrar' : 'Falha na autenticação'}
                  </p>
                  <p className="text-texto-suave">{mensagemDeErro(erroDeEnvio)}</p>
                </div>
              </div>
            ) : null}

            <CampoDeFormulario id="email" rotulo="E-mail" erro={errors.email?.message} obrigatorio>
              <Input
                id="email"
                type="email"
                inputMode="email"
                autoComplete="username"
                autoFocus
                placeholder="nome.sobrenome@proyfebrasil.com.br"
                invalido={Boolean(errors.email)}
                aria-describedby={descritoPor('email', false, Boolean(errors.email))}
                {...register('email')}
              />
            </CampoDeFormulario>

            <CampoDeFormulario id="senha" rotulo="Senha" erro={errors.senha?.message} obrigatorio>
              <Input
                id="senha"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                invalido={Boolean(errors.senha)}
                aria-describedby={descritoPor('senha', false, Boolean(errors.senha))}
                {...register('senha')}
              />
            </CampoDeFormulario>

            {/* O botão tem largura fixa de 8rem, definida na especificação — daí ser
                centralizado em vez de esticado. O `pt` maior acomoda o contorno de foco,
                que se projeta 6px para fora. */}
            <div className="flex justify-center pt-4">
              <BotaoDeEntrada carregando={isSubmitting}>Entrar</BotaoDeEntrada>
            </div>

            {/* O rótulo do botão não muda para "Entrando…": a caixa tem 8rem fixos, e o
                texto maior estouraria a geometria da especificação. Quem enxerga percebe
                o envio pelo botão esmaecido e imóvel; para leitor de tela, o aviso vem
                daqui, que é o que `aria-busy` sozinho não garante anunciar. */}
            <p role="status" aria-live="polite" className="sr-only">
              {isSubmitting ? 'Entrando…' : ''}
            </p>
          </form>

          <p className="mt-8 text-xs leading-relaxed text-texto-tenue">
            Esqueceu a senha ou precisa de acesso? Procure o setor de Frotas —
            <span className="whitespace-nowrap"> atendimento.frota@proyfebrasil.com.br</span>.
          </p>
        </div>
      </main>
    </div>
  )
}
