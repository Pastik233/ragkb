# AQS 与 ReentrantLock

AQS 的全称是 AbstractQueuedSynchronizer，核心是一个 volatile 的 int 状态 state 加一个 CLH 双向队列。state 的语义由子类定义：ReentrantLock 用它表示重入次数，Semaphore 用它表示剩余许可，CountDownLatch 用它表示剩余计数。

获取锁失败时，线程被包装成 Node 加入队列尾部，然后通过 LockSupport.park 阻塞。释放锁时唤醒头节点的后继，被唤醒的线程重新尝试获取。

AQS 区分独占和共享两种模式。ReentrantLock 是独占，Semaphore 和 CountDownLatch 是共享，共享模式下唤醒会向后传播。

ReentrantLock 比 synchronized 多了三个能力：可中断获取 lockInterruptibly、超时获取 tryLock(long, TimeUnit)、公平锁。公平锁按队列顺序分配，吞吐略低但避免饥饿；非公平锁允许插队，默认是非公平。

公平锁的实现差异只在 tryAcquire 里多了一次 hasQueuedPredecessors 判断。非公平锁性能更好的原因是刚释放锁的线程很可能再次抢到，省掉了唤醒和上下文切换。

Condition 基于 AQS 的条件队列实现，await 把线程移到条件队列并释放锁，signal 把它移回同步队列。一个锁可以创建多个 Condition，实现精确唤醒，这是 synchronized 配合 wait/notify 做不到的，后者只能随机唤醒一个。
