# 账号与文章权限

- 统一登录：POST `/api/v1/auth/password-login`（email/password），或原 email-login（email/code）。
- 注册：POST `/api/v1/auth/register` 必须包含 email、nickname、code、password。服务端固定角色 USER，内置管理员邮箱不可注册。
- 密码使用 BCrypt（cost 12），8字符以上且不超过72个UTF-8字节。验证码校验、密码失败限流均由后端执行。
- 内置管理员邮箱为 `1158189673@qq.com`。首次启动前在后端私密 `.env` 或进程环境中设置 `ADMIN_INITIAL_PASSWORD` 为指定初始密码。不要把密码提交到Git；初始化后可移除变量。重启不会重置现有密码。
- Docker 必须通过 Compose backend 服务的 environment 显式传入 `ADMIN_INITIAL_PASSWORD`，仅在宿主机 .env 定义不足以传入容器。
- 未设置初始化密码时输出提示并跳过创建；不会影响其他功能。若该邮箱已被普通账户占用，启动报错，需人工核实，绝不自动提权。
- V4迁移新增 password_hash，将非指定管理员改成USER。旧普通账户没有密码，仍可验证码登录，不会生成默认密码。
- GET/POST `/api/v1/manage/posts`、GET/PUT/DELETE `/api/v1/manage/posts/{id}` 需要登录。用户仅能管理自己的文章，管理员可管理全部。作者身份来自会话，更新不可转移作者。
- 本地默认关闭邮件与极验；密码登录独立可用，验证码登录/注册明确返回不可用提示。需要本地数据库和已初始化账号。
- 前端令牌仅保存在内存，刷新页面需重新登录。生产运行请启用HTTPS。

部署注意：先备份数据库；迁移后旧版本 READER 注册逻辑不再兼容，不应直接回滚到旧注册实现。此改动未执行线上迁移或部署。
