ALTER TABLE sys_user ALTER COLUMN role SET DEFAULT 'READER';
-- Fail rather than silently merge existing accounts that differ only in email case.
CREATE UNIQUE INDEX uk_sys_user_email_normalized ON sys_user(lower(email)) WHERE deleted_at IS NULL;
CREATE TABLE auth_code (
  email VARCHAR(320) PRIMARY KEY,
  code_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE auth_session (
  token_hash CHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES sys_user(id),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_session_expiry ON auth_session(expires_at);
CREATE TABLE auth_send_limit (
  scope VARCHAR(400) PRIMARY KEY,
  window_start TIMESTAMPTZ NOT NULL DEFAULT now(),
  count INTEGER NOT NULL DEFAULT 0
);
