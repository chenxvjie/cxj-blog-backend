# 图片上传

图片存储桶：`cxj-blog-images-1317285711`，地域 `ap-nanjing`，私有读写。
公开读取走 `https://img.chenxujie-bolg.cn`，CDN 使用私有源站回源鉴权。

## 上传流程

登录 USER 或 ADMIN 调用 `POST /api/v1/manage/images/upload-url`，提交 filename、contentType、size、contentMd5（MD5 的 Base64）。
后端生成 `images/uploads/{userId}/{UUID}.{extension}`，签发有效期 5 分钟的 HTTPS PUT 地址，签入 Host、Content-Type、Content-MD5、Content-Length、x-cos-forbid-overwrite=true。
浏览器以原始 File 上传，Content-Length 由浏览器自动设置，不携带博客 Bearer token。
上传成功后调用 `POST /api/v1/manage/images/complete`，提交 uploadId。后端检查当前用户、票据有效期、COS HEAD 大小/声明 MIME/ETag 与 MD5 一致，写入现有 file_object 表后才返回 publicUrl。

仅允许 PNG/JPEG/WebP/GIF，单张 1 字节至 5 MiB，不接受 SVG。每个用户最多 5 个未完成票据、每小时最多 30 次签名；全局最多 1000 个有效票据。票据保存在内存中，有效期 10 分钟，重启后未完成上传需要重新上传；同一有效票据重复完成不会重复写记录。
MIME 校验针对声明和 COS 元数据，不对文件进行服务端解码或内容审核。已上传但未完成登记的对象不会自动删除。
图片 CDN 链接公开可访问，包括草稿中上传的图片；此目录不存储私密内容。

## 服务器配置

独立 CAM 子用户只需要 `name/cos:PutObject` 和 `name/cos:HeadObject`，资源限定为：
`qcs::cos:ap-nanjing:uid/1317285711:cxj-blog-images-1317285711/images/uploads/*`。

部署环境变量见 `.env.example`：COS_ENABLED、COS_SECRET_ID、COS_SECRET_KEY、COS_REGION、COS_BUCKET、COS_PUBLIC_BASE_URL。
通过 `deploy/configure-cos.py` 隐藏输入密钥并备份 `.env`；安装新版 deploy-backend.sh 和 preflight-release.sh 后再触发后端发布。配置脚本不重启容器，发布脚本重新生成 Compose 环境变量映射并重建 backend。

COS CORS 来源为 https://chenxujie-bolg.cn，允许 PUT/GET/HEAD，Allow-Headers=*；无需将桶改为公有写。上传走 COS 默认 HTTPS 域名，图片读取走 CDN 域名。

## 验证

本地单元测试覆盖权限、签名头部、大小/类型/校验值拒绝、所有权、过期、频率限制、重复完成、SDK 真实签名和失败提示；未使用生产密钥，也未将模拟测试当作真实 COS 验收。
发布后：重新登录，选择小 PNG 上传，确认正文出现 CDN URL，保存文章并在详情中查看；用普通用户检查自己的上传，退出登录后确认无法申请上传。

参考：[Java 预签名 URL](https://cloud.tencent.com/document/product/436/35217)、[COS 策略](https://cloud.tencent.com/document/product/436/18023)。
