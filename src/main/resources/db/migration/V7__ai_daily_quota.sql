CREATE TABLE ai_daily_usage (
  subject varchar(80) NOT NULL,
  usage_day date NOT NULL,
  used integer NOT NULL DEFAULT 0 CHECK(used>=0),
  PRIMARY KEY(subject,usage_day)
);
CREATE TABLE ai_usage_reservation (
  id uuid PRIMARY KEY,
  subject varchar(80) NOT NULL,
  usage_day date NOT NULL,
  state varchar(16) NOT NULL CHECK(state IN ('RESERVED','SUCCEEDED','REFUNDED')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_usage_reservation_created ON ai_usage_reservation(created_at);
