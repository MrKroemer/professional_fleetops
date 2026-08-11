import { Route, Routes } from 'react-router-dom'

import { AppLayout } from './layout/AppLayout'
import { LoginPage } from '@/features/auth/LoginPage'
import { RotaProtegida } from '@/features/auth/RotaProtegida'
import { CondutoresPage } from '@/features/cadastros/condutores/CondutoresPage'
import { FornecedoresPage } from '@/features/cadastros/fornecedores/FornecedoresPage'
import { LocadorasPage } from '@/features/cadastros/locadoras/LocadorasPage'
import { ObrasPage } from '@/features/cadastros/obras/ObrasPage'
import { TabelasDePrecoPage } from '@/features/cadastros/precos/TabelasDePrecoPage'
import { VeiculoPage } from '@/features/cadastros/veiculos/VeiculoPage'
import { VeiculosPage } from '@/features/cadastros/veiculos/VeiculosPage'
import { ContratoPage } from '@/features/contratos/ContratoPage'
import { ContratosPage } from '@/features/contratos/ContratosPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { NaoEncontradaPage } from '@/features/erros/NaoEncontradaPage'
import { OperacaoPage } from '@/features/operacao/OperacaoPage'
import { UsuariosPage } from '@/features/usuarios/UsuariosPage'

/**
 * Rotas da aplicação.
 *
 * As áreas ainda não entregues (conformidade, relatórios) não têm rota: elas entram
 * junto com suas telas, nas Fases 4 e 5. Registrar rotas vazias agora só produziria
 * becos sem saída.
 */
export function Rotas() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />

      <Route element={<RotaProtegida />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />

          {/* Ciclo de vida do contrato (Fase 2). A página do contrato é rota própria,
              e não painel lateral: retirada, trocas e devolução precisam de espaço. */}
          <Route path="contratos" element={<ContratosPage />} />
          <Route path="contratos/:id" element={<ContratoPage />} />

          {/* Operação mensal (Fase 3): a tela é por competência, não por contrato —
              é assim que a conferência do mês acontece. */}
          <Route path="operacao" element={<OperacaoPage />} />

          {/* Cadastros — leitura liberada a todos os perfis; a escrita é barrada
              pelo backend e as ações ficam ocultas para quem não pode editar. */}
          <Route path="cadastros/obras" element={<ObrasPage />} />
          <Route path="cadastros/locadoras" element={<LocadorasPage />} />
          <Route path="cadastros/condutores" element={<CondutoresPage />} />
          <Route path="cadastros/veiculos" element={<VeiculosPage />} />
          {/* Painel individual do veículo, alvo dos cards do dashboard. */}
          <Route path="cadastros/veiculos/:id" element={<VeiculoPage />} />
          <Route path="cadastros/fornecedores" element={<FornecedoresPage />} />
          <Route path="cadastros/tabelas-preco" element={<TabelasDePrecoPage />} />

          <Route element={<RotaProtegida perfis={['ADMIN']} />}>
            <Route path="usuarios" element={<UsuariosPage />} />
          </Route>
          <Route path="*" element={<NaoEncontradaPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
