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
| 2026-10-03 | geetest-response-fix | 兼容极验以text/javascript返回JSON，修复二次校验响应转换引起的503；异常日志仅记录类型与HTTP状态。 | 回归覆盖该媒体类型、失败验证、JSONP和尾随非JSON内容；真实发信仍需部署后重试。 |
| 2026-09-20 | local-profile | 默认local配置隔离生产数据库地址与云服务开关，云认证停用时明确中止提示；prod仍严格校验。 | 本地配置隔离、无外部调用和生产缺失配置测试。 |
| 2026-09-20 | geetest-v4 | 发邮箱验证码前强制极验v4二次校验，公开ID配置接口、超时拒绝与V3数据库防重放。 | 模拟极验成功/失败/异常及控制器顺序测试；真实ID/Key和前后端联合部署后需验收。 |
| 2026-09-20 | ses-api | 邮箱改用腾讯云SES模板API，默认香港地域与模板219249；移除SMTP依赖，解决未配置SMTP导致健康检查503。 | 新增SES请求、异常与配置测试；真实投递待模板审核及服务器凭证配置后验收。 |
| 2026-09-19 | flow-deploy | 新增Flow JAR制品部署脚本：运行镜像构建、数据库备份、健康检查、Nginx重载及失败回滚。 | 部署步骤见 deploy/FLOW.md；服务器端完整验收待执行。 |
| 2026-09-19 | auth | 邮箱注册/登录、可撤销Bearer会话、管理员接口保护、作者身份绑定、V2迁移修正默认角色。 | 自动化权限测试通过；SMTP真实投递及PostgreSQL迁移待部署验证，详见 AUTHENTICATION.md。 |
| 2026-09-17 | initial | 初始化 Spring Boot、MyBatis-Plus、Flyway 与 PostgreSQL；建立文章、用户、分类、标签、文件、审计、埋点表及公开文章 API。 | `mvn test` 通过；Flyway V1 已在本地 PostgreSQL 执行。 |
| 2026-09-17 | config | 后端与 Docker 配置收拢至本模块；本地启动自动读取本目录 `.env`。 | IDEA 与 Maven 启动均可连接 PostgreSQL。 |
| 2026-09-18 | docker-prod | 增加 Java 21 多阶段生产镜像与 Docker 构建忽略规则。 | 待云服务器执行 `docker compose build backend` 验证。 |

## 当前账号与验证

统一密码/验证码登录，注册要求验证码和密码；普通用户管理自己的文章，管理员管理全部文章。密码登录在local下独立可用。详见 [ACCOUNT_PERMISSIONS.md](ACCOUNT_PERMISSIONS.md)。

2026-10-02 已复跑59项后端测试并通过，在独立PostgreSQL 18.6执行V1–V4迁移及真实权限接口检查；范围与未验收项见 [LOCAL_VERIFICATION_2026-10-02.md](LOCAL_VERIFICATION_2026-10-02.md)。本地结果不代表生产发布完成。

## 本地与生产环境

本地默认使用local，也可显式运行 `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`。IDEA运行配置的Active profiles填local。数据库需启动本模块PostgreSQL，默认localhost:5433/cxj_blog；只通过LOCAL_DB_URL、LOCAL_DB_USERNAME、LOCAL_DB_PASSWORD自定义（密码可回退POSTGRES_PASSWORD），不使用生产DB_URL。数据库未启动或密码错误仍会阻止启动：先检查本地PostgreSQL及.env，而不是调整云服务器。

local只监听127.0.0.1:8080，忽略AUTH_EMAIL_ENABLED、GEETEST_ENABLED等生产开关，关闭真实邮件、极验、COS及统计。发送验证码、验证码登录和注册会提示未启用并停止；密码登录可用。不生成假验证码、不绕过权限。需要测试认证流程请执行mvn test。不能把local配置部署到公网；生产容器必须明确配置SPRING_PROFILES_ACTIVE=prod，不能同时启用local和prod。真实云服务联调使用独立的非local配置并设置完整凭证。

2026-09-20：邮箱验证码已切换腾讯云SES API，保留注册/登录、可撤销Bearer会话、当前用户与退出、管理员写接口保护、作者身份绑定；Flyway V2 修正新用户默认角色。配置、部署和限制见 [AUTHENTICATION.md](AUTHENTICATION.md)。真实邮件投递需要模板审核通过及部署环境配置后验证。

认证/邮箱验证码/极验、COS/CDN、统计、飞书通知的接入状态见 [EXTERNAL_INTEGRATIONS.md](EXTERNAL_INTEGRATIONS.md)，已知兼容性问题见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。
