ALTER TABLE sys_user ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE sys_user DROP CONSTRAINT sys_user_role_check;
UPDATE sys_user SET role='USER' WHERE role <> 'ADMIN';
UPDATE sys_user SET role='USER' WHERE role='ADMIN' AND lower(email)<>'1158189673@qq.com';
ALTER TABLE sys_user ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE sys_user ADD CONSTRAINT sys_user_role_check CHECK (role IN ('ADMIN','USER'));
ALTER TABLE sys_user ADD CONSTRAINT sys_user_admin_email_check CHECK (role <> 'ADMIN' OR lower(email)='1158189673@qq.com');
