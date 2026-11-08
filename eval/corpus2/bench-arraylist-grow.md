# ArrayList 的扩容

ArrayList 底层是 Object 数组，默认容量是 10，但只有在第一次 add 时才真正分配，构造时给的是一个空数组，这是为了延迟初始化省内存。

扩容发生在 add 时发现 size 已经等于数组长度。新容量按旧容量的 1.5 倍计算，代码是 oldCapacity + (oldCapacity >> 1)。这个 1.5 倍是折中：太小会频繁复制，太大浪费空间。

扩容通过 Arrays.copyOf 完成，是一次全量复制，时间复杂度 O(n)。所以能预估大小时应该在构造时指定容量，避免多次扩容。批量添加用 addAll 而不是循环 add，因为 addAll 最多只扩容一次。

ArrayList 的删除同样要移动元素。删中间一个元素，后面的所有元素都要前移一位，复杂度 O(n)。频繁在头部插入删除应该用 LinkedList，但 LinkedList 的随机访问是 O(n)，实际大多数场景 ArrayList 反而更快，因为数组内存连续、缓存友好。

ArrayList 不是线程安全的。多线程同时 add 可能导致数组越界或元素覆盖。需要并发时用 CopyOnWriteArrayList，它写时复制整个数组，读操作完全无锁，适合读多写极少的场景，写多时复制开销会非常大。
