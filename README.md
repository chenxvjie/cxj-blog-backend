# CXJ Blog Backend

Spring Boot + MyBatis-Plus + PostgreSQL + Flyway 的博客后端。

## 本地运行

1. 确认本目录 `.env` 包含 `POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD`。
2. 启动数据库：`docker compose up -d`。
3. 启动应用：`mvn spring-boot:run`。
4. 健康检查：`http://localhost:8080/actuator/health`。

数据库迁移位于 `src/main/resources/db/migration`，由 Flyway 在启动时自动执行。

## 提交功能记录

提交代码时，请在此表追加一行，说明本次用户可见功能、数据库迁移或重要配置变更；不要记录密码、令牌、Webhook 等敏感信息。

| 日期 | 提交/版本 | 功能记录 | 影响/验证 |
| --- | --- | --- | --- |
| 2026-09-17 | initial | 初始化 Spring Boot、MyBatis-Plus、Flyway 与 PostgreSQL；建立文章、用户、分类、标签、文件、审计、埋点表及公开文章 API。 | `mvn test` 通过；Flyway V1 已在本地 PostgreSQL 执行。 |
| 2026-09-17 | config | 后端与 Docker 配置收拢至本模块；本地启动自动读取本目录 `.env`。 | IDEA 与 Maven 启动均可连接 PostgreSQL。 |

## 待接入能力

认证/邮箱验证码/极验、COS/CDN、统计、飞书通知的接入状态见 [EXTERNAL_INTEGRATIONS.md](EXTERNAL_INTEGRATIONS.md)，已知兼容性问题见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。
