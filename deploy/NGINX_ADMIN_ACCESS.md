# 管理员接口被 Nginx 返回 403

2026-10-10：线上 `/api/v1/admin/comments?page=1` 返回 `text/html`、555 字节的 Nginx 403；本地同版本管理员 Cookie 请求 `/admin/users` 与 `/admin/comments` 均为 200。仓库旧认证说明曾要求保留 `/api/v1/admin/ return 403` 的临时封锁。需检查实际运行的外层 Nginx 配置，不能通过放宽后端角色权限解决。

在服务器部署目录执行只读检查（Compose 文件名按现有部署使用）：

```sh
docker compose exec -T nginx nginx -T
```

找到 `/api/v1/admin/`、`return 403` 或 `deny all` 相关规则，并找到其挂载的主机配置文件。备份该文件后，删除仅针对管理员 API 的临时拦截 location，例如：

```nginx
location /api/v1/admin/ {
    return 403;
}
```

让该路径落入已有 `/api/` 反向代理；保留原代理目标、路径改写、请求头、HTTPS 配置及其他保护，不要新增无权限校验的接口或公开后端端口。修改后先检查语法，成功后再 reload：

```sh
docker compose exec -T nginx nginx -t
docker compose exec -T nginx nginx -s reload
```

验收：管理员登录后两个接口返回 200 JSON，普通用户返回 403 JSON，未登录返回 401 JSON。若仍返回 HTML 403，检查 `nginx -T` 中其他 location、外层代理/WAF；若返回 JSON 403，检查 `/api/v1/auth/me` 的角色是否 ADMIN。不要在日志、截图或命令中公开 Cookie。

本地仓库仅有前端静态 Nginx 配置，不包含线上外层 Nginx 配置。本轮没有修改服务器配置或重载线上 Nginx。
