# 数据库迁移

此目录采用 Flyway 默认命名规则：`V<版本号>__<描述>.sql`。

- `V1__init_blog_schema.sql`：博客基础表、索引、全文搜索和审计/埋点表。
- 新增数据库结构时创建新的迁移文件，例如 `V2__add_comment_table.sql`；不要修改已在任何共享环境执行过的迁移。

本地 Docker PostgreSQL 对 Windows 宿主机暴露在 `5433`，因此后端本地连接示例为：

```text
jdbc:postgresql://localhost:5433/cxj_blog
```

启动前复制本模块目录的 `.env.example` 为 `.env`，并设置仅用于本地开发的数据库密码：

```powershell
Copy-Item .env.example .env
docker compose up -d
```

迁移由 Spring Boot 中的 Flyway 在应用启动时执行。不要把 `.env` 或生产密码提交到 Git。
