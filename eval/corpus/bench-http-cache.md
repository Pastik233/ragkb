# HTTP 缓存协商

强缓存由 Cache-Control 和 Expires 控制，命中时浏览器直接读本地副本，不发请求。Cache-Control 是 HTTP/1.1 的字段，优先级高于 HTTP/1.0 的 Expires。常用值有 max-age=3600、no-cache、no-store、private、public。

no-cache 不是不缓存，而是每次使用前必须向服务端验证。no-store 才是完全不存储。这两个字段经常被混淆。

协商缓存靠 Last-Modified 加 If-Modified-Since，或者 ETag 加 If-None-Match。服务端判断资源没变就返回 304，响应体为空，只省传输不省请求。ETag 的精度比时间戳高，能识别同一秒内的修改，优先级也更高。

强缓存和协商缓存的区别是：强缓存不发请求，协商缓存一定发请求但可能只回 304。

前端工程里常见的做法是 HTML 用 no-cache，保证用户拿到最新的入口文件；JS 和 CSS 带内容哈希并设置一年的 max-age，靠文件名变化来失效。这样既能让静态资源长期缓存，又能保证发版后用户立刻拿到新版本。

CDN 上还要注意回源策略和缓存键的设计，如果缓存键包含了不必要的 cookie 或请求头，会导致缓存命中率极低。
