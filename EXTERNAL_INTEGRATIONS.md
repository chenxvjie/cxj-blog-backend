# 外部功能接入记录

这些功能不会阻塞当前本地开发。未配置时，系统通过 `/api/v1/system/integrations` 返回 `placeholder`，而不是发起外部请求。

| 功能 | 当前替代/占位 | 正式接入条件 |
| --- | --- | --- |
| 极验 | 已接入v4二次校验及数据库防重放；未开启时拒绝发送验证码，不绕过验证 | GEETEST_ENABLED=true、GEETEST_CAPTCHA_ID、GEETEST_CAPTCHA_KEY；前端同步部署 |
| 腾讯云 COS/CDN | 文件字段保存 URL；暂无上传签名接口 | COS bucket、地域、临时密钥（STS）、CDN 自定义域名 |
| 百度统计 | 前端不注入脚本 | 生产环境站点 ID、隐私政策与用户同意策略 |
| 飞书部署通知 | 不调用 Webhook | 云效流水线、飞书机器人 Webhook 密钥 |
| 邮箱验证码 | 已实现腾讯云 SES API 模板发信、PostgreSQL 验证码/限流/会话；默认关闭 | CAM最小权限凭证、已审核模板、SES_REGION、MAIL_FROM、AUTH_CODE_SECRET，详见 AUTHENTICATION.md |

接入时新增对应的 `application-prod.yml` 环境变量，不得把密钥提交到 Git。
