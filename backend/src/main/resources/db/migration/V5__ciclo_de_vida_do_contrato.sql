-- =============================================================================
-- V5 — Ciclo de vida do contrato (Fase 2).
--
-- Completa o agregado iniciado na V3 com o que faltava da Seção 3.2: trocas de
-- condutor, os eventos de retirada e devolução com book fotográfico e CRLV, o
-- armazenamento de anexos e o teste de fumaça preta que a RN-09 exige na retirada.
--
-- Duas decisões de modelagem valem registro antes do schema.
--
-- 1. TROCA DE CONDUTOR COMO PERÍODO, NÃO COMO EVENTO
--
--    A Seção 3.2 descreve `TrocaCondutor` "com data e condutor anterior/novo". A
--    tabela abaixo guarda, em vez disso, um período por condutor — o mesmo desenho
--    de `substituicao_veiculo`. Anterior e novo continuam disponíveis: são os
--    condutores dos períodos adjacentes, e a API os expõe assim.
--
--    O motivo é a RN-18. "Quem dirigia em 15/03?" contra períodos é uma busca por
--    intervalo, que um índice resolve. Contra eventos, é preciso ler todos os
--    eventos anteriores à data e dobrá-los em ordem — mais caro, e sujeito a
--    responder errado se um evento faltar. Com períodos, um buraco no histórico é
--    visível no próprio dado, e a restrição de exclusão impede sobreposição.
--
-- 2. O BOOK FOTOGRÁFICO É UMA TABELA, NÃO UMA COLUNA POR ÂNGULO
--
--    Pelo mesmo motivo que as substituições não são colunas repetidas: a lista de
--    ângulos obrigatórios muda com o tempo e com a locadora. Como linhas, mudar a
--    exigência é mudar dado, não schema.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- anexo — metadados dos arquivos guardados no MinIO (Seção 5, item 4).
--
-- O binário nunca entra no Postgres: a coluna `chave` aponta para o objeto no
-- bucket, e o acesso se dá por URL pré-assinada de vida curta. Guardar o conteúdo
-- aqui incharia backups e transações com dados que não participam de consulta.
--
-- `sha256` existe para reconhecer reenvio do mesmo arquivo e para provar que o
-- objeto no bucket é o que foi registrado — um book fotográfico é prova documental.
-- -----------------------------------------------------------------------------
CREATE TABLE anexo (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chave          varchar(300) NOT NULL,
    nome_original  varchar(260) NOT NULL,
    tipo_conteudo  varchar(120) NOT NULL,
    tamanho_bytes  bigint       NOT NULL,
    sha256         varchar(64)  NOT NULL,
    created_at     timestamptz  NOT NULL,
    created_by     varchar(180) NOT NULL,
    updated_at     timestamptz  NOT NULL,
    updated_by     varchar(180) NOT NULL,
    deleted_at     timestamptz,
    CONSTRAINT ck_anexo_tamanho CHECK (tamanho_bytes > 0)
);

COMMENT ON TABLE  anexo IS
    'Metadados de arquivo; o binário vive no bucket S3-compatível apontado por `chave`.';
COMMENT ON COLUMN anexo.sha256 IS
    'Impressão digital do conteúdo: reconhece reenvio e prova a integridade do documento.';

CREATE UNIQUE INDEX ux_anexo_chave ON anexo (chave);
CREATE INDEX ix_anexo_sha256 ON anexo (sha256);

-- -----------------------------------------------------------------------------
-- troca_condutor — histórico ordenado dos condutores de um contrato (RN-18).
-- -----------------------------------------------------------------------------
CREATE TABLE troca_condutor (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id bigint NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    condutor_id bigint NOT NULL REFERENCES condutor (id),
    inicio      date   NOT NULL,
    fim         date,
    motivo      varchar(300),
    CONSTRAINT ck_troca_condutor_periodo CHECK (fim IS NULL OR fim >= inicio)
);

COMMENT ON TABLE troca_condutor IS
    'Períodos de cada condutor em um contrato. `fim` nulo indica o período em curso.';

CREATE INDEX ix_troca_condutor_contrato ON troca_condutor (contrato_id, inicio);
CREATE INDEX ix_troca_condutor_condutor ON troca_condutor (condutor_id);

-- Mesma garantia dada aos veículos na V4, pelo mesmo motivo: dois condutores no
-- mesmo dia tornariam a pergunta da RN-18 ambígua. Adiada até o commit porque uma
-- troca fecha um período e abre outro na mesma transação, e o Hibernate emite o
-- INSERT antes do UPDATE.
ALTER TABLE troca_condutor
    ADD CONSTRAINT ex_troca_condutor_sem_sobreposicao
    EXCLUDE USING gist (
        contrato_id WITH =,
        daterange(inicio, fim, '[]') WITH &&
    )
    DEFERRABLE INITIALLY DEFERRED;

-- -----------------------------------------------------------------------------
-- evento_de_contrato — retirada e devolução (Seção 3.2, RN-12).
--
-- Um evento nasce EM_PREENCHIMENTO e só passa a CONCLUIDO quando o book está
-- completo e o CRLV anexado. A RN-12 proíbe "conclusão parcial silenciosa": o
-- estado intermediário existe justamente para que o preenchimento possa ser
-- interrompido e retomado sem que o sistema finja que terminou.
--
-- O veículo é registrado no evento, e não lido do contrato: a retirada pode ser de
-- um veículo substituto, e a devolução de um veículo que já não é o atual. Amarrar
-- ao ponteiro do contrato faria o histórico mentir depois da primeira substituição.
-- -----------------------------------------------------------------------------
CREATE TABLE evento_de_contrato (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id       bigint       NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    veiculo_id        bigint       NOT NULL REFERENCES veiculo (id),
    condutor_id       bigint       REFERENCES condutor (id),
    crlv_anexo_id     bigint       REFERENCES anexo (id),
    tipo              varchar(20)  NOT NULL,
    situacao          varchar(20)  NOT NULL DEFAULT 'EM_PREENCHIMENTO',
    data_do_evento    date         NOT NULL,
    km                integer,
    local_do_evento   varchar(180),
    checklist_locadora text,
    regras_aceitas_em timestamptz,
    concluido_em      timestamptz,
    observacoes       text,
    created_at        timestamptz  NOT NULL,
    created_by        varchar(180) NOT NULL,
    updated_at        timestamptz  NOT NULL,
    updated_by        varchar(180) NOT NULL,
    deleted_at        timestamptz,
    CONSTRAINT ck_evento_tipo     CHECK (tipo IN ('RETIRADA', 'DEVOLUCAO')),
    CONSTRAINT ck_evento_situacao CHECK (situacao IN ('EM_PREENCHIMENTO', 'CONCLUIDO')),
    CONSTRAINT ck_evento_km       CHECK (km IS NULL OR km >= 0),
    -- Concluído sem carimbo de conclusão seria um registro sem quando; e o carimbo
    -- sem a situação, uma conclusão que a listagem não enxerga.
    CONSTRAINT ck_evento_conclusao
        CHECK ((situacao = 'CONCLUIDO') = (concluido_em IS NOT NULL))
);

COMMENT ON TABLE  evento_de_contrato IS
    'Retirada ou devolução de um veículo: book fotográfico, CRLV, checklist e aceite de regras.';
COMMENT ON COLUMN evento_de_contrato.veiculo_id IS
    'O veículo do evento, não o atual do contrato — a devolução costuma ser de um substituído.';
COMMENT ON COLUMN evento_de_contrato.regras_aceitas_em IS
    'Confirmação de que as regras de uso foram enviadas e aceitas pelo condutor (Seção 3.2).';

CREATE INDEX ix_evento_contrato ON evento_de_contrato (contrato_id, data_do_evento);
CREATE INDEX ix_evento_veiculo ON evento_de_contrato (veiculo_id);
CREATE INDEX ix_evento_situacao ON evento_de_contrato (situacao) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- foto_do_book — uma linha por ângulo fotografado.
--
-- A lista de ângulos vem da Seção 3.2 (4 lados, pneus, hodômetro, motor,
-- porta-malas, avarias). AVARIAS é o único condicional: só existe quando há avaria
-- a registrar, e por isso não entra na conta do book completo.
-- -----------------------------------------------------------------------------
CREATE TABLE foto_do_book (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id  bigint      NOT NULL REFERENCES evento_de_contrato (id) ON DELETE CASCADE,
    anexo_id   bigint      NOT NULL REFERENCES anexo (id),
    item       varchar(30) NOT NULL,
    observacao varchar(300),
    CONSTRAINT ck_foto_item CHECK (item IN (
        'FRENTE', 'TRASEIRA', 'LATERAL_ESQUERDA', 'LATERAL_DIREITA',
        'PNEUS', 'HODOMETRO', 'MOTOR', 'PORTA_MALAS', 'AVARIAS'))
);

COMMENT ON TABLE foto_do_book IS
    'Book fotográfico do evento. Um ângulo por linha; AVARIAS aceita várias.';

CREATE INDEX ix_foto_evento ON foto_do_book (evento_id);

-- Cada ângulo obrigatório aparece uma vez só — uma segunda foto da frente seria
-- ambiguidade, não redundância útil. AVARIAS fica de fora porque um veículo pode
-- ter várias, e cada uma merece o seu registro.
CREATE UNIQUE INDEX ux_foto_item_unico
    ON foto_do_book (evento_id, item) WHERE item <> 'AVARIAS';

-- -----------------------------------------------------------------------------
-- teste_fumaca_preta — FOR.MA.01, escala de Ringelmann (Seção 3.4, RN-09).
--
-- `conforme` é coluna gerada, não campo digitado: a RN-21 manda todo valor
-- calculado ser derivado. O critério da Seção 3.4 — reprova acima do Padrão 2 até
-- 500 m de altitude, acima do Padrão 3 acima disso — fica expresso aqui, no lugar
-- onde nenhuma carga em lote pode contorná-lo.
-- -----------------------------------------------------------------------------
CREATE TABLE teste_fumaca_preta (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    veiculo_id       bigint      NOT NULL REFERENCES veiculo (id),
    contrato_id      bigint      REFERENCES contrato_locacao (id) ON DELETE SET NULL,
    condutor_id      bigint      REFERENCES condutor (id),
    obra_id          bigint      REFERENCES obra (id),
    data_do_teste    date        NOT NULL,
    padrao_ringelmann smallint   NOT NULL,
    altitude_metros  integer     NOT NULL DEFAULT 0,
    observacoes      text,
    conforme         boolean GENERATED ALWAYS AS (
        CASE WHEN altitude_metros > 500 THEN padrao_ringelmann <= 3
             ELSE padrao_ringelmann <= 2 END
    ) STORED,
    created_at       timestamptz  NOT NULL,
    created_by       varchar(180) NOT NULL,
    updated_at       timestamptz  NOT NULL,
    updated_by       varchar(180) NOT NULL,
    deleted_at       timestamptz,
    CONSTRAINT ck_fumaca_padrao   CHECK (padrao_ringelmann BETWEEN 1 AND 5),
    CONSTRAINT ck_fumaca_altitude CHECK (altitude_metros >= 0)
);

COMMENT ON TABLE  teste_fumaca_preta IS
    'Teste de opacidade na escala de Ringelmann, exigido na retirada de veículo a diesel (RN-09).';
COMMENT ON COLUMN teste_fumaca_preta.conforme IS
    'Derivado (RN-21): reprova acima do Padrão 2 até 500 m, acima do Padrão 3 acima disso.';

CREATE INDEX ix_fumaca_veiculo ON teste_fumaca_preta (veiculo_id, data_do_teste DESC);
CREATE INDEX ix_fumaca_contrato ON teste_fumaca_preta (contrato_id);

-- =============================================================================
-- Trilha do Envers.
--
-- Entram evento e teste, que são registros documentais: quem alterou o km de uma
-- retirada ou o padrão de um teste precisa ficar gravado. Ficam de fora as tabelas
-- que já são histórico por natureza — `troca_condutor` e `foto_do_book` —, porque
-- versioná-las produziria um histórico do histórico sem utilidade, exatamente como
-- a V3 decidiu para `substituicao_veiculo`.
-- =============================================================================

CREATE TABLE evento_de_contrato_aud (
    id                bigint   NOT NULL,
    rev               bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype           smallint NOT NULL,
    contrato_id       bigint,
    veiculo_id        bigint,
    condutor_id       bigint,
    crlv_anexo_id     bigint,
    tipo              varchar(20),
    situacao          varchar(20),
    data_do_evento    date,
    km                integer,
    local_do_evento   varchar(180),
    checklist_locadora text,
    regras_aceitas_em timestamptz,
    concluido_em      timestamptz,
    observacoes       text,
    created_at        timestamptz,
    created_by        varchar(180),
    updated_at        timestamptz,
    updated_by        varchar(180),
    deleted_at        timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_evento_de_contrato_aud_rev ON evento_de_contrato_aud (rev);

CREATE TABLE teste_fumaca_preta_aud (
    id                bigint   NOT NULL,
    rev               bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype           smallint NOT NULL,
    veiculo_id        bigint,
    contrato_id       bigint,
    condutor_id       bigint,
    obra_id           bigint,
    data_do_teste     date,
    padrao_ringelmann smallint,
    altitude_metros   integer,
    observacoes       text,
    created_at        timestamptz,
    created_by        varchar(180),
    updated_at        timestamptz,
    updated_by        varchar(180),
    deleted_at        timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_teste_fumaca_preta_aud_rev ON teste_fumaca_preta_aud (rev);

CREATE TABLE anexo_aud (
    id            bigint   NOT NULL,
    rev           bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype       smallint NOT NULL,
    chave         varchar(300),
    nome_original varchar(260),
    tipo_conteudo varchar(120),
    tamanho_bytes bigint,
    sha256        varchar(64),
    created_at    timestamptz,
    created_by    varchar(180),
    updated_at    timestamptz,
    updated_by    varchar(180),
    deleted_at    timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_anexo_aud_rev ON anexo_aud (rev);
