# AI流式回答部署

POST /api/v1/ai/ask/stream使用SSE响应；保留原/api代理的认证头、Cookie、X-Real-IP、CORS及限流配置。Java和AI均发送X-Accel-Buffering: no及Cache-Control: no-cache, no-transform。标准Nginx会依据该响应头关闭缓冲；若现有配置忽略该头，需在同一API location或单独的精确location设置：

```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 150s;
proxy_send_timeout 150s;
gzip off;
```

这段是加入现有代理location的参数，不能直接替代完整location。保留现有proxy_pass地址及X-Real-IP设置。CDN/WAF也不能缓存、合并或缓冲该流。检查nginx -t通过后再reload，当前只更新仓库文档，未修改服务器。

验收：浏览器Network中响应Content-Type为text/event-stream；生成期间持续收到delta，done仅一次；正常回答只计一次，完成前断连或服务报错退款。旧POST /ai/ask保留兼容，但新版前端使用/ai/ask/stream。只有生成中草稿，引用和文章版本校验通过才确认回答；失败清除草稿。

Java限制最多16个同时转发，超时120秒；AI仍限制2个并行请求并每秒发送等待心跳。次数为游客每日10、登录账号每日20、管理员不限每日次数。完成后断开连接不会退款。退款不代表已经发生的模型调用没有费用。
