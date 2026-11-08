# JWT 与 Session 的取舍

Session 是有状态的，服务端保存会话数据，客户端只存一个 sessionId。优点是服务端可以随时让会话失效，缺点是多实例部署需要共享存储，通常落到 Redis。

JWT 是无状态的，用户信息编码在 token 里，服务端只验签不存储。优点是天然适合水平扩展，缺点是签发后无法撤销，只能等过期。

JWT 由三段组成：header、payload、signature，用点分隔。header 声明算法，payload 放 claims，signature 用密钥对前两段签名。payload 只是 Base64 编码，不是加密，任何人都能解出内容，所以绝对不能放密码、身份证号这类敏感信息。

常见的坑是把 alg 设成 none 或者不校验算法，攻击者可以伪造 token。服务端必须显式指定允许的算法，不要相信 header 里的 alg。

另一个坑是把过期时间设得太长。折中方案是双 token：access token 有效期 15 分钟，refresh token 有效期 7 天且存库，注销时删掉 refresh token 即可实现准撤销。

存储位置也有讲究。localStorage 会被 XSS 读取，Cookie 要设 HttpOnly 和 SameSite 防 CSRF。没有绝对安全的方案，取决于更担心哪类攻击。
