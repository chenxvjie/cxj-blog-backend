# 邮箱认证与权限

使用 Spring Security + PostgreSQL 持久化 Bearer 会话。令牌是安全随机的32字节值，数据库只保存 SHA-256 摘要；不是 JWT，不使用旧 JWT_SECRET。每次请求检查会话到期时间及用户状态、角色，禁用用户/修改角色立即生效。有效期24小时，退出删除当前会话。

## 配置

本地默认local配置会关闭真实邮箱与极验，即使.env中存在生产功能开关也不会调用云服务；发送/登录会提示停止，不提供绕过。以下配置用于显式SPRING_PROFILES_ACTIVE=prod的服务器。不可同时启用local和prod。本地文章功能仍需本地PostgreSQL；详见README本地与生产环境。

在服务器 deploy/.env 添加以下变量；Compose 的 backend 必须保留 env_file: [.env]。不把真实密码提交到 Git。

```dotenv
AUTH_EMAIL_ENABLED=true
AUTH_CODE_SECRET=replace-with-at-least-32-random-characters
MAIL_FROM=noreply@mail.chenxujie-bolg.cn
TENCENTCLOUD_SECRET_ID=replace-with-dedicated-cam-secret-id
TENCENTCLOUD_SECRET_KEY=replace-with-dedicated-cam-secret-key
SES_REGION=ap-hongkong
SES_TEMPLATE_ID=219249
GEETEST_ENABLED=true
GEETEST_CAPTCHA_ID=replace-with-geetest-v4-public-id
GEETEST_CAPTCHA_KEY=replace-with-server-only-key
```

AUTH_CODE_SECRET 请使用密码管理器或 `openssl rand -hex 32` 生成。邮件已改为腾讯云 SES SendEmail API（官方 Java SDK），不再使用 SMTP，旧 MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD 可移除。未开启时 AUTH_EMAIL_ENABLED 默认为false，发送和登录返回503，不创建云客户端，不提供万能验证码或日志明文验证码。开启时必须提供密钥、发信地址、有效模板ID和地域；缺少配置会拒绝启动。

香港地域为 ap-hongkong；模板219249需要审核通过，正文使用 {{code}}，有效期文案为5分钟。MAIL_FROM必须是在相同地域已创建的发信地址；模板ID和发信地址均可通过环境变量替换。使用专用CAM子用户，最小权限为 ses:SendEmail，不要授予管理员权限，不要把凭证提交到Git、前端或聊天。环境变量必须注入backend容器，仅在宿主机.env填写但未配置env_file/environment不会生效。

移除Spring Mail依赖及SMTP配置，因此不再注册MailHealthIndicator；保留数据库等原有健康检查。健康UP只表示核心服务健康，不保证模板已审核、API权限或真实投递可用。SES失败会返回通用503，日志仅记录错误码和请求ID，不输出验证码、收件人、密钥或原始异常。发送失败的运行时异常会触发原有数据库事务回滚；API受理不等于收件箱投递成功，投递状态需在腾讯云控制台核实。

## 接口

所有路径在 /api/v1 下，响应沿用 {code,message,data}。

| 方法/路径 | 请求/行为 |
| --- | --- |
| GET /auth/captcha-config | 匿名读取{enabled,captchaId}，仅返回公开ID，不返回Key |
| POST /auth/email-code | {email,captcha:{lot_number,captcha_output,pass_token,gen_time}}；极验通过后发送，验证码可用于注册或登录；不返回验证码 |
| POST /auth/register | {email,code,nickname}；新用户固定READER，返回登录结果 |
| POST /auth/email-login | {email,code}；已有ACTIVE用户登录 |
| GET /auth/me | Authorization: Bearer <accessToken> |
| POST /auth/logout | 撤销请求携带的Bearer会话 |

登录 data 包含 accessToken、tokenType: Bearer、expiresIn: 86400、user: {id,email,nickname,role}。客户端必须显式携带 Authorization；不接受Cookie认证，因此此API关闭CSRF。前端当前仅有提交表单，需要后续补充令牌管理、me和logout集成，推荐令牌放内存；没有实现HttpOnly Cookie登录。

验证码5分钟有效、最大5次猜测、使用即销毁，HMAC摘要存储，邮件地址大小写归一。发送限流：60秒/次、每邮箱每小时5次、每来源IP每小时20次、全站每小时100次；多副本共享数据库。默认不信任X-Forwarded-For，Nginx后面IP限额可能合并为共享限额，属于保守限流。

极验v4：前端点击获取验证码时按需加载https://static.geetest.com/v4/gt4.js，调用getValidate取得四个参数。后端以Key对lot_number做HMAC-SHA256，向固定HTTPS地址https://gcaptcha4.geetest.com/validate提交表单，只有result=success才继续。连接超时3秒、读取超时5秒；异常拒绝，不使用宕机放行。请求时间超过10分钟或未来超过60秒拒绝，服务器应保持时间同步。V3迁移新增auth_captcha_used，成功凭证以摘要原子占用，保留1天并在后续成功验证时清理；即使邮件失败也不能重用凭证，需要重新验证。业务发送限流仍保留，公开上线还需在入口设置请求速率限制以保护极验调用端点。

GEETEST_ENABLED=false是停用而不是绕过：发送接口拒绝请求。启用但缺少ID/Key时拒绝启动。前端不需要VITE密钥，通过captcha-config读取同一公开ID；需在极验后台配置实际使用的网站域名，并同步部署新版前端与后端。旧前端仅提交email将得到400。隐私声明需披露极验第三方人机验证。未使用真实ID/Key执行外部校验，当前测试模拟上游结果；上线前需人工完成弹窗及邮件投递联调。

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

当前使用Flow JAR制品部署，步骤见deploy/FLOW.md。已有docker-compose.flow.yml时，手动检查必须同时带上两个-f文件，避免误用旧镜像。真实SES发信需在模板审核通过、服务器凭证配置完成后，以本人邮箱验收；测试环境使用模拟客户端，不会发送邮件或产生邮件费用。

2026-10-10：管理功能已接入后端ADMIN权限校验，原Nginx `/api/v1/admin/ return 403` 临时规则需要移除，让请求进入受Spring Security保护的/api/代理；检查步骤见 [deploy/NGINX_ADMIN_ACCESS.md](deploy/NGINX_ADMIN_ACCESS.md)。无需开放8080或5432公网端口。/actuator继续仅内网健康检查。

已知限制：短信不在此实现；邮件发送与数据库提交不是分布式事务，极少数提交失败可能导致收到但不可用的验证码；重新申请即可。验证码和限流表保留邮箱，应后续加入到期清理及隐私数据删除流程。数据层使用JdbcTemplate参数化SQL处理行锁及PostgreSQL返回值，文章仍用MyBatis-Plus。
