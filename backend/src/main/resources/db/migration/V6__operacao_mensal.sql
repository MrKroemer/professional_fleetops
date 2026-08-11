-- =============================================================================
-- V6 — Operação mensal (Fase 3).
--
-- Os lançamentos do dia a dia: quilometragem, abastecimentos, serviços, faturas
-- e uso particular. É o que hoje vive nos arquivos de "Controles por Obra".
--
-- A decisão estrutural desta migração é o que ela NÃO cria.
--
-- NÃO EXISTE TABELA DE FECHAMENTO MENSAL COM NÚMEROS.
--
-- A Seção 3.3 descreve o "Fechamento mensal por veículo" com km inicial, km
-- final, km percorrida, consumo total e número de abastecimentos. Nenhuma dessas
-- colunas existe aqui, porque a RN-21 é explícita: todo valor calculado é
-- derivado, recalculado a partir dos lançamentos, nunca armazenado como fonte de
-- verdade editável. E a própria Seção 3.3 completa: "Gerado automaticamente a
-- partir dos lançamentos — nunca digitado".
--
-- Guardar os totais criaria uma segunda verdade. Um abastecimento lançado com
-- atraso — o caso comum, porque a nota chega dias depois — deixaria o total
-- gravado divergindo da soma real, e a divergência seria invisível. Os números
-- são calculados na leitura, sempre a partir dos lançamentos.
--
-- O que a tabela `fechamento_mensal` guarda é só o que não se deriva: se o gestor
-- já conferiu aquela competência, quando, e o que anotou. Isso é decisão humana,
-- não cálculo.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- registro_km — digitalização do FOR.FRO.02 (RN-03).
-- -----------------------------------------------------------------------------
CREATE TABLE registro_km (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id bigint       NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    condutor_id bigint       REFERENCES condutor (id),
    data        date         NOT NULL,
    km_inicial  integer      NOT NULL,
    km_final    integer      NOT NULL,
    origem      varchar(180),
    destino     varchar(180),
    observacao  text,
    created_at  timestamptz  NOT NULL,
    created_by  varchar(180) NOT NULL,
    updated_at  timestamptz  NOT NULL,
    updated_by  varchar(180) NOT NULL,
    deleted_at  timestamptz,
    CONSTRAINT ck_registro_km_nao_negativo CHECK (km_inicial >= 0 AND km_final >= 0),
    -- Metade da RN-03 no banco: o hodômetro não anda para trás dentro de um registro.
    -- A outra metade — o encadeamento com o registro anterior — depende de consulta e
    -- vive no domínio, onde a mensagem de erro pode dizer qual registro conflita.
    CONSTRAINT ck_registro_km_ordem CHECK (km_final >= km_inicial)
);

COMMENT ON TABLE registro_km IS
    'Registro diário de quilometragem por contrato (FOR.FRO.02, RN-03).';

CREATE INDEX ix_registro_km_contrato ON registro_km (contrato_id, data);
CREATE INDEX ix_registro_km_condutor ON registro_km (condutor_id);

-- -----------------------------------------------------------------------------
-- abastecimento — RN-04.
--
-- `nao_conforme` não é um defeito do registro: é a forma que a RN-04 dá para
-- lançar o que aconteceu fora da regra. O abastecimento em posto não credenciado
-- ou em dia não autorizado é um fato, e recusá-lo faria o gestor deixar de
-- registrar o gasto — perdendo o custo e a não conformidade de uma vez.
-- -----------------------------------------------------------------------------
CREATE TABLE abastecimento (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id    bigint         NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    posto_id       bigint         REFERENCES fornecedor (id),
    data           date           NOT NULL,
    valor          numeric(12,2)  NOT NULL,
    litros         numeric(10,3),
    km             integer,
    nao_conforme   boolean        NOT NULL DEFAULT false,
    justificativa  text,
    observacao     text,
    created_at     timestamptz  NOT NULL,
    created_by     varchar(180) NOT NULL,
    updated_at     timestamptz  NOT NULL,
    updated_by     varchar(180) NOT NULL,
    deleted_at     timestamptz,
    CONSTRAINT ck_abastecimento_valor  CHECK (valor >= 0),
    CONSTRAINT ck_abastecimento_litros CHECK (litros IS NULL OR litros > 0),
    CONSTRAINT ck_abastecimento_km     CHECK (km IS NULL OR km >= 0),
    -- Uma não conformidade sem justificativa é só um dado faltando. A RN-04 exige
    -- a justificativa explícita, e o banco é o único lugar que nenhuma carga contorna.
    CONSTRAINT ck_abastecimento_justificativa
        CHECK (NOT nao_conforme OR (justificativa IS NOT NULL AND length(trim(justificativa)) > 0))
);

COMMENT ON COLUMN abastecimento.nao_conforme IS
    'RN-04: lançado fora de posto credenciado ou de dia autorizado, com justificativa.';

CREATE INDEX ix_abastecimento_contrato ON abastecimento (contrato_id, data);
CREATE INDEX ix_abastecimento_posto ON abastecimento (posto_id);

-- Máximo de um abastecimento por contrato por dia (RN-04). Índice parcial porque o
-- soft delete precisa liberar a data de um lançamento excluído por engano.
CREATE UNIQUE INDEX ux_abastecimento_por_dia
    ON abastecimento (contrato_id, data) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- servico_operacional — lava-jato, borracharia e para-brisas (RN-05).
--
-- Uma tabela para os três, e não três tabelas: os campos são idênticos — data,
-- fornecedor, valor, descrição — e só a regra de frequência difere. Separá-las
-- triplicaria consulta, DTO e tela para distinguir um enum.
-- -----------------------------------------------------------------------------
CREATE TABLE servico_operacional (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id   bigint        NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    fornecedor_id bigint        REFERENCES fornecedor (id),
    tipo          varchar(20)   NOT NULL,
    data          date          NOT NULL,
    valor         numeric(12,2) NOT NULL,
    descricao     varchar(300),
    nao_conforme  boolean       NOT NULL DEFAULT false,
    justificativa text,
    created_at    timestamptz  NOT NULL,
    created_by    varchar(180) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    updated_by    varchar(180) NOT NULL,
    deleted_at    timestamptz,
    CONSTRAINT ck_servico_tipo  CHECK (tipo IN ('LAVA_JATO', 'BORRACHARIA', 'PARA_BRISAS')),
    CONSTRAINT ck_servico_valor CHECK (valor >= 0),
    CONSTRAINT ck_servico_justificativa
        CHECK (NOT nao_conforme OR (justificativa IS NOT NULL AND length(trim(justificativa)) > 0))
);

COMMENT ON TABLE servico_operacional IS
    'Lava-jato, borracharia e para-brisas. A frequência semanal da RN-05 vale só para lava-jato.';

CREATE INDEX ix_servico_contrato ON servico_operacional (contrato_id, data);
CREATE INDEX ix_servico_fornecedor ON servico_operacional (fornecedor_id);
CREATE INDEX ix_servico_tipo ON servico_operacional (tipo, data) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- fatura_locadora — RN-13.
--
-- `divergencia` é coluna gerada. A RN-13 define a fórmula, e a RN-21 manda derivá-la:
-- gravada como campo comum, uma correção no valor faturado deixaria a divergência
-- antiga no lugar, e a conferência passaria a mentir.
-- -----------------------------------------------------------------------------
CREATE TABLE fatura_locadora (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id       bigint        NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    competencia       date          NOT NULL,
    valor_contratado  numeric(12,2) NOT NULL DEFAULT 0,
    valor_faturado    numeric(12,2) NOT NULL DEFAULT 0,
    extras_aprovados  numeric(12,2) NOT NULL DEFAULT 0,
    status_conferencia varchar(20)  NOT NULL DEFAULT 'PENDENTE',
    observacoes       text,
    numero_da_nota    varchar(60),
    vencimento        date,
    divergencia       numeric(12,2) GENERATED ALWAYS AS
        (valor_faturado - (valor_contratado + extras_aprovados)) STORED,
    created_at        timestamptz  NOT NULL,
    created_by        varchar(180) NOT NULL,
    updated_at        timestamptz  NOT NULL,
    updated_by        varchar(180) NOT NULL,
    deleted_at        timestamptz,
    CONSTRAINT ck_fatura_status CHECK (status_conferencia IN
        ('PENDENTE', 'OK', 'EM_CONTESTACAO', 'AJUSTADA')),
    CONSTRAINT ck_fatura_valores CHECK (
        valor_contratado >= 0 AND valor_faturado >= 0 AND extras_aprovados >= 0),
    -- A competência é sempre o primeiro dia do mês: guardá-la como data solta
    -- permitiria duas linhas para o mesmo mês, uma no dia 1 e outra no dia 15.
    CONSTRAINT ck_fatura_competencia CHECK (extract(day from competencia) = 1)
);

COMMENT ON COLUMN fatura_locadora.divergencia IS
    'RN-13, derivada: valor_faturado − (valor_contratado + extras aprovados).';

CREATE INDEX ix_fatura_contrato ON fatura_locadora (contrato_id, competencia);
CREATE UNIQUE INDEX ux_fatura_competencia
    ON fatura_locadora (contrato_id, competencia) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- uso_particular — RN-10.
-- -----------------------------------------------------------------------------
CREATE TABLE uso_particular (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id   bigint       NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    condutor_id   bigint       NOT NULL REFERENCES condutor (id),
    tipo          varchar(20)  NOT NULL,
    inicio        date         NOT NULL,
    fim           date         NOT NULL,
    km_autorizado integer      NOT NULL DEFAULT 1000,
    km_percorrido integer,
    aceite_em     timestamptz,
    observacoes   text,
    created_at    timestamptz  NOT NULL,
    created_by    varchar(180) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    updated_by    varchar(180) NOT NULL,
    deleted_at    timestamptz,
    CONSTRAINT ck_uso_tipo    CHECK (tipo IN ('FOLGA_RECORRENTE', 'USO_PONTUAL')),
    CONSTRAINT ck_uso_periodo CHECK (fim >= inicio),
    -- O teto de 1.000 km é da RN-10. Autorizar mais exigiria mudar a regra, não o dado.
    CONSTRAINT ck_uso_km      CHECK (km_autorizado > 0 AND km_autorizado <= 1000),
    CONSTRAINT ck_uso_km_percorrido CHECK (km_percorrido IS NULL OR km_percorrido >= 0)
);

COMMENT ON TABLE uso_particular IS
    'Autorização de uso particular: teto de 1.000 km e proibição de condução após 20:00 (RN-10).';
COMMENT ON COLUMN uso_particular.aceite_em IS
    'Momento em que o condutor aceitou as regras. Sem aceite, a autorização não vale.';

CREATE INDEX ix_uso_contrato ON uso_particular (contrato_id, inicio);
CREATE INDEX ix_uso_condutor ON uso_particular (condutor_id);

-- Dois usos particulares sobrepostos no mesmo contrato seriam duas autorizações
-- simultâneas para o mesmo carro — e dois tetos de 1.000 km valendo ao mesmo tempo.
ALTER TABLE uso_particular
    ADD CONSTRAINT ex_uso_particular_sem_sobreposicao
    EXCLUDE USING gist (
        contrato_id WITH =,
        daterange(inicio, fim, '[]') WITH &&
    )
    DEFERRABLE INITIALLY DEFERRED;

-- -----------------------------------------------------------------------------
-- fechamento_mensal — só a decisão humana, nunca os números.
--
-- Ver o cabeçalho desta migração: km percorrida, consumo e contagens são
-- calculados a partir dos lançamentos, na leitura. Aqui fica o que não se deriva —
-- se o gestor conferiu a competência e o que anotou ao conferir.
-- -----------------------------------------------------------------------------
CREATE TABLE fechamento_mensal (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id  bigint       NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    competencia  date         NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'ABERTO',
    conferido_em timestamptz,
    conferido_por varchar(180),
    observacoes  text,
    created_at   timestamptz  NOT NULL,
    created_by   varchar(180) NOT NULL,
    updated_at   timestamptz  NOT NULL,
    updated_by   varchar(180) NOT NULL,
    deleted_at   timestamptz,
    CONSTRAINT ck_fechamento_status CHECK (status IN ('ABERTO', 'CONFERIDO')),
    CONSTRAINT ck_fechamento_competencia CHECK (extract(day from competencia) = 1),
    CONSTRAINT ck_fechamento_conferencia
        CHECK ((status = 'CONFERIDO') = (conferido_em IS NOT NULL))
);

COMMENT ON TABLE fechamento_mensal IS
    'Conferência de uma competência. Os totais NÃO ficam aqui: são derivados dos lançamentos (RN-21).';

CREATE INDEX ix_fechamento_contrato ON fechamento_mensal (contrato_id, competencia);
CREATE UNIQUE INDEX ux_fechamento_competencia
    ON fechamento_mensal (contrato_id, competencia) WHERE deleted_at IS NULL;

-- =============================================================================
-- Trilha do Envers — lançamentos financeiros e a conferência.
--
-- Entram abastecimento, serviço e fatura, que movimentam dinheiro, e o registro de
-- KM, que sustenta o cálculo do excedente. Quem alterou o valor de uma nota ou o
-- hodômetro de um dia precisa ficar gravado.
-- =============================================================================

CREATE TABLE registro_km_aud (
    id bigint NOT NULL, rev bigint NOT NULL REFERENCES revisao_auditoria (id), revtype smallint NOT NULL,
    contrato_id bigint, condutor_id bigint, data date, km_inicial integer, km_final integer,
    origem varchar(180), destino varchar(180), observacao text,
    created_at timestamptz, created_by varchar(180), updated_at timestamptz,
    updated_by varchar(180), deleted_at timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_registro_km_aud_rev ON registro_km_aud (rev);

CREATE TABLE abastecimento_aud (
    id bigint NOT NULL, rev bigint NOT NULL REFERENCES revisao_auditoria (id), revtype smallint NOT NULL,
    contrato_id bigint, posto_id bigint, data date, valor numeric(12,2), litros numeric(10,3),
    km integer, nao_conforme boolean, justificativa text, observacao text,
    created_at timestamptz, created_by varchar(180), updated_at timestamptz,
    updated_by varchar(180), deleted_at timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_abastecimento_aud_rev ON abastecimento_aud (rev);

CREATE TABLE servico_operacional_aud (
    id bigint NOT NULL, rev bigint NOT NULL REFERENCES revisao_auditoria (id), revtype smallint NOT NULL,
    contrato_id bigint, fornecedor_id bigint, tipo varchar(20), data date, valor numeric(12,2),
    descricao varchar(300), nao_conforme boolean, justificativa text,
    created_at timestamptz, created_by varchar(180), updated_at timestamptz,
    updated_by varchar(180), deleted_at timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_servico_operacional_aud_rev ON servico_operacional_aud (rev);

CREATE TABLE fatura_locadora_aud (
    id bigint NOT NULL, rev bigint NOT NULL REFERENCES revisao_auditoria (id), revtype smallint NOT NULL,
    contrato_id bigint, competencia date, valor_contratado numeric(12,2), valor_faturado numeric(12,2),
    extras_aprovados numeric(12,2), status_conferencia varchar(20), observacoes text,
    numero_da_nota varchar(60), vencimento date,
    created_at timestamptz, created_by varchar(180), updated_at timestamptz,
    updated_by varchar(180), deleted_at timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_fatura_locadora_aud_rev ON fatura_locadora_aud (rev);

CREATE TABLE fechamento_mensal_aud (
    id bigint NOT NULL, rev bigint NOT NULL REFERENCES revisao_auditoria (id), revtype smallint NOT NULL,
    contrato_id bigint, competencia date, status varchar(20), conferido_em timestamptz,
    conferido_por varchar(180), observacoes text,
    created_at timestamptz, created_by varchar(180), updated_at timestamptz,
    updated_by varchar(180), deleted_at timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_fechamento_mensal_aud_rev ON fechamento_mensal_aud (rev);

CREATE TABLE uso_particular_aud (
    id bigint NOT NULL, rev bigint NOT NULL REFERENCES revisao_auditoria (id), revtype smallint NOT NULL,
    contrato_id bigint, condutor_id bigint, tipo varchar(20), inicio date, fim date,
    km_autorizado integer, km_percorrido integer, aceite_em timestamptz, observacoes text,
    created_at timestamptz, created_by varchar(180), updated_at timestamptz,
    updated_by varchar(180), deleted_at timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_uso_particular_aud_rev ON uso_particular_aud (rev);
