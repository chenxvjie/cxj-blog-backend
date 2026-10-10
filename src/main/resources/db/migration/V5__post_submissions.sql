-- Keep the currently published article intact while an author's revision is reviewed.
CREATE TABLE blog_post_submission (
  post_id BIGINT PRIMARY KEY REFERENCES blog_post(id),
  payload JSONB NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PENDING','REJECTED')),
  review_reason VARCHAR(1000),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_submission_status ON blog_post_submission(status,updated_at);
