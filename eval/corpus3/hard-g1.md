# G1 收集器的工作机制

## Region 划分

G1 把整个堆切成大小相等的 Region，每个 Region 默认 1 到 32 MB，必须是 2 的幂，通过 `-XX:G1HeapRegionSize` 指定。一个 Region 在同一时刻只能扮演一种角色：Eden、Survivor、Old 或者 Humongous。

Humongous 专门存放大对象，判定标准是对象大小超过 Region 的一半。连续的大对象会占用多个连续的 Humongous Region，这类对象直接在老年代分配，不经过新生代，容易造成碎片和提前触发 Mixed GC。

## 停顿预测模型

G1 的停顿目标是软目标，参数是 `-XX:MaxGCPauseMillis`，默认值 200 毫秒。它不是硬性保证，G1 会根据历史回收数据建立每个 Region 的回收成本模型，预测回收多少个 Region 能把停顿控制在目标以内。

每次 Young GC 结束后，G1 会更新这个模型。所以刚启动时的预测不准，运行一段时间后才趋于稳定。如果把目标设得极小，比如 20 毫秒，G1 每次只能回收很少的 Region，会导致回收赶不上分配，最终退化成 Full GC。

## Mixed GC 的触发

Mixed GC 除了回收年轻代，还会回收部分老年代 Region。触发条件是老年代占用超过 `-XX:InitiatingHeapOccupancyPercent`，默认 45%。这个值调低会更早开始并发标记，减少 Full GC 风险，但会增加标记开销。

Mixed GC 分多次进行，每次回收一部分老年代 Region，直到收益低于阈值或者占用降到目标以下。

## 与 CMS 的差异

CMS 是标记清除，不整理内存，会产生碎片；G1 整体上是标记整理，Region 之间通过复制存活对象来整理，碎片问题小得多。CMS 的并发模式失败会退化成单线程 Full GC，G1 则通过更早的并发标记来规避。
