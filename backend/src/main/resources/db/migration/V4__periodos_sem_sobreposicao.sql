-- =============================================================================
-- V4 — Não sobreposição de períodos de veículo no contrato (RN-01).
--
-- A V3 garantia a regra com um índice único parcial sobre `contrato_id` onde
-- `fim IS NULL`: no máximo um período aberto. A intenção estava certa, mas a
-- implementação tinha dois problemas.
--
-- 1. Ela é verificada linha a linha, no momento da escrita. Ao substituir um
--    veículo, o Hibernate emite o INSERT do período novo antes do UPDATE que
--    fecha o anterior — a ordem padrão de um flush. Existe portanto um instante,
--    dentro da mesma transação, com dois períodos abertos, e o índice disparava
--    mesmo em uma substituição perfeitamente válida.
--
-- 2. Ela só proibia dois períodos **abertos**. Dois períodos fechados que se
--    sobrepusessem — digamos, 01/01 a 30/06 e 01/03 a 31/08 — passavam sem
--    reclamação, e a pergunta "qual veículo estava no contrato em 15/04?"
--    passaria a ter duas respostas, quebrando a RN-18.
--
-- A restrição de exclusão abaixo resolve os dois: proíbe qualquer sobreposição
-- de intervalo dentro do mesmo contrato, e é DEFERRABLE — verificada no commit,
-- quando a transação já deixou os dados consistentes.
--
-- `daterange(inicio, fim, '[]')` trata os limites como inclusivos, de modo que um
-- período terminado em 31/05 e outro iniciado em 01/06 se encostam sem colidir.
-- `fim` nulo produz intervalo sem limite superior, que é exatamente o significado
-- de "período em curso".
-- =============================================================================

-- `btree_gist` permite combinar igualdade em bigint com sobreposição de intervalo
-- no mesmo índice GiST; sem ela, `contrato_id WITH =` não é aceito.
CREATE EXTENSION IF NOT EXISTS btree_gist;

DROP INDEX IF EXISTS ux_substituicao_periodo_aberto;

ALTER TABLE substituicao_veiculo
    ADD CONSTRAINT ex_substituicao_sem_sobreposicao
    EXCLUDE USING gist (
        contrato_id WITH =,
        daterange(inicio, fim, '[]') WITH &&
    )
    DEFERRABLE INITIALLY DEFERRED;

COMMENT ON CONSTRAINT ex_substituicao_sem_sobreposicao ON substituicao_veiculo IS
    'RN-01: os períodos de veículo de um contrato não podem se sobrepor. Adiada até o '
    'commit, porque uma substituição fecha um período e abre outro na mesma transação.';

-- -----------------------------------------------------------------------------
-- Remoção de ux_contrato_veiculo_ativo.
--
-- A V3 impedia que dois contratos ativos apontassem para o mesmo veículo. A ideia
-- é razoável — um carro não está fisicamente em duas obras ao mesmo tempo —, mas
-- a restrição foi um acréscimo além do texto: a RN-01 fala do contrato ("um
-- contrato tem exatamente um veículo ativo por vez"), não do veículo.
--
-- E o acervo real a contradiz: cinco veículos aparecem em dois contratos com
-- períodos sobrepostos no controle geral. Alguns são o dia da transferência
-- contado nas duas pontas; outros são conflito de fato. Mantida como restrição de
-- banco, a carga do histórico simplesmente falharia, e a informação se perderia.
--
-- Um conflito desses é um problema de dado a ser mostrado ao gestor, não um motivo
-- para recusar o registro. Ele passa a aparecer na central de pendências, que é
-- onde a RN-23 concentra o que precisa de tratativa humana.
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS ux_contrato_veiculo_ativo;
