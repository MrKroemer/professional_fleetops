-- =============================================================================
-- V3 — Contrato de locação de veículo.
--
-- É o agregado central do domínio (Seção 3.2): o vínculo operacional não é o
-- veículo em si, mas a "linha" do controle geral — uma obra, um condutor, um
-- veículo, uma data de retirada e um pacote de KM contratado.
--
-- Esta migração entrega o esqueleto do contrato e o histórico de veículos. O
-- restante da Fase 2 — trocas de condutor, retirada e devolução com book
-- fotográfico, CRLV e teste de fumaça preta — entra nas próximas migrações.
--
-- Sobre `veiculo_atual_id` e `condutor_atual_id`: são ponteiros mantidos, não a
-- fonte de verdade. O histórico vive em `substituicao_veiculo` (e, adiante, em
-- `troca_condutor`), de modo que a RN-18 continue respondível — "quem dirigia a
-- placa X em 15/03?" se responde pelo histórico, não pelo ponteiro. Os ponteiros
-- existem porque toda listagem precisa do estado atual, e derivá-lo por subconsulta
-- em cada linha custaria caro sem nenhum ganho de integridade.
-- =============================================================================

CREATE TABLE contrato_locacao (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    obra_id                 bigint       NOT NULL REFERENCES obra (id),
    locadora_id             bigint       NOT NULL REFERENCES locadora (id),
    veiculo_atual_id        bigint       REFERENCES veiculo (id),
    condutor_atual_id       bigint       REFERENCES condutor (id),
    codigo_interno          varchar(40),
    local_retirada          varchar(180),
    data_retirada           date,
    data_encerramento       date,
    pacote_km_contratado    integer,
    valor_mensal_contratado numeric(12,2),
    status                  varchar(20)  NOT NULL DEFAULT 'ATIVO',
    observacoes             text,
    created_at              timestamptz  NOT NULL,
    created_by              varchar(180) NOT NULL,
    updated_at              timestamptz  NOT NULL,
    updated_by              varchar(180) NOT NULL,
    deleted_at              timestamptz,
    CONSTRAINT ck_contrato_status
        CHECK (status IN ('ATIVO', 'DESMOBILIZADO', 'DEVOLVIDO', 'INATIVO')),
    CONSTRAINT ck_contrato_pacote
        CHECK (pacote_km_contratado IS NULL OR pacote_km_contratado > 0),
    CONSTRAINT ck_contrato_valor
        CHECK (valor_mensal_contratado IS NULL OR valor_mensal_contratado >= 0),
    CONSTRAINT ck_contrato_periodo
        CHECK (data_encerramento IS NULL OR data_retirada IS NULL
               OR data_encerramento >= data_retirada)
);

COMMENT ON TABLE  contrato_locacao IS
    'Uma linha do controle geral de veículos: obra, condutor, veículo e pacote contratado.';
COMMENT ON COLUMN contrato_locacao.veiculo_atual_id IS
    'Ponteiro mantido; o histórico completo está em substituicao_veiculo (RN-18).';
COMMENT ON COLUMN contrato_locacao.pacote_km_contratado IS
    'Franquia mensal. Nulo quando o veículo é do próprio profissional e não tem franquia.';

CREATE INDEX ix_contrato_obra ON contrato_locacao (obra_id);
CREATE INDEX ix_contrato_locadora ON contrato_locacao (locadora_id);
CREATE INDEX ix_contrato_condutor ON contrato_locacao (condutor_atual_id);
CREATE INDEX ix_contrato_status ON contrato_locacao (status) WHERE deleted_at IS NULL;

-- Um veículo participa de no máximo um contrato ativo por vez (RN-01). O índice
-- parcial cobre exatamente isso: contratos encerrados podem repetir a mesma placa.
CREATE UNIQUE INDEX ux_contrato_veiculo_ativo
    ON contrato_locacao (veiculo_atual_id)
    WHERE deleted_at IS NULL AND status = 'ATIVO' AND veiculo_atual_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- substituicao_veiculo — histórico ordenado dos veículos de um contrato.
--
-- A planilha atual registra até seis substituições em colunas repetidas
-- (MODELO2..MODELO6). Aqui cada período é uma linha, sem teto e sem lacuna.
-- -----------------------------------------------------------------------------
CREATE TABLE substituicao_veiculo (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contrato_id  bigint NOT NULL REFERENCES contrato_locacao (id) ON DELETE CASCADE,
    veiculo_id   bigint NOT NULL REFERENCES veiculo (id),
    inicio       date   NOT NULL,
    fim          date,
    motivo       varchar(300),
    CONSTRAINT ck_substituicao_periodo CHECK (fim IS NULL OR fim >= inicio)
);

COMMENT ON TABLE substituicao_veiculo IS
    'Períodos de cada veículo em um contrato. `fim` nulo indica o período em curso.';

CREATE INDEX ix_substituicao_contrato ON substituicao_veiculo (contrato_id, inicio);
CREATE INDEX ix_substituicao_veiculo ON substituicao_veiculo (veiculo_id);

-- Um contrato tem no máximo um período em aberto (RN-01): sem isso, uma
-- substituição malfeita deixaria dois veículos ativos no mesmo contrato.
CREATE UNIQUE INDEX ux_substituicao_periodo_aberto
    ON substituicao_veiculo (contrato_id) WHERE fim IS NULL;

-- =============================================================================
-- Trilha do Envers — o contrato é entidade crítica (Seção 3.5).
-- =============================================================================

CREATE TABLE contrato_locacao_aud (
    id                      bigint   NOT NULL,
    rev                     bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype                 smallint NOT NULL,
    obra_id                 bigint,
    locadora_id             bigint,
    veiculo_atual_id        bigint,
    condutor_atual_id       bigint,
    codigo_interno          varchar(40),
    local_retirada          varchar(180),
    data_retirada           date,
    data_encerramento       date,
    pacote_km_contratado    integer,
    valor_mensal_contratado numeric(12,2),
    status                  varchar(20),
    observacoes             text,
    created_at              timestamptz,
    created_by              varchar(180),
    updated_at              timestamptz,
    updated_by              varchar(180),
    deleted_at              timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_contrato_locacao_aud_rev ON contrato_locacao_aud (rev);
