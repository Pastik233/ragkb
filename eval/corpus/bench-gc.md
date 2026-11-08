# G1 与 ZGC 的取舍

G1 把堆划分成大小相等的 Region，默认每个 Region 1 到 32 MB，通过 `-XX:G1HeapRegionSize` 调整。它不再要求物理上的新生代和老年代连续，逻辑上区分 Eden、Survivor、Old，靠 Region 的角色动态变化来管理。

G1 的停顿目标是软目标，用 `-XX:MaxGCPauseMillis` 设置，默认 200 毫秒。G1 会根据历史数据预测每个 Region 的回收成本，优先回收收益最高的那些，这也是 Garbage First 这个名字的由来。它允许垃圾最多的 Region 优先被回收，而不是整代扫描。

G1 有两种收集模式：Young GC 只回收年轻代 Region；Mixed GC 会额外回收部分老年代 Region，触发条件是老年代占用超过 `-XX:InitiatingHeapOccupancyPercent`，默认 45%。

ZGC 的目标是完全不同的量级。它把停顿时间控制在 1 毫秒以内，并且停顿时间不随堆大小增长，即使是 TB 级堆也能保持这个水平。实现依赖染色指针和读屏障：对象引用里直接编码了标记位，标记和移动对象时只需要修改指针颜色，不需要 Stop The World 扫描整个堆。

ZGC 在 JDK 15 转为正式特性，JDK 21 支持分代 ZGC，用 `-XX:+UseZGC -XX:+ZGenerational` 开启。分代之后年轻代回收更频繁，整体吞吐有明显改善。

选型上，如果堆在几十 GB 以内、对吞吐更敏感，G1 是默认且稳妥的选择。如果堆很大或者对尾延迟极敏感，比如交易系统要求 P99 在 10 毫秒内，ZGC 更合适，代价是更高的内存占用和一定的吞吐损失。
