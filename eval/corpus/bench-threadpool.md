# 线程池的核心参数

ThreadPoolExecutor 有七个参数：corePoolSize、maximumPoolSize、keepAliveTime、unit、workQueue、threadFactory、handler。

任务提交后的处理顺序是：核心线程未满就新建核心线程；核心线程满了就进队列；队列满了且线程数未达上限就新建非核心线程；都满了就执行拒绝策略。这个顺序和很多人直觉相反——队列没满之前不会扩容到 maximumPoolSize。

队列的选择直接决定行为。用无界队列 LinkedBlockingQueue 时，maximumPoolSize 永远不会生效，风险是任务堆积导致 OOM。用 SynchronousQueue 时任务不排队，直接交给线程，适合高吞吐短任务，配合较大的 maximumPoolSize。

四种拒绝策略：AbortPolicy 抛异常，默认值；CallerRunsPolicy 由提交任务的线程自己执行，起到反压作用；DiscardPolicy 静默丢弃；DiscardOldestPolicy 丢掉队列里最老的任务。

线程数估算分场景：CPU 密集型取核数加一；IO 密集型取核数乘以 (1 + 等待时间/计算时间)，实际还要压测校正。

核心线程默认不会回收，除非设置 allowCoreThreadTimeOut(true)。线程池应该显式创建并命名，不要用 Executors 的快捷方法，因为 FixedThreadPool 和 SingleThreadPool 用的是无界队列，CachedThreadPool 的最大线程数是 Integer.MAX_VALUE，都有 OOM 风险。
