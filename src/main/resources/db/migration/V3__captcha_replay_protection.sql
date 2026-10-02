CREATE TABLE auth_captcha_used (
  lot_hash varchar(64) PRIMARY KEY,
  consumed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_captcha_used_consumed_at ON auth_captcha_used(consumed_at);
