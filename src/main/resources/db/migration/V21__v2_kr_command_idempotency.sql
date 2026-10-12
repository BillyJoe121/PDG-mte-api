-- Per-actor replay of direct KR creation. No legacy objectives are synthesized.
CREATE TABLE IF NOT EXISTS kr_create_command (
  actor_id BIGINT NOT NULL REFERENCES professor(id),
  request_key VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  key_result_id BIGINT NOT NULL REFERENCES key_result(id),
  response JSONB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(actor_id, request_key)
);
