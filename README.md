# FleetOps — Sistema de Gestão de Frotas Locadas

Sistema web da **Proyfe Brasil Projetos & Consultoria Ltda.** para gerir a frota de veículos
alugados de locadoras parceiras e distribuídos entre condutores nas obras.

Substitui o conjunto de ~20 planilhas Excel e formulários Word usados hoje: controle geral de
veículos, controles por obra (KM, abastecimento, lava-jato, borracharia), avarias, manutenções,
multas, fornecedores credenciados e tabelas de preço das locadoras.

> **Estado atual: Fases 0 a 3 concluídas.**
> A fundação (acesso, perfis, auditoria, contrato de erros, OpenAPI, ambiente Docker), os
> cadastros base (obras, locadoras, condutores, veículos, fornecedores credenciados e
> tabelas de preço) e o ciclo de vida do contrato (retirada com book fotográfico,
> substituições, trocas de condutor e devolução) e a operação mensal (quilometragem,
> abastecimentos, serviços, fechamento calculado e faturas) estão operacionais. A
> conformidade entra na Fase 4 — ver [Fases de entrega](#fases-de-entrega).

---

## Sumário

- [Subir o ambiente](#subir-o-ambiente)
- [Primeiro acesso](#primeiro-acesso)
- [Rodar os testes e a verificação](#rodar-os-testes-e-a-verificação)
- [Mapa dos módulos](#mapa-dos-módulos)
- [Criar uma migração](#criar-uma-migração)
- [Regenerar o cliente da API](#regenerar-o-cliente-da-api)
- [Decisões de arquitetura](#decisões-de-arquitetura)
- [Fases de entrega](#fases-de-entrega)

---

## Subir o ambiente

**Pré-requisitos:** Docker e Docker Compose. Java e Node **não** são necessários — tudo roda em
container.

```bash
cp .env.example .env
```

Gere os segredos e coloque-os no `.env`:

```bash
openssl rand -base64 48   # → FLEETOPS_JWT_SECRET
openssl rand -base64 32   # → FLEETOPS_CRYPTO_KEY  (exatamente 32 bytes; RN-20)
```

Defina também `POSTGRES_PASSWORD` e `MINIO_ROOT_PASSWORD`. A aplicação **não sobe** sem o
segredo JWT e sem a chave de criptografia — por desenho, para que nenhum ambiente rode com
credenciais padrão.

### Desenvolvimento

Backend com recompilação a partir do código montado do host, frontend com HMR do Vite e usuários
fictícios de seed:

```bash
docker compose --profile dev up
```

| Serviço            | Endereço                        |
| ------------------ | ------------------------------- |
| Aplicação (Vite)   | http://localhost:5173           |
| API                | http://localhost:8080/api/v1    |
| Swagger UI         | http://localhost:8080/swagger-ui.html |
| Console do MinIO   | http://localhost:9001           |
| PostgreSQL         | localhost:5432                  |

### Produção

Imagens multi-estágio enxutas: JRE Temurin no backend, Nginx servindo os estáticos e fazendo
proxy de `/api`, tudo na mesma origem.

```bash
docker compose --profile prod up --build
```

> **Os dois perfis não convivem na mesma máquina.** Eles usam as mesmas portas e o mesmo
> Postgres — subir `prod` sobre um ambiente `dev` em uso sobrescreve o banco e derruba a
> porta do backend. O perfil `prod` existe para o **servidor de produção**, onde é a única
> coisa rodando; localmente, use sempre `--profile dev`.
>
> Se precisar validar o build de produção na sua máquina, derrube o outro perfil antes e
> devolva o ambiente depois:
>
> ```bash
> docker compose --profile dev --profile prod down -v --remove-orphans
> docker compose --profile prod up --build          # validação
> docker compose --profile dev --profile prod down -v --remove-orphans
> docker compose --profile dev up -d                # volta ao ambiente de trabalho
> ```

> **Cuidado com `docker compose up -d <serviço>`.** Nomear um serviço explicitamente
> **ignora o perfil** dele: `docker compose up -d backend` sobe o backend de produção mesmo
> sem `--profile prod`, e o container fica lá, disputando a porta 8090 com o de
> desenvolvimento. Quando isso acontece, os containers de `dev` ficam em estado `Created` e
> nunca sobem — `docker compose ps -a` mostra isso na hora.

> **Portas ocupadas?** Todas são configuráveis pelo `.env` (`BACKEND_PORT`, `FRONTEND_PORT`,
> `POSTGRES_PORT`, `MINIO_API_PORT`, `MINIO_CONSOLE_PORT`). Ajuste se outro projeto já usar
> alguma delas na sua máquina.

---

## Primeiro acesso

### No perfil `dev`

O seed cria um usuário por perfil de acesso. Senha comum, definida por `FLEETOPS_SEED_SENHA`
(padrão `Fleet@2026`):

| E-mail                          | Perfil         | Pode fazer                                             |
| ------------------------------- | -------------- | ------------------------------------------------------ |
| `admin@proyfebrasil.com.br`     | `ADMIN`        | Tudo, incluindo administrar usuários                   |
| `gestor@proyfebrasil.com.br`    | `GESTOR_FROTA` | Operação completa da frota, sem administrar usuários   |
| `consulta@proyfebrasil.com.br`  | `CONSULTA`     | Somente leitura, sem credenciais de fornecedores       |

Esses dados são **fictícios e restritos ao perfil `dev`**: o componente que os cria sequer é
registrado fora dele.

### No perfil `prod`

Uma instalação nova não tem usuário algum. Para criar o primeiro administrador, defina no `.env`
antes da primeira subida:

```env
FLEETOPS_ADMIN_EMAIL=frota@proyfebrasil.com.br
FLEETOPS_ADMIN_SENHA=<senha forte, mínimo 10 caracteres>
FLEETOPS_ADMIN_NOME=Administrador
```

O bootstrap só age quando **não existe nenhum usuário**: ele nunca altera, reativa ou redefine a
senha de quem já está cadastrado. Depois do primeiro acesso, troque a senha pela interface e
**remova essas variáveis do ambiente**.

---

## Rodar os testes e a verificação

O script `scripts/verificar.sh` é o "CI da sua máquina": roda exatamente o que o contrato de
qualidade exige antes de considerar uma funcionalidade pronta.

```bash
./scripts/verificar.sh             # composição + backend + frontend
./scripts/verificar.sh backend     # compilação, Checkstyle, testes, cobertura
./scripts/verificar.sh frontend    # TypeScript estrito, ESLint, build
./scripts/verificar.sh compose     # valida docker-compose.yml nos dois perfis
```

O que cada etapa garante:

- **Backend** — compilação com `-Werror` (nenhum aviso novo passa), Checkstyle sem violações,
  testes unitários e de integração, e cobertura mínima de **80%** nas camadas `domain` e
  `application`.
- **Frontend** — `tsc` em modo estrito (sem `any`), ESLint incluindo regras de tipo, testes de
  componente com Vitest e Testing Library, e build de produção gerado.

O caminho incremental das migrações também é conferido: aplicar V3 sobre um banco que já
tem V1 e V2 é diferente de criar tudo do zero, e só o primeiro reproduz o que acontece em
produção.

Os testes de integração sobem um **PostgreSQL 16 real** via Testcontainers — não um banco em
memória. É isso que valida de fato as migrações Flyway, os índices parciais que dependem de
`deleted_at` e a conferência do mapeamento do Hibernate contra o schema.

Para rodar apenas os testes do backend, sem lint nem cobertura:

```bash
docker run --rm -u "$(id -u):$(id -g)" --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  -v "$PWD/backend":/app -v /var/run/docker.sock:/var/run/docker.sock -w /app \
  -e GRADLE_USER_HOME=/app/.gradle -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  gradle:8.14-jdk21 ./gradlew test
```

---

## Mapa dos módulos

Monólito modular. Cada módulo de domínio é isolado em seu pacote e, dentro dele, dividido em
`domain` (entidades e regras), `application` (casos de uso), `api` (controllers e DTOs) e
`infra` (repositórios e adaptadores). Controllers nunca recebem ou devolvem entidades JPA.

```
backend/src/main/java/br/com/proyfebrasil/fleetops/
  shared/          config, security, exception, auditing, money, pagination   [Fase 0 ✓]
  shared/arquivos/ anexos em storage S3-compatível, com URL pré-assinada       [Fase 2 ✓]
  administracao/   usuários, perfis de acesso e sessões                        [Fase 0 ✓]
  cadastros/       obra, locadora, condutor, veículo, fornecedor, preços       [Fase 1 ✓]
  contratos/       contrato, substituição, troca de condutor, retirada/devolução [Fase 2 ✓]
  conformidade/    teste de fumaça preta (FOR.MA.01)                           [Fase 2 ✓]
  operacao/        KM, abastecimento, serviços, fechamento mensal, fatura      [Fase 3 ✓]
  conformidade/    checklist, avaria, manutenção, multa, plano de viagem       [Fase 4]
  alertas/         central de pendências e jobs agendados                      [Fase 4]
  importacao/      importadores das planilhas legadas                          [Fase 5]

frontend/src/
  app/             roteador, provedores, layout, tema
  components/ui/   design system (Tailwind + Radix)
  features/        um diretório por módulo, espelhando o backend
  lib/             cliente da API tipado, formatação pt-BR, acessibilidade
```

### Auditoria geral após a Fase 3

Auditoria de todas as camadas contra a especificação. Sete achados, todos corrigidos.

**Dois eram graves — o sistema não era operável de verdade:**

- **A Fase 3 era somente leitura na tela.** Cinco `POST` e cinco `PUT` da operação mensal
  não tinham interface: dava para ver a operação, não para operá-la. Um sistema que mostra
  a planilha mas não deixa lançar nada não substitui a planilha. Entraram os formulários de
  quilometragem, abastecimento, serviço, fatura e uso particular, com exclusão lógica.
- **A base estava vazia da operação.** As 13 planilhas por obra têm 141 abas de condutor,
  com blocos mensais de KM, abastecimento, lava-jato e borracharia, e nada disso tinha sido
  extraído. Agora são **400 registros de KM, 1.684 abastecimentos e 197 serviços** em 74
  contratos e 29 meses.

**Três regras estavam entregues pela metade:**

- A RN-23 lista "faturas divergentes" entre o que a central consolida, e ela não as tinha.
  Entraram `FATURA_DIVERGENTE` (crítica quando a locadora cobra a mais) e
  `KM_ACIMA_DA_FRANQUIA`.
- A RN-06 manda sinalizar o excedente "no dashboard"; ele só existia na tela de operação.
- A página do contrato não levava à operação daquele contrato.

**Dois defeitos que a carga dos dados reais revelou**, e que nenhum teste tinha pego:

| Defeito | Correção |
| ------- | -------- |
| `buscarAtivoDoVeiculo` devolvia `Optional`, mas a V4 removeu o índice que garantia unicidade — três placas do acervo estão em dois contratos ativos, e a consulta estourava em `IncorrectResultSizeDataAccessException` | Passou a devolver lista; o serviço escolhe o contrato de retirada mais recente, e o conflito segue na central de pendências |
| O extrator aplicava o marcador de ano da planilha a **todos** os blocos, inclusive aos anteriores a ele — Novembro e Dezembro caíam no ano seguinte, e o hodômetro "voltava" ao ordenar por data | O ano passou a vir das datas de abastecimento do próprio bloco, que são datas reais; o marcador só entra onde não há data |

A segunda correção derrubou as quebras de encadeamento de **135 para 21** (4% das
transições). Uma terceira melhoria — separar as séries de hodômetro onde ele despenca mais
de 5.000 km, atribuindo a série seguinte à placa substituta do cabeçalho — recuperou os 42
casos que eram troca de veículo dentro da aba. As 21 que restam são erros de digitação da
planilha, e a RN-03 deve mesmo recusá-las.

**Duas decisões revistas durante a auditoria:**

- A pendência de KM excedente olhava o "mês anterior" do calendário. Como os lançamentos
  chegam com atraso, o alerta sumiria justamente quando a digitação atrasasse. Passou a
  olhar as **três últimas competências apuradas** — janela em que a tratativa com a
  locadora ainda acontece.
- Abastecimento sem posto era marcado como não conformidade. Isso é registro incompleto,
  não irregularidade; marcá-lo assim poluiria o relatório da RN-04 com falhas de digitação.
  O posto passou a ser obrigatório no lançamento pela tela — e os 1.684 importados entram
  sem posto, porque a planilha registra apenas data e valor, o que a carga relata.

**Falso positivo verificado:** `UsuarioController` e `AutenticacaoController` apareceram sem
`@PreAuthorize` em uma varredura por método. O primeiro tem a anotação na classe e o
segundo está corretamente no `permitAll` — login, refresh e logout não podem exigir sessão.

**Ficou de fora, por ser superfície de API e não código morto:** cinco endpoints sem
consumidor no frontend (`/auth/eu`, `/condutores/cnh-em-alerta`, `/veiculos/por-placa`,
`/tabelas-preco/vigencia`, `/usuarios/{id}`).

### O que a Fase 3 entregou

A operação mensal: quilometragem diária (FOR.FRO.02), abastecimentos, serviços de
lava-jato, borracharia e para-brisas, faturas da locadora, uso particular e o
**fechamento mensal calculado**.

| Regra | Onde vive | Comportamento |
| ----- | --------- | ------------- |
| **RN-03** | `ServicoDeLancamentos.verificarEncadeamento` | O hodômetro não anda para trás. Confere os **dois** vizinhos, não só o anterior |
| **RN-04** | `ServicoDeLancamentos.aplicarConformidade` | Um abastecimento por dia; fora de posto credenciado ou dia autorizado, exige justificativa e entra marcado |
| **RN-05** | `TipoDeServico.isLimitadoPorSemana` | Um lava-jato por semana de calendário, com a mesma mecânica de exceção |
| **RN-06** | `ServicoDeFechamentoMensal.apurar` | KM excedente e custo pela vigência da competência, sinalizado no painel |
| **RN-10** | `UsoParticular` | Teto de 1.000 km, proibição após 20:00, aceite como condição de validade |
| **RN-13** | `FaturaDaLocadora.alterarConferencia` | Divergência é coluna gerada; com divergência, "OK" é proibido |
| **RN-21** | ausência de colunas em `fechamento_mensal` | Ver abaixo |

#### O fechamento mensal não guarda número nenhum

A Seção 3.3 descreve o fechamento com km inicial, km final, km percorrida, consumo total
e número de abastecimentos. **Nenhuma dessas colunas existe no banco.** A tabela
`fechamento_mensal` tem apenas `status`, `conferido_em`, `conferido_por` e `observacoes`.

A RN-21 é explícita — todo valor calculado é derivado, nunca armazenado como fonte de
verdade editável — e a própria Seção 3.3 completa: "gerado automaticamente a partir dos
lançamentos, nunca digitado". O motivo é prático: a nota de um abastecimento chega dias
depois de o mês virar, e o lançamento retroativo é rotina. Com os totais gravados, o
número no banco passaria a divergir da soma dos lançamentos sem que nada acusasse.

Calculando na leitura, o fechamento é sempre a verdade de agora — e uma conferência
anterior a um lançamento novo aparece como desatualizada, o que é informação, em vez de
o total ficar errado, o que não seria.

Quatro decisões mais:

- **A km percorrida sai dos extremos do hodômetro, não da soma dos trechos.** Quando
  falta o registro de um dia, somar os trechos esconderia o buraco e o mês fecharia
  bonito; pelos extremos, os quilômetros que a locadora vai cobrar entram na conta.
- **Sem tabela de preços do ano, o excedente é conhecido mas não precificado.** Estimar
  zero seria afirmar que ele não custa nada; a tela mostra "sem vigência" (RN-14).
- **A semana da RN-05 é a de calendário, de segunda a domingo.** Com janela corrida de
  sete dias, lavar na sexta e na segunda seguinte violaria a regra, embora sejam semanas
  diferentes para quem organiza a rotina da obra.
- **Fatura `PENDENTE` com divergência é estado legítimo.** A RN-13 exige status ≠ "OK"
  com observação; `PENDENTE` já é ≠ OK, e é o estado de quem acabou de lançar a nota — a
  divergência é justamente o que o sistema acaba de revelar. A explicação é cobrada ao
  concluir a tratativa, contestando ou ajustando.

### O que a Fase 2 entregou

O ciclo de vida completo do contrato, em uma tela por contrato: **linha do tempo** que
funde veículos, condutores e eventos em uma coluna só, substituição de veículo, troca de
condutor, retirada e devolução com book fotográfico, e encerramento com as verificações
da RN-17.

| Regra | Onde vive | Comportamento |
| ----- | --------- | ------------- |
| **RN-01** | `ContratoDeLocacao.colocarVeiculo` | Substituição fecha o período anterior na véspera, sem lacuna nem sobreposição |
| **RN-09** | `ServicoDoCicloDeVida` + coluna gerada | Veículo a diesel só conclui retirada com fumaça preta aprovada. O critério da Seção 3.4 é coluna gerada no banco, não campo digitado (RN-21) |
| **RN-12** | `EventoDeContrato.concluir` | Book de 8 ângulos e CRLV. O que falta é devolvido **item a item**, e a tela mostra antes do clique |
| **RN-16** | `ContratoDeLocacao.colocarCondutor` | CNH avaliada na data do vínculo, não hoje |
| **RN-17** | `ServicoDoCicloDeVida.encerrar` | `DEVOLVIDO` exige verificação; com pendência, só `DESMOBILIZADO` |
| **RN-18** | `TrocaCondutor` + `condutorEm` | "Quem dirigia a placa X em 15/03?" respondido pelo histórico |

Quatro decisões merecem registro:

- **Troca de condutor é período, não evento.** A Seção 3.2 descreve `TrocaCondutor` "com
  data e condutor anterior/novo". A tabela guarda um período por condutor — anterior e
  novo saem dos períodos vizinhos. O motivo é a RN-18: contra períodos, "quem dirigia em
  15/03?" é uma busca por intervalo; contra eventos, seria preciso ler tudo antes da data
  e dobrar em ordem, com risco de responder errado em silêncio se um evento faltasse.
- **A RN-09 é verificada antes da RN-12.** Um veículo a diesel reprovado vai ser trocado,
  e com ele o book inteiro. Pedir as oito fotos antes de dizer que o carro não serve faria
  o usuário fotografar duas vezes.
- **`AVARIAS` não entra na conta do book.** Exigi-lo tornaria impossível concluir a
  retirada de um carro sem avaria — o caso comum e o desejável. É o único ângulo que
  aceita várias fotos, uma por avaria.
- **O binário nunca passa pelo Postgres.** Anexos vão para o MinIO com chave
  `<ano>/<mês>/<uuid>-<nome>`, e a leitura sai por URL pré-assinada de vida curta. O
  `sha256` do conteúdo é gravado porque um book fotográfico é prova documental em uma
  discussão de avaria com a locadora.

#### Uma condição da RN-17 ainda não tem o que verificar

A regra exige quatro coisas antes de devolver à locadora. Três já valem: o evento de
devolução concluído, o **fechamento mensal do período final** — ligado quando a Fase 3
entregou a conferência de competência — e a data de encerramento. A quarta, **avarias
abertas**, depende do módulo de conformidade da Fase 4 e continua no código como consulta
isolada e marcada, devolvendo zero.

Deixá-la de fora daria a impressão de que a RN-17 está completa. Ela não está, e a tela de
encerramento mostra as quatro condições, não três.

### O que a Fase 1 entregou

Todas as seis telas de cadastro seguem o mesmo padrão de listagem exigido pela Seção 6.1:
ordenação e paginação **no servidor**, densidade ajustável, colunas configuráveis,
exportação CSV da visão filtrada e painel lateral de detalhe ao clicar na linha. A
exceção é a tela de tabelas de preço, que exibe cada vigência como uma grade expansível
— uma vigência é um documento com dezenas de células, não uma linha de lista, e forçá-la
em uma tabela de dados pioraria a leitura.


| Cadastro | Destaques |
| -------- | --------- |
| **Obras** | Código único, período, situação; é a dimensão de apuração de custos |
| **Locadoras** | Sete canais de atendimento e credenciais de portal cifradas (RN-20) |
| **Condutores** | CPF com dígito verificador, CNH com alerta de 60/30 dias e bloqueio por vencimento (RN-16) |
| **Veículos** | Placa normalizada e única nos formatos Mercosul e antigo (RN-02); diesel marcado para teste de fumaça preta (RN-09) |
| **Fornecedores** | Sete tipos, cada um com seus campos: dias autorizados do posto (RN-04), frequência do lava-jato (RN-05), custos e portal do rastreador, tamanhos da gráfica |
| **Tabelas de preço** | Uma vigência por locadora e ano (RN-14), grupos tarifários, pacotes de KM como linhas e valor de KM excedente por categoria (RN-06) |

Duas decisões de modelagem merecem registro:

- **Pacotes de KM são linhas, não colunas.** A Unidas trabalha com 3000/4500/5000/6000 e a
  Localiza com 3000/4000/5000; nas planilhas isso obriga a manter duas grades lado a lado.
  Como linhas, qualquer conjunto de pacotes cabe sem alterar o schema.
- **O veículo não conhece obra nem condutor.** Esse vínculo pertence ao contrato (Fase 2),
  porque o mesmo veículo passa por obras e condutores diferentes ao longo do tempo. Colocá-lo
  no veículo destruiria a capacidade de responder "quem dirigia a placa X em 15/03?" (RN-18).

#### Segunda auditoria da Fase 1

Antes de abrir a Fase 2, a Fase 1 foi relida contra a Seção 6.1 procurando o que tinha
passado. Dois pontos apareceram, ambos corrigidos:

- **A barra superior mentia.** O campo de busca estava `disabled` com o rótulo "disponível
  a partir da Fase 1", e o sino dizia "prevista para a Fase 4" — textos escritos na Fase 0
  e nunca revistos, embora a busca fosse requisito da própria Fase 1 e a central de
  pendências já existisse. Ambos agora funcionam: a busca procura por placa, condutor e
  obra (`/` foca o campo, setas navegam, Enter abre o registro) e o sino mostra as cinco
  pendências mais graves, acendendo o marcador só para as críticas.
- **O acervo estava sendo lido pela metade.** As planilhas registram as trocas de veículo
  em colunas repetidas (`DATA 1ª SUBS`/`MODELO`, `DATA 2ª SUBS`/`MODELO2`, …) que o
  extrator ignorava. Eram **108 substituições em 76 contratos** e 97 veículos que nunca
  entraram na base. O efeito não era só histórico faltando: como o veículo atual de um
  contrato é o *último* período, e não a coluna `PLACA`, **8 dos 49 cards do painel
  mostravam a placa errada**.

Recuperar esse histórico exigiu quatro correções de fundo, cada uma na camada certa:

| Sintoma | Causa | Correção |
| ------- | ----- | -------- |
| Índice de período aberto violado | O Hibernate emite o INSERT do período novo antes do UPDATE que fecha o anterior | `EXCLUDE USING gist` com `daterange`, `DEFERRABLE` (V4) — também pega sobreposição entre períodos fechados, que o índice antigo deixava passar |
| Índice de veículo em contrato ativo violado | Restrição minha, além do texto: a RN-01 fala do contrato, não do veículo — e cinco veículos realmente aparecem em dois contratos no acervo | Índice removido; o conflito virou pendência `VEICULO_EM_DOIS_CONTRATOS` (RN-23) |
| `UnexpectedRollbackException` | Capturar exceção vinda de método `@Transactional` marca a transação como somente-rollback; o `catch` não desfaz isso | As trocas passaram a ir junto da abertura, que valida antes de aplicar e devolve as recusadas |
| Sobreposição no commit | O contrato era encerrado **antes** de receber as trocas, e a troca seguinte abria período por cima do intervalo já fechado | A abertura monta a linha do tempo inteira e só então encerra |

Das 108 substituições, **101 entraram** e 7 foram recusadas com o motivo registrado — o
embrião do relatório de rejeições da RN-24. Duas por data posterior ao encerramento do
contrato; cinco por trazerem placa diferente na mesma data da retirada, um caso ambíguo
do acervo que precisa de conferência humana (ver *Pontos em aberto*).

### O que a Fase 0 entregou

| Área              | Entrega                                                                              |
| ----------------- | ------------------------------------------------------------------------------------ |
| Segurança         | JWT stateless, refresh token com rotação e revogação, perfis `ADMIN`/`GESTOR_FROTA`/`CONSULTA` (RN-19) |
| Criptografia      | AES-256-GCM para credenciais de portais, com mascaramento na UI (base da RN-20)       |
| Auditoria         | `created_at/by`, `updated_at/by`, exclusão lógica e trilha do Hibernate Envers com autor |
| Contrato de erros | RFC 7807 em toda falha, com código de negócio estável e `requestId` correlacionado     |
| Banco             | PostgreSQL 16, Flyway, `ddl-auto=validate`, unicidade parcial respeitando soft delete  |
| Observabilidade   | Actuator (health, metrics, prometheus) e logs JSON estruturados (ECS) no perfil `prod` |
| Frontend          | Design system próprio, tema claro/escuro, layout com sidebar colapsável, login completo |

---

## Criar uma migração

Migrações Flyway são a **única** forma de evoluir o schema. O Hibernate roda com
`ddl-auto=validate` e recusa subir se o mapeamento divergir do banco.

1. Crie o arquivo em `backend/src/main/resources/db/migration/` seguindo a numeração:

   ```
   V2__cadastro_de_obras_e_locadoras.sql
   ```

2. Escreva o DDL comentado, respeitando as convenções da Seção 7 da especificação:
   `snake_case`, PK `bigint generated always as identity`, enums como `VARCHAR + CHECK`,
   `unique` parcial com `WHERE deleted_at IS NULL`, índice em toda FK, `timestamptz` para
   instantes e `NUMERIC(12,2)` para dinheiro.

3. Verifique que ela aplica do zero **e** sobre um banco existente:

   ```bash
   docker compose --profile dev up -d          # sobre o banco atual
   docker compose --profile dev down -v        # zera o volume
   docker compose --profile dev up -d          # aplica do zero
   ```

Entidades auditadas pelo Envers exigem também a tabela `<tabela>_aud` correspondente na mesma
migração — ver `V1__estrutura_inicial.sql` como referência.

---

## Regenerar o cliente da API

Os tipos TypeScript são **gerados** a partir da OpenAPI. Duplicar tipos à mão é proibido.

```bash
docker compose --profile dev up -d backend-dev
cd frontend && npm run api:types
```

O arquivo `src/lib/api/schema.d.ts` é versionado de propósito: sem ele, compilar o frontend
exigiria um backend no ar.

Ao adicionar um endpoint, marque na OpenAPI o que nunca é nulo:

```java
@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome
```

Sem isso o springdoc gera tudo como opcional e o frontend precisa checar `null` em campos que
sempre existem — um contrato impreciso contamina todas as telas.

---

## Decisões de arquitetura

Decisões tomadas na Fase 0 que valem para todas as fases seguintes.

### Onde ficam os tokens

**O access token vive apenas em memória** (uma variável de módulo no cliente da API); o refresh
token trafega em cookie `HttpOnly`, `Secure`, `SameSite=Strict`, restrito ao caminho
`/api/v1/auth`. Nada de sessão vai para `localStorage`.

*Por quê:* `localStorage` é legível por qualquer script da página, então um XSS vira roubo de
sessão. Com o token em memória e o refresh em cookie `HttpOnly`, nenhum dos dois é acessível a
JavaScript injetado.

*Contrapartidas assumidas:*

- recarregar a página descarta o access token — a sessão é reconstruída por uma chamada a
  `/auth/refresh` na inicialização do app;
- exige **mesma origem** entre frontend e API, garantida pelo proxy do Nginx em produção e pelo
  proxy do Vite em desenvolvimento;
- a proteção CSRF do Spring fica desativada porque `SameSite=Strict` somado ao `Path` restrito
  já impede que uma requisição originada de outro site carregue o cookie — e nenhuma outra rota
  o aceita.

### Rotação de refresh token

Cada renovação revoga o token usado e emite um novo. Reapresentar um token já rotacionado é
tratado como indício de vazamento: **todas** as sessões do usuário são encerradas.

Essa revogação em massa roda em transação própria (`REQUIRES_NEW`) — na mesma transação, o
rollback da exceção que devolve o 401 a desfaria, e o atacante seguiria com as demais sessões
abertas.

### Carga inicial do acervo, e por que ela não é um importador

O sistema existe para eliminar as planilhas internas — o controle geral e os controles por
obra. Levar o histórico delas para dentro do sistema é uma **migração feita uma vez**, não
um recurso permanente, e por isso não ganhou tela de upload: uma porta para carregar
planilha é uma porta para o hábito voltar.

O caminho é uma chave de configuração:

```
FLEETOPS_CARGA_INICIAL=true    # primeira subida da instalação
                               # confira o relatório no log e desligue
```

Duas travas independentes evitam estrago: a chave vem desligada por padrão (o perfil `dev`
a liga), e cada carregador verifica antes se as tabelas que ele preenche estão vazias —
ligar a chave com o banco povoado não faz nada, como o log confirma.

Os dados carregados **não são simulados**: são os 35 obras, 367 veículos, 270 contratos,
400 registros de KM e 1.684 abastecimentos que existem nas planilhas da empresa. É por isso
que esta carga pode rodar em produção sem contrariar a Seção 11 — o que ela proíbe é
inventar dados. O seed de **usuários**, com senha conhecida, continua restrito ao perfil
`dev`, porque aquele sim é dado inventado.

A carga entra pelos serviços de aplicação, e não por SQL: cada linha passa pelas mesmas
validações da API, e o que é recusado aparece no log com o motivo — a mesma ideia do
relatório de rejeições da RN-24.

#### O importador da RN-24 continua na Fase 5, para outras planilhas

A RN-24 nomeia "multas, tabelas de preço" entre o que os importadores devem ler. Essas
planilhas chegam **de fora**, das locadoras: a Proyfe não controla o formato nem pode
decidir parar de recebê-las. Elas continuarão existindo depois que os controles internos
forem aposentados, e é para elas que um importador se justifica de forma durável.

### Seed de dados fora do Flyway

O Flyway cuida do **schema**; dados fictícios de desenvolvimento são criados por um componente
`@Profile("dev")` idempotente.

*Por quê:* uma migração de dados fictícios faria o histórico do Flyway em desenvolvimento
divergir do de produção, quebrando a validação de migrações.

### Módulo `administracao`

A especificação lista os módulos de domínio mas não um para usuários, embora exija a tabela na
migração V1 e a matriz de permissões da RN-19. Usuários, perfis e sessões ficam em
`administracao`, correspondendo à área "Administração" da navegação.

### Nomes de coluna de auditoria em inglês

O domínio inteiro é em português, mas as colunas de auditoria seguem literalmente a
especificação: `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`. Os atributos
Java permanecem em português (`criadoEm`, `criadoPor`…), com mapeamento explícito.

### Exportação em CSV, não em XLSX

A Seção 6.1 pede "exportação CSV/XLSX da visão filtrada". A Fase 1 entrega **CSV**, com
separador `;` e BOM UTF-8 — o que faz o Excel em português abrir o arquivo com colunas
separadas e acentos corretos, sem conversão manual.

XLSX exigiria uma biblioteca fora da stack fixa (SheetJS ou equivalente), e a Seção 11
proíbe introduzir dependências sem aprovação. A Fase 5 lista "exportações XLSX" como
entrega própria: é lá que a decisão sobre a biblioteca deve ser tomada, junto dos
relatórios que realmente precisam de formatação de planilha.

### Biblioteca de testes do frontend

**Vitest + Testing Library.** A stack fixa não previa uma biblioteca de testes para o
frontend; esta foi acrescentada na Fase 1, quando entraram os primeiros formulários com
validação zod e regras de exibição por perfil. Os testes verificam comportamento observável
— o que aparece na tela, o que é oferecido a cada perfil, quando a API é chamada — e não
detalhes de implementação.

### Prefixo `use` nos hooks do React

Hooks se chamam `useAutenticacao`, `useTema` — não `usarAutenticacao`. O prefixo `use` é contrato
do React, verificado pelo compilador e pelo `eslint-plugin-react-hooks`; não é vocabulário de
domínio. Todo o resto do código, incluindo nomes de componentes e variáveis, segue em português.

### Fonte tipográfica

A interface usa **Inter**, carregada do Google Fonts, com queda para a pilha de fontes do sistema
caso o acesso seja bloqueado. Em uma rede corporativa sem saída para a internet, o layout se
mantém íntegro com a fonte do sistema. Se a preferência for eliminar a dependência externa,
basta hospedar os arquivos da Inter em `public/` e trocar o `@font-face`.

---

## Pontos em aberto

Perguntas que o acervo levanta e que o texto da especificação não responde. Nenhuma foi
resolvida por conta própria — a Seção 12 pede parar e perguntar, e é o que está aqui.

**Substituição na mesma data da retirada.** Cinco contratos (104, 123, 183, 193 e 203)
registram uma troca de veículo na mesma data da retirada, com placa diferente da coluna
`PLACA`. As duas leituras possíveis levam a dados diferentes:

- a coluna `DATA RETIRADA` foi atualizada junto com a troca, e a placa da substituição é a
  correta desde o início — nesse caso o veículo original nunca esteve no contrato;
- houve de fato uma troca no dia da retirada, e o contrato teve dois veículos em um dia.

Enquanto não houver resposta, essas trocas são **recusadas e registradas em log**, e o
contrato fica com o veículo da coluna `PLACA`. É a leitura conservadora: preserva o que
está escrito na coluna principal e não inventa um período de um dia.

**Veículo em dois contratos ao mesmo tempo.** Três veículos aparecem com períodos
sobrepostos em contratos distintos (7/12, 153/244, 227/261). Alguns podem ser o dia da
transferência contado nas duas pontas, outros conflito real. Aparecem na central de
pendências para conferência, sem bloqueio de cadastro.

## Fases de entrega

| Fase  | Escopo                                                                     | Estado         |
| ----- | -------------------------------------------------------------------------- | -------------- |
| **0** | Fundação: monorepo, Compose, segurança, auditoria, OpenAPI, design system   | **Concluída**  |
| **1** | Cadastros: obras, locadoras, condutores, veículos, fornecedores, preços     | **Concluída**  |
| **2** | Ciclo de vida do contrato: retirada, substituições, trocas, desmobilização  | **Concluída**  |
| **3** | Operação mensal: KM, abastecimentos, serviços, fechamento, faturas          | **Concluída**  |
| 4     | Conformidade e alertas: checklist, avarias, manutenções, multas, pendências | —              |
| 5     | Migração de legado e relatórios: importadores, exportações, custos          | —              |

Os itens de navegação das fases futuras aparecem na barra lateral **desabilitados e marcados com
a fase prevista** — o gestor enxerga o mapa completo do produto sem que áreas não entregues
pareçam funcionais.

---

## Referências do domínio

A pasta `arquivos/` guarda as planilhas e formulários que o sistema substitui. Eles são a fonte
de verdade sobre o domínio e a base dos importadores da Fase 5:

- `FOR.FRO.06_Controle geral de veículos.xlsx` — controle geral e uso particular
- `Controles por Obra - KM, Lava-jato, Borracharia/` — um arquivo por obra
- `Controle de Avarias.xlsx` — avarias, análise de checklist e manutenções
- `Multas Locadoras.xlsx` — multas por locadora
- `Controle Geral - Fornecedores Frotas.xlsx` — fornecedores credenciados por tipo
- `Valores Locação Mensal_Unidas e Localiza.xlsx` — tabelas de preço por vigência
- `Formulários/` — FOR.FRO.01 (checklist de 32 itens), FOR.FRO.02 (controle de KM),
  FOR.FRO.05 (plano de viagem), FOR.MA.01 (teste de fumaça preta) e os textos de
  retirada e devolução
