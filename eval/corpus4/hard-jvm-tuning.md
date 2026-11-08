# JVM 启动参数怎么配

## 堆大小

-Xms 和 -Xmx 设成一样大，避免堆反复伸缩带来的抖动。堆频繁扩张收缩会触发额外的 Full GC，而且伸缩过程中对象的地址会变化，影响缓存局部性。生产环境一般直接把两者设为同一个值，比如 4g。

容器环境下不要写死 -Xmx，因为 JVM 可能读不到容器的内存限制。JDK 10 之后默认开启 UseContainerSupport，但比例还是要显式设置：-XX:MaxRAMPercentage=75，留 25% 给堆外内存、元空间和线程栈。

## 新生代

新生代大小可以用 -Xmn 直接指定，也可以用 -XX:NewRatio 设置新旧比例。NewRatio 默认是 2，表示老年代是新生代的 2 倍。Web 应用通常对象朝生夕死，把新生代调大能减少晋升到老年代的对象数量，从而减少 Full GC。

但新生代过大会让单次 Young GC 的停顿变长，因为要复制的存活对象更多。这是一个需要压测权衡的参数，没有通用值。

## GC 日志

JDK 9 之后统一用 -Xlog 系列参数：-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=50m。比起老的 -XX:+PrintGCDetails 更灵活，可以按 tag 过滤，也可以配置滚动策略。

GC 日志是排查停顿问题的第一手资料，必须开。没有日志时只能靠猜，而 GC 问题最怕猜。

## 元空间

-XX:MetaspaceSize 是首次触发元空间 GC 的阈值，不是初始大小，很多人会搞混。-XX:MaxMetaspaceSize 才是上限。动态生成类多的应用（大量 CGLIB 代理、Groovy 脚本）要留意元空间增长。
