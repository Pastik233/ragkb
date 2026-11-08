# 线上 CPU 飙高的排查路径

第一步用 top 找到占用最高的进程，按 P 排序。拿到 PID 后执行 top -Hp PID 找到最耗 CPU 的线程，记下线程 ID。

第二步把线程 ID 转成十六进制，printf "%x" 12345，因为 Java 线程栈里的 nid 是十六进制。

第三步用 jstack PID 导出线程栈，搜索这个 nid，就能定位到具体是哪段代码。如果是 GC 线程占满 CPU，说明是内存问题而不是业务代码，应该转去看 jstat -gcutil 和堆转储。

常见原因有几类：死循环或正则回溯、频繁的 Full GC、大量线程上下文切换、锁竞争导致的 spin。

上下文切换可以用 vmstat 看 cs 列的数值，也可以用 pidstat -w 看自愿和非自愿切换。

如果是 GC 引起，先用 jstat -gcutil PID 1000 观察各代的回收频率和耗时，再用 jmap -dump 导出堆，用 MAT 找支配树里最大的对象。注意 dump 会暂停应用，生产上要评估影响，优先在预发复现。

火焰图是更高效的手段，用 async-profiler 采集 30 秒生成，能直接看出热点方法，比 jstack 多次采样更准。
