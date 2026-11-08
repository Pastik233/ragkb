# pgvector 的 HNSW 索引

pgvector 支持两种近似最近邻索引：IVFFlat 和 HNSW。IVFFlat 需要先有数据再建索引，因为要对现有向量做聚类划分；HNSW 可以在空表上建，插入时增量维护图结构，更适合持续写入的场景。

HNSW 的关键参数有两个。m 表示每个节点保留的邻居数量，默认 16，调大能提升召回率但增加内存和写入开销。ef_construction 表示建索引时的候选队列大小，默认 64，调大能让图结构更优，代价是建索引更慢。

查询时的召回率由 ef_search 控制，默认 40，可以在会话级调整：`SET hnsw.ef_search = 100`。它不影响索引结构，只影响单次查询搜索的广度。ef_search 越大召回越高、延迟越大，这是运行时唯一需要调的旋钮。

距离算子必须和索引的 operator class 匹配。`vector_cosine_ops` 对应余弦距离 `<=>`，`vector_l2_ops` 对应欧氏距离 `<->`，`vector_ip_ops` 对应内积 `<#>`。如果索引建的是余弦，查询却用 `<->`，索引不会被使用，会退化成全表扫描。

查询必须带 ORDER BY 和 LIMIT 才能走索引。写成 `ORDER BY embedding <=> :vec LIMIT 10`，不要写成先取全部再在应用层排序。另外 ORDER BY 里要写完整表达式，不要用 SELECT 的别名，否则优化器可能识别不到索引。

带过滤条件的查询要小心。如果 WHERE 条件过滤掉大部分行，HNSW 先取近似结果再过滤，可能凑不满 LIMIT。pgvector 0.8 之后提供迭代扫描，可以设置 `SET hnsw.iterative_scan = relaxed_order` 让索引继续往下找。

索引不是越多越好。每个 HNSW 索引都会常驻内存，写入时也要维护图结构，所以只在真正用于检索的向量列上建索引。
