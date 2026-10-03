# 外部功能接入记录

这些功能不会阻塞当前本地开发。未配置的功能不会发起外部请求；图片上传关闭时接口返回503，并提供明确提示。

| 功能 | 当前替代/占位 | 正式接入条件 |
| --- | --- | --- |
| 极验 | 已接入v4二次校验及数据库防重放；未开启时拒绝发送验证码，不绕过验证 | GEETEST_ENABLED=true、GEETEST_CAPTCHA_ID、GEETEST_CAPTCHA_KEY；前端同步部署 |
| 腾讯云 COS/CDN | 已实现受限 PUT 预签名、HEAD 校验和文件登记，默认关闭 | 独立 COS 凭证、bucket、地域、CDN HTTPS 域名；见 COS_UPLOAD.md |
| 百度统计 | 前端不注入脚本 | 生产环境站点 ID、隐私政策与用户同意策略 |
| 飞书部署通知 | 不调用 Webhook | 云效流水线、飞书机器人 Webhook 密钥 |
| 邮箱验证码 | 已实现腾讯云 SES API 模板发信、PostgreSQL 验证码/限流/会话；默认关闭 | CAM最小权限凭证、已审核模板、SES_REGION、MAIL_FROM、AUTH_CODE_SECRET，详见 AUTHENTICATION.md |

配置使用环境变量，并由部署脚本映射到容器；不得把密钥提交到 Git。
