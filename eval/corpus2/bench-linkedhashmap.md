# LinkedHashMap 的顺序控制

LinkedHashMap 继承 HashMap，在哈希表的基础上额外维护一条双向链表，把所有 Entry 按顺序串起来。所以它既保留了 O(1) 的查找，又能保证遍历顺序。

默认是插入顺序。构造时传入 accessOrder=true 会变成访问顺序：每次 get 或 put 命中已有 key，就把该节点移到链表尾部。这个特性是实现 LRU 缓存的基础。

标准写法是继承 LinkedHashMap 并重写 removeEldestEntry，当 size 超过容量时返回 true，容器会自动删掉链表头部的节点，也就是最久未使用的那个。

因为要维护链表，LinkedHashMap 的写入比 HashMap 略慢，每个节点多两个指针，内存占用也更高。读取性能基本一致。

遍历 LinkedHashMap 不会像 HashMap 那样受桶数量影响，它直接沿链表走，所以遍历效率更稳定。

一个常见误区是以为 accessOrder 会影响 get 的性能。实际上 get 本身仍是 O(1)，只是命中后要额外做一次链表摘除和尾插，常数开销很小。

和 HashMap 一样，LinkedHashMap 也不是线程安全的，并发场景需要额外同步或者改用带并发控制的容器。
