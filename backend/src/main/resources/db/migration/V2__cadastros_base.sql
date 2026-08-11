-- =============================================================================
-- V2 — Cadastros base: obras, locadoras, condutores, veículos, fornecedores
--      credenciados e tabelas de preço de locação.
--
-- A estrutura corrige as violações de normalização das planilhas atuais:
--   * fornecedores estão hoje em uma aba por tipo, com colunas específicas
--     misturadas; aqui viram uma tabela base mais tabelas satélite por tipo;
--   * o vínculo fornecedor↔obra é N:N (um posto atende várias obras);
--   * as tabelas de preço têm pacotes de KM como colunas (3000/4500/5000/6000
--     na Unidas, 3000/4000/5000 na Localiza); aqui viram linhas, o que permite
--     qualquer conjunto de pacotes por locadora sem alterar o schema;
--   * credenciais de portal, hoje em texto claro nas planilhas, passam a ser
--     cifradas em AES-256-GCM pela aplicação (RN-20).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- obra — frentes de trabalho onde a frota é alocada.
-- -----------------------------------------------------------------------------
CREATE TABLE obra (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo       varchar(20)  NOT NULL,
    nome         varchar(180) NOT NULL,
    cliente      varchar(180),
    cidade       varchar(120) NOT NULL,
    uf           varchar(2)   NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'ATIVA',
    data_inicio  date,
    data_fim     date,
    observacoes  text,
    created_at   timestamptz  NOT NULL,
    created_by   varchar(180) NOT NULL,
    updated_at   timestamptz  NOT NULL,
    updated_by   varchar(180) NOT NULL,
    deleted_at   timestamptz,
    CONSTRAINT ck_obra_status CHECK (status IN ('ATIVA', 'ENCERRADA')),
    CONSTRAINT ck_obra_uf CHECK (uf ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_obra_periodo CHECK (data_fim IS NULL OR data_inicio IS NULL OR data_fim >= data_inicio)
);

COMMENT ON TABLE  obra IS 'Obras da consultoria, ex.: 24.019 SKER Ventos de Santa Eugênia.';
COMMENT ON COLUMN obra.codigo IS 'Código interno da obra, ex.: 24.019.';

CREATE UNIQUE INDEX ux_obra_codigo ON obra (codigo) WHERE deleted_at IS NULL;
CREATE INDEX ix_obra_status ON obra (status) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- locadora — empresas de quem os veículos são alugados.
-- As credenciais de portal ficam cifradas; o IV acompanha o próprio valor.
-- -----------------------------------------------------------------------------
CREATE TABLE locadora (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome                  varchar(160) NOT NULL,
    tipo                  varchar(20)  NOT NULL,
    consultor             varchar(160),
    telefone              varchar(60),
    email                 varchar(180),
    portal_url            varchar(400),
    portal_login_cifrado  text,
    portal_senha_cifrada  text,
    canal_reservas        varchar(200),
    canal_manutencao      varchar(200),
    canal_guincho         varchar(200),
    canal_assistencia_24h varchar(200),
    canal_financeiro      varchar(200),
    canal_suporte         varchar(200),
    canal_telemetria      varchar(200),
    observacoes           text,
    ativa                 boolean      NOT NULL DEFAULT true,
    created_at            timestamptz  NOT NULL,
    created_by            varchar(180) NOT NULL,
    updated_at            timestamptz  NOT NULL,
    updated_by            varchar(180) NOT NULL,
    deleted_at            timestamptz,
    CONSTRAINT ck_locadora_tipo CHECK (tipo IN ('NACIONAL', 'AVULSA'))
);

COMMENT ON TABLE  locadora IS 'Locadoras parceiras: nacionais (Unidas, Localiza) e avulsas/locais.';
COMMENT ON COLUMN locadora.portal_senha_cifrada IS 'AES-256-GCM em Base64 (IV + criptograma). Nunca em texto claro (RN-20).';

CREATE UNIQUE INDEX ux_locadora_nome ON locadora (lower(nome)) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- condutor — funcionários habilitados a conduzir os veículos.
-- -----------------------------------------------------------------------------
CREATE TABLE condutor (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           varchar(180) NOT NULL,
    cargo          varchar(120),
    cpf            varchar(11)  NOT NULL,
    cnh_numero     varchar(20),
    cnh_categoria  varchar(4),
    cnh_validade   date,
    telefone       varchar(60),
    email          varchar(180),
    obra_atual_id  bigint REFERENCES obra (id),
    status         varchar(20)  NOT NULL DEFAULT 'ATIVO',
    observacoes    text,
    created_at     timestamptz  NOT NULL,
    created_by     varchar(180) NOT NULL,
    updated_at     timestamptz  NOT NULL,
    updated_by     varchar(180) NOT NULL,
    deleted_at     timestamptz,
    CONSTRAINT ck_condutor_status CHECK (status IN ('ATIVO', 'INATIVO')),
    CONSTRAINT ck_condutor_cpf CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT ck_condutor_cnh_categoria CHECK (cnh_categoria IS NULL OR cnh_categoria ~ '^[ABCDE]{1,4}$')
);

COMMENT ON COLUMN condutor.cpf IS 'Somente dígitos; a formatação é responsabilidade da exibição.';
COMMENT ON COLUMN condutor.cnh_validade IS 'Base dos alertas de 60/30 dias e do bloqueio de vínculo (RN-16).';

CREATE UNIQUE INDEX ux_condutor_cpf ON condutor (cpf) WHERE deleted_at IS NULL;
CREATE INDEX ix_condutor_obra_atual ON condutor (obra_atual_id);
CREATE INDEX ix_condutor_cnh_validade ON condutor (cnh_validade) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- veiculo — veículos físicos alugados. A placa é única entre os não excluídos
-- (RN-02) e é sempre armazenada normalizada: caixa alta, sem espaços nem hífen.
-- -----------------------------------------------------------------------------
CREATE TABLE veiculo (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    placa                 varchar(7)   NOT NULL,
    modelo                varchar(120) NOT NULL,
    fabricante            varchar(120),
    ano_fabricacao        integer,
    categoria             varchar(20)  NOT NULL,
    combustivel           varchar(20)  NOT NULL,
    locadora_id           bigint       NOT NULL REFERENCES locadora (id),
    grupo_tarifario       varchar(20),
    codigo_interno        varchar(40),
    possui_rastreador     boolean      NOT NULL DEFAULT false,
    fornecedor_rastreador varchar(160),
    possui_adesivo        boolean      NOT NULL DEFAULT false,
    status                varchar(20)  NOT NULL DEFAULT 'DISPONIVEL',
    observacoes           text,
    created_at            timestamptz  NOT NULL,
    created_by            varchar(180) NOT NULL,
    updated_at            timestamptz  NOT NULL,
    updated_by            varchar(180) NOT NULL,
    deleted_at            timestamptz,
    CONSTRAINT ck_veiculo_placa CHECK (placa ~ '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$'),
    CONSTRAINT ck_veiculo_categoria
        CHECK (categoria IN ('PASSEIO', 'SUV', 'QUATRO_X_QUATRO', 'UTILITARIO')),
    CONSTRAINT ck_veiculo_combustivel
        CHECK (combustivel IN ('FLEX', 'GASOLINA', 'ETANOL', 'DIESEL', 'HIBRIDO', 'ELETRICO')),
    CONSTRAINT ck_veiculo_status
        CHECK (status IN ('DISPONIVEL', 'EM_USO', 'EM_MANUTENCAO', 'DEVOLVIDO')),
    CONSTRAINT ck_veiculo_ano CHECK (ano_fabricacao IS NULL OR ano_fabricacao BETWEEN 1980 AND 2100)
);

COMMENT ON TABLE  veiculo IS 'Veículos alugados. O vínculo operacional com a obra é do contrato, não do veículo.';
COMMENT ON CONSTRAINT ck_veiculo_placa ON veiculo IS
    'Aceita o padrão antigo AAA9999 e o Mercosul AAA9A99 com uma única expressão (RN-02).';

CREATE UNIQUE INDEX ux_veiculo_placa ON veiculo (placa) WHERE deleted_at IS NULL;
CREATE INDEX ix_veiculo_locadora ON veiculo (locadora_id);
CREATE INDEX ix_veiculo_status ON veiculo (status) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- fornecedor — base comum dos credenciados. Os campos específicos de cada tipo
-- ficam em tabelas satélite, evitando uma tabela larga e majoritariamente nula.
-- -----------------------------------------------------------------------------
CREATE TABLE fornecedor (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo              varchar(20)  NOT NULL,
    nome              varchar(180) NOT NULL,
    cidade            varchar(120),
    uf                varchar(2),
    endereco          varchar(300),
    telefone          varchar(120),
    email             varchar(180),
    responsavel       varchar(160),
    funcionamento     varchar(200),
    forma_faturamento varchar(200),
    forma_pagamento   varchar(200),
    credenciado_em    date,
    ativo             boolean      NOT NULL DEFAULT true,
    observacoes       text,
    created_at        timestamptz  NOT NULL,
    created_by        varchar(180) NOT NULL,
    updated_at        timestamptz  NOT NULL,
    updated_by        varchar(180) NOT NULL,
    deleted_at        timestamptz,
    CONSTRAINT ck_fornecedor_tipo CHECK (tipo IN
        ('POSTO', 'LAVA_JATO', 'BORRACHARIA', 'PARA_BRISAS', 'RASTREADOR', 'GRAFICA', 'OFICINA')),
    CONSTRAINT ck_fornecedor_uf CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$')
);

COMMENT ON TABLE fornecedor IS 'Fornecedores credenciados. Dados por tipo ficam nas tabelas fornecedor_*.';

CREATE UNIQUE INDEX ux_fornecedor_nome_tipo ON fornecedor (lower(nome), tipo) WHERE deleted_at IS NULL;
CREATE INDEX ix_fornecedor_tipo ON fornecedor (tipo) WHERE deleted_at IS NULL;

-- Um mesmo posto ou lava-jato atende mais de uma obra: o vínculo é N:N.
CREATE TABLE fornecedor_obra (
    fornecedor_id bigint NOT NULL REFERENCES fornecedor (id) ON DELETE CASCADE,
    obra_id       bigint NOT NULL REFERENCES obra (id),
    PRIMARY KEY (fornecedor_id, obra_id)
);

CREATE INDEX ix_fornecedor_obra_obra ON fornecedor_obra (obra_id);

-- Posto: dias em que o abastecimento é autorizado (RN-04).
CREATE TABLE fornecedor_posto (
    fornecedor_id     bigint PRIMARY KEY REFERENCES fornecedor (id) ON DELETE CASCADE,
    dias_autorizados  varchar(60) NOT NULL DEFAULT '',
    acesso_faturas    varchar(120)
);

COMMENT ON COLUMN fornecedor_posto.dias_autorizados IS
    'Dias da semana separados por vírgula: SEG,TER,QUA,QUI,SEX,SAB,DOM. Vazio = sem restrição.';

-- Lava-jato: frequência permitida (RN-05) e preço por categoria de veículo.
CREATE TABLE fornecedor_lava_jato (
    fornecedor_id           bigint PRIMARY KEY REFERENCES fornecedor (id) ON DELETE CASCADE,
    servicos_por_semana     integer       NOT NULL DEFAULT 1,
    preco_passeio           numeric(12,2),
    preco_suv               numeric(12,2),
    preco_quatro_x_quatro   numeric(12,2),
    CONSTRAINT ck_lava_jato_frequencia CHECK (servicos_por_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_lava_jato_precos CHECK (
        coalesce(preco_passeio, 0) >= 0
        AND coalesce(preco_suv, 0) >= 0
        AND coalesce(preco_quatro_x_quatro, 0) >= 0)
);

-- Rastreador: custos e credenciais do portal de telemetria (RN-20).
CREATE TABLE fornecedor_rastreador (
    fornecedor_id        bigint PRIMARY KEY REFERENCES fornecedor (id) ON DELETE CASCADE,
    mensalidade          numeric(12,2),
    custo_instalacao     numeric(12,2),
    custo_desinstalacao  numeric(12,2),
    equipadora           varchar(180),
    portal_url           varchar(400),
    portal_login_cifrado text,
    portal_senha_cifrada text,
    CONSTRAINT ck_rastreador_custos CHECK (
        coalesce(mensalidade, 0) >= 0
        AND coalesce(custo_instalacao, 0) >= 0
        AND coalesce(custo_desinstalacao, 0) >= 0)
);

-- Gráfica: adesivos e imãs de identificação dos veículos.
CREATE TABLE fornecedor_grafica (
    fornecedor_id   bigint PRIMARY KEY REFERENCES fornecedor (id) ON DELETE CASCADE,
    tamanho_adesivo varchar(40),
    preco_adesivo   numeric(12,2),
    tamanho_ima     varchar(40),
    preco_ima       numeric(12,2),
    CONSTRAINT ck_grafica_precos CHECK (
        coalesce(preco_adesivo, 0) >= 0 AND coalesce(preco_ima, 0) >= 0)
);

-- -----------------------------------------------------------------------------
-- tabela_preco — vigência anual por locadora (RN-14). Lançamentos consultam a
-- vigência da competência, não a tabela mais recente.
-- -----------------------------------------------------------------------------
CREATE TABLE tabela_preco (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    locadora_id   bigint      NOT NULL REFERENCES locadora (id),
    ano_vigencia  integer     NOT NULL,
    observacoes   text,
    created_at    timestamptz  NOT NULL,
    created_by    varchar(180) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    updated_by    varchar(180) NOT NULL,
    deleted_at    timestamptz,
    CONSTRAINT ck_tabela_preco_ano CHECK (ano_vigencia BETWEEN 2000 AND 2100)
);

COMMENT ON TABLE tabela_preco IS 'Uma vigência por locadora e ano. Ver RN-14.';

CREATE UNIQUE INDEX ux_tabela_preco_locadora_ano
    ON tabela_preco (locadora_id, ano_vigencia) WHERE deleted_at IS NULL;

-- Grupo tarifário da locadora, ex.: "AM — KWID/Mobi" na Unidas.
CREATE TABLE grupo_tarifario (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tabela_preco_id   bigint       NOT NULL REFERENCES tabela_preco (id) ON DELETE CASCADE,
    codigo            varchar(20)  NOT NULL,
    veiculos_do_grupo varchar(300) NOT NULL,
    categoria         varchar(20)  NOT NULL,
    CONSTRAINT ck_grupo_categoria
        CHECK (categoria IN ('PASSEIO', 'SUV', 'QUATRO_X_QUATRO', 'UTILITARIO'))
);

CREATE UNIQUE INDEX ux_grupo_tarifario_codigo ON grupo_tarifario (tabela_preco_id, upper(codigo));
CREATE INDEX ix_grupo_tarifario_tabela ON grupo_tarifario (tabela_preco_id);

-- Um valor mensal por pacote de KM. Pacotes viram linhas justamente porque
-- variam entre locadoras (Unidas: 3000/4500/5000/6000; Localiza: 3000/4000/5000).
CREATE TABLE preco_pacote_km (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo_tarifario_id  bigint        NOT NULL REFERENCES grupo_tarifario (id) ON DELETE CASCADE,
    pacote_km           integer       NOT NULL,
    valor_mensal        numeric(12,2) NOT NULL,
    CONSTRAINT ck_pacote_km_positivo CHECK (pacote_km > 0),
    CONSTRAINT ck_pacote_valor_positivo CHECK (valor_mensal >= 0)
);

CREATE UNIQUE INDEX ux_preco_pacote_km ON preco_pacote_km (grupo_tarifario_id, pacote_km);

-- Valor do KM excedente por categoria (RN-06). `pacote_km` nulo significa que o
-- valor vale para todos os pacotes — é o caso da Unidas, enquanto a Localiza
-- cobra valores distintos por pacote.
CREATE TABLE preco_km_excedente (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tabela_preco_id bigint        NOT NULL REFERENCES tabela_preco (id) ON DELETE CASCADE,
    categoria       varchar(20)   NOT NULL,
    pacote_km       integer,
    valor_km        numeric(12,2) NOT NULL,
    CONSTRAINT ck_km_excedente_categoria
        CHECK (categoria IN ('PASSEIO', 'SUV', 'QUATRO_X_QUATRO', 'UTILITARIO')),
    CONSTRAINT ck_km_excedente_valor CHECK (valor_km >= 0),
    CONSTRAINT ck_km_excedente_pacote CHECK (pacote_km IS NULL OR pacote_km > 0)
);

-- Unicidade com `pacote_km` nulo exige dois índices parciais: em SQL, NULL nunca
-- é igual a NULL, então um índice único comum permitiria duplicatas do caso geral.
CREATE UNIQUE INDEX ux_km_excedente_por_pacote
    ON preco_km_excedente (tabela_preco_id, categoria, pacote_km) WHERE pacote_km IS NOT NULL;
CREATE UNIQUE INDEX ux_km_excedente_geral
    ON preco_km_excedente (tabela_preco_id, categoria) WHERE pacote_km IS NULL;

-- =============================================================================
-- Trilhas de auditoria do Envers para as entidades críticas desta fase.
-- Locadora e fornecedor guardam credenciais; veículo e condutor sustentam a
-- rastreabilidade temporal exigida pela RN-18.
-- =============================================================================

CREATE TABLE obra_aud (
    id          bigint   NOT NULL,
    rev         bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype     smallint NOT NULL,
    codigo      varchar(20),
    nome        varchar(180),
    cliente     varchar(180),
    cidade      varchar(120),
    uf          varchar(2),
    status      varchar(20),
    data_inicio date,
    data_fim    date,
    observacoes text,
    created_at  timestamptz,
    created_by  varchar(180),
    updated_at  timestamptz,
    updated_by  varchar(180),
    deleted_at  timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_obra_aud_rev ON obra_aud (rev);

-- As credenciais cifradas ficam fora da trilha: replicá-las multiplicaria as
-- cópias de um segredo sem que ninguém precise do histórico delas.
CREATE TABLE locadora_aud (
    id                    bigint   NOT NULL,
    rev                   bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype               smallint NOT NULL,
    nome                  varchar(160),
    tipo                  varchar(20),
    consultor             varchar(160),
    telefone              varchar(60),
    email                 varchar(180),
    portal_url            varchar(400),
    canal_reservas        varchar(200),
    canal_manutencao      varchar(200),
    canal_guincho         varchar(200),
    canal_assistencia_24h varchar(200),
    canal_financeiro      varchar(200),
    canal_suporte         varchar(200),
    canal_telemetria      varchar(200),
    observacoes           text,
    ativa                 boolean,
    created_at            timestamptz,
    created_by            varchar(180),
    updated_at            timestamptz,
    updated_by            varchar(180),
    deleted_at            timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_locadora_aud_rev ON locadora_aud (rev);

CREATE TABLE condutor_aud (
    id            bigint   NOT NULL,
    rev           bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype       smallint NOT NULL,
    nome          varchar(180),
    cargo         varchar(120),
    cpf           varchar(11),
    cnh_numero    varchar(20),
    cnh_categoria varchar(4),
    cnh_validade  date,
    telefone      varchar(60),
    email         varchar(180),
    obra_atual_id bigint,
    status        varchar(20),
    observacoes   text,
    created_at    timestamptz,
    created_by    varchar(180),
    updated_at    timestamptz,
    updated_by    varchar(180),
    deleted_at    timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_condutor_aud_rev ON condutor_aud (rev);

CREATE TABLE veiculo_aud (
    id                    bigint   NOT NULL,
    rev                   bigint   NOT NULL REFERENCES revisao_auditoria (id),
    revtype               smallint NOT NULL,
    placa                 varchar(7),
    modelo                varchar(120),
    fabricante            varchar(120),
    ano_fabricacao        integer,
    categoria             varchar(20),
    combustivel           varchar(20),
    locadora_id           bigint,
    grupo_tarifario       varchar(20),
    codigo_interno        varchar(40),
    possui_rastreador     boolean,
    fornecedor_rastreador varchar(160),
    possui_adesivo        boolean,
    status                varchar(20),
    observacoes           text,
    created_at            timestamptz,
    created_by            varchar(180),
    updated_at            timestamptz,
    updated_by            varchar(180),
    deleted_at            timestamptz,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_veiculo_aud_rev ON veiculo_aud (rev);
