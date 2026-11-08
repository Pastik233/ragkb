# MVCC 与间隙锁

InnoDB 的 MVCC 靠三个东西实现：隐藏字段、undo log、ReadView。

每行有两个隐藏列，DB_TRX_ID 记录最后修改它的事务 ID，DB_ROLL_PTR 指向 undo log 里的历史版本。查询时沿着版本链往回找，直到找到对当前 ReadView 可见的版本。

ReadView 里有四个关键字段：m_ids 是活跃事务列表，min_trx_id 是其中最小值，max_trx_id 是下一个要分配的事务 ID，creator_trx_id 是创建者自己。判断可见性的规则是：小于 min_trx_id 可见，大于等于 max_trx_id 不可见，在两者之间则看是否在 m_ids 里。

RC 和 RR 的唯一区别是 ReadView 的创建时机。RC 每次 select 都新建一个，所以能看到别人提交的新数据；RR 只在第一次 select 时创建并复用，所以整个事务看到的是同一个快照。这也解释了为什么 RR 下不能读到别的事务的提交。

MVCC 只解决快照读，也就是普通 select。当前读包括 select for update、select lock in share mode、update、delete，它们读的是最新版本，靠锁保证一致。

间隙锁锁的是索引记录之间的空隙，目的是防止幻读。在 RR 隔离级别下，范围查询会加 next-key lock，等于记录锁加间隙锁。间隙锁之间不冲突，但会阻塞插入。

RC 级别下间隙锁基本被关闭，只保留记录锁和外键约束需要的部分，所以并发插入性能更好。
