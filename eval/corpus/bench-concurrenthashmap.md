# ConcurrentHashMap 的并发设计

JDK 7 的 ConcurrentHashMap 使用分段锁，把整个表分成 16 个 Segment，每个 Segment 继承 ReentrantLock，不同段之间可以并发写。缺点是分段数量固定，并发度上限就是段数。

JDK 8 放弃了分段锁，改成 CAS 加 synchronized 锁单个桶的头节点。写入时如果目标桶为空，用 CAS 直接放入；如果桶非空，只对这一个桶的头节点加 synchronized，锁的粒度从段降到了单个链表或红黑树。并发度等于桶的数量，理论上可以支持更多线程同时写。

扩容支持多线程协同迁移。每个线程领取一个步长区间搬运节点，通过 `sizeCtl` 的低位记录参与线程数，每搬运完一个区间就把该位置标记为 ForwardingNode，其他线程遇到这个标记就知道该桶已经迁移完成。

size 的统计没有用一把全局锁。JDK 8 使用 baseCount 加 CounterCell 数组：先尝试 CAS 更新 baseCount，失败则分散到不同的 CounterCell 槽位，最后求和。这和 LongAdder 的思路一致，用空间换掉了热点竞争。

get 操作全程不加锁。Node 的 val 和 next 都用 volatile 修饰，保证可见性，所以读操作可以完全并发。

有一个容易踩的点：ConcurrentHashMap 不允许 null 键和 null 值。原因是并发场景下无法区分“这个 key 不存在”和“这个 key 存在但值是 null”，而单线程的 HashMap 可以用 containsKey 二次确认，并发下这个确认不再可靠。

复合操作仍然需要额外同步。putIfAbsent、computeIfAbsent 这类方法是原子的，但先 get 再 put 不是。计数场景应该直接用 merge 或 compute，不要自己写 check-then-act。
