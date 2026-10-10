ALTER TABLE sys_user ADD COLUMN bio VARCHAR(500);
CREATE TABLE blog_comment (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 post_id BIGINT NOT NULL REFERENCES blog_post(id),
 author_id BIGINT NOT NULL REFERENCES sys_user(id),
 parent_id BIGINT REFERENCES blog_comment(id),
 content VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','DELETED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comment_post_status ON blog_comment(post_id,status,created_at);
CREATE TABLE comment_report (
 comment_id BIGINT REFERENCES blog_comment(id),
 reporter_id BIGINT REFERENCES sys_user(id),
 reason VARCHAR(500) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 resolved BOOLEAN NOT NULL DEFAULT false,
 PRIMARY KEY(comment_id,reporter_id)
);
CREATE TABLE site_setting (
 id INT PRIMARY KEY CHECK(id=1),
 title VARCHAR(100) NOT NULL DEFAULT 'CXJ Blog',
 description VARCHAR(500) NOT NULL DEFAULT '',
 about TEXT NOT NULL DEFAULT '',
 contact VARCHAR(320) NOT NULL DEFAULT ''
);
INSERT INTO site_setting(id) VALUES (1);
