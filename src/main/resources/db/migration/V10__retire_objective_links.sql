-- Retira el Objetivo como nivel de la jerarquía: el KR se vincula N:M a Metas y Apuestas.
-- La tabla objective se conserva como legado; ningún código la escribe.

CREATE TABLE key_result_goal (
    key_result_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    CONSTRAINT pk_key_result_goal PRIMARY KEY (key_result_id, goal_id),
    CONSTRAINT fk_key_result_goal_key_result
        FOREIGN KEY (key_result_id) REFERENCES key_result (id) ON DELETE CASCADE,
    CONSTRAINT fk_key_result_goal_goal
        FOREIGN KEY (goal_id) REFERENCES goal (id)
);

CREATE TABLE key_result_strategic_bet (
    key_result_id BIGINT NOT NULL,
    strategic_bet_id BIGINT NOT NULL,
    CONSTRAINT pk_key_result_strategic_bet PRIMARY KEY (key_result_id, strategic_bet_id),
    CONSTRAINT fk_key_result_strategic_bet_key_result
        FOREIGN KEY (key_result_id) REFERENCES key_result (id) ON DELETE CASCADE,
    CONSTRAINT fk_key_result_strategic_bet_bet
        FOREIGN KEY (strategic_bet_id) REFERENCES strategic_bet (id)
);

CREATE INDEX IF NOT EXISTS idx_key_result_goal_goal ON key_result_goal (goal_id);
CREATE INDEX IF NOT EXISTS idx_key_result_strategic_bet_bet ON key_result_strategic_bet (strategic_bet_id);

INSERT INTO key_result_goal (key_result_id, goal_id)
SELECT kr.id, o.goal_id
FROM key_result kr
JOIN objective o ON o.id = kr.objective_id;

INSERT INTO key_result_strategic_bet (key_result_id, strategic_bet_id)
SELECT kr.id, o.strategic_bet_id
FROM key_result kr
JOIN objective o ON o.id = kr.objective_id;

UPDATE key_result
SET academic_period_id = (
    SELECT o.academic_period_id FROM objective o WHERE o.id = key_result.objective_id
)
WHERE academic_period_id IS NULL
  AND objective_id IS NOT NULL;

ALTER TABLE key_result ALTER COLUMN objective_id DROP NOT NULL;
