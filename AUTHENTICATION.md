# 邮箱认证与权限

使用 Spring Security + PostgreSQL 持久化 Bearer 会话。令牌是安全随机的32字节值，数据库只保存 SHA-256 摘要；不是 JWT，不使用旧 JWT_SECRET。每次请求检查会话到期时间及用户状态、角色，禁用用户/修改角色立即生效。有效期24小时，退出删除当前会话。

## 配置

在服务器 deploy/.env 添加以下变量；Compose 的 backend 必须保留 env_file: [.env]。不把真实密码提交到 Git。

```dotenv
AUTH_EMAIL_ENABLED=true
AUTH_CODE_SECRET=replace-with-at-least-32-random-characters
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your-smtp-user
MAIL_PASSWORD=your-smtp-authorization-secret
MAIL_FROM=blog@example.com
```

AUTH_CODE_SECRET 请使用密码管理器或 `openssl rand -hex 32` 生成。SMTP 使用认证和强制 STARTTLS；端口587示例以邮件供应商要求为准，当前模板不是465隐式TLS配置。未配置时 AUTH_EMAIL_ENABLED 默认为false，发送和登录返回503，不提供万能验证码或日志明文验证码。

## 接口

所有路径在 /api/v1 下，响应沿用 {code,message,data}。

| 方法/路径 | 请求/行为 |
| --- | --- |
| POST /auth/email-code | {email}；通用邮箱所有权验证码，可用于注册或登录；不返回验证码 |
| POST /auth/register | {email,code,nickname}；新用户固定READER，返回登录结果 |
| POST /auth/email-login | {email,code}；已有ACTIVE用户登录 |
| GET /auth/me | Authorization: Bearer <accessToken> |
| POST /auth/logout | 撤销请求携带的Bearer会话 |

登录 data 包含 accessToken、tokenType: Bearer、expiresIn: 86400、user: {id,email,nickname,role}。客户端必须显式携带 Authorization；不接受Cookie认证，因此此API关闭CSRF。前端当前仅有提交表单，需要后续补充令牌管理、me和logout集成，推荐令牌放内存；没有实现HttpOnly Cookie登录。

验证码5分钟有效、最大5次猜测、使用即销毁，HMAC摘要存储，邮件地址大小写归一。发送限流：60秒/次、每邮箱每小时5次、每来源IP每小时20次、全站每小时100次；多副本共享数据库。默认不信任X-Forwarded-For，Nginx后面IP限额可能合并为共享限额，属于保守限流。极验未接入，不会把传入的geetestToken当作已验证；公开发送服务前需评估限额并完成极验。

## 权限和首位管理员

公开只读文章和健康检查无需登录，/api/v1/admin/** 仅ADMIN可访问。其他未知路径默认拒绝。文章authorId由登录身份覆盖，不允许伪造作者；AUTHOR目前无管理员写权限。

先用本人邮箱注册，然后管理员通过数据库人工授予角色；没有公开提权接口。请替换下面邮箱并核实仅更新目标账户：

```sql
UPDATE sys_user SET role='ADMIN'
WHERE lower(email)='your-verified-email@example.com'
  AND status='ACTIVE' AND deleted_at IS NULL
RETURNING id,email,role;
```

## 部署

部署前备份数据库；V2迁移自动执行：默认角色改READER、新建验证码/会话/限流表及邮箱不区分大小写唯一索引，不修改已经存在的用户角色。若历史存在重复大小写邮箱，迁移会停止，需人工核查，不能删除Flyway历史跳过。

服务器拉取代码后执行 `docker compose --env-file .env -f docker-compose.prod.yml up -d --build --no-deps backend`，验证后重载外层Nginx（后端容器IP可能改变）。SMTP真实投递必须由服务器配置后验收。

已有Nginx /api/v1/admin/ return 403可以继续保留；后端认证与管理员登录验收通过后，移除该location让请求进入受Spring Security保护的/api/代理。无需开放8080或5432公网端口。/actuator继续仅内网健康检查。

已知限制：短信/极验不在此实现，邮件发送与数据库提交不是分布式事务，极少数提交失败可能导致收到但不可用的验证码；重新申请即可。验证码和限流表保留邮箱，应后续加入到期清理及隐私数据删除流程。数据层使用JdbcTemplate参数化SQL处理行锁及PostgreSQL返回值，文章仍用MyBatis-Plus。
