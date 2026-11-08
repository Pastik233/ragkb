# Kafka 如何保证消息不丢

消息在三个环节都可能丢：生产者、Broker、消费者。

生产者端要设置 acks=all，让所有 ISR 副本都确认才返回成功。acks=1 只等 leader 写入，leader 挂了就可能丢；acks=0 完全不等待。同时要开启重试并把 max.in.flight.requests.per.connection 设为 1，否则重试可能导致乱序。Kafka 0.11 之后有幂等生产者，设置 enable.idempotence=true 可以在保证顺序的同时允许更大的并发窗口，配合事务还能实现跨分区的 exactly-once。

Broker 端依赖副本机制。replication.factor 至少设为 3，min.insync.replicas 设为 2，这样 acks=all 时至少要两个副本写入成功。还要关闭 unclean.leader.election.enable，否则落后的副本可能被选为 leader，直接造成数据丢失。

消费者端要关闭自动提交，改成处理完业务逻辑再手动提交 offset。自动提交是在 poll 时提交上一批的 offset，如果处理到一半崩溃，这批消息就丢了。

重复消费无法完全避免，所以消费端要做幂等：用业务唯一键去重，或者用数据库的唯一索引兜底。这也是为什么 Kafka 承诺的是 at-least-once 而不是 exactly-once。

顺序性只在单分区内保证，需要全局有序时只能用一个分区，代价是吞吐受限。
