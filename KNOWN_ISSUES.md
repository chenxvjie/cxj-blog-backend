# 已知问题

## Flyway 与 PostgreSQL 18

首次启动时，Flyway 会提示当前依赖版本尚未声明测试 PostgreSQL 18.6（日志显示最新已测试版本为 17）。本地迁移 `V1__init_blog_schema.sql` 已实际执行成功。

上线前应升级到明确支持目标 PostgreSQL 大版本的 Flyway 依赖，或将生产数据库固定在已验证版本，并在预发布环境重新执行所有迁移与回滚演练。

## IDEA 本地启动需要数据库密码

IDEA 不会自动继承先前 PowerShell 会话中的 `DB_PASSWORD`。后端已通过 `spring.config.import` 可选加载模块目录中的 `.env`，并将 `POSTGRES_PASSWORD` 作为本地回退值。`.env` 不应提交到 Git；部署与 CI 仍应通过环境变量或密钥管理注入 `DB_PASSWORD`。
