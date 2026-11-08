# ThreadLocal 的内存泄漏

ThreadLocal 本身不存数据，数据存在 Thread 对象的 threadLocals 字段里，类型是 ThreadLocalMap。也就是说每个线程持有一份自己的 Map，key 是 ThreadLocal 实例，value 是实际的值。

ThreadLocalMap 的 Entry 继承 WeakReference，key 是弱引用，value 是强引用。这个设计是为了让 ThreadLocal 对象在没有外部强引用时能被回收，避免 Map 里堆积大量无用的 key。

问题出在 value 上。当 ThreadLocal 被回收后，Entry 的 key 变成 null，但 value 仍然被 Entry 强引用着，而 Entry 又被 Thread 持有。如果这个线程是线程池里的常驻线程，它的生命周期可能和整个应用一样长，这些 key 为 null 的 Entry 就一直无法释放，形成内存泄漏。

ThreadLocalMap 在 get、set、remove 时会顺带清理 key 为 null 的 Entry，但这只是碰运气：如果后续没有再操作这个 Map，脏 Entry 就会一直留着。

正确的做法是在 finally 块里显式调用 remove。尤其在 Web 容器和线程池场景，线程会被复用，不清理会把上一个请求的数据带给下一个请求，除了内存问题还会造成数据串号。

需要区分 InheritableThreadLocal：它允许子线程继承父线程的值，但只在创建子线程时复制一次。线程池里的线程是复用的，不会重新创建，所以 InheritableThreadLocal 在池化场景下基本失效，应该改用 TransmittableThreadLocal 配合装饰器。
