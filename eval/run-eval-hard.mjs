import { readFileSync } from 'node:fs'

const BASE = process.env.RAG_BASE ?? 'http://localhost:8080'
const K = Number(process.env.RAG_K ?? 5)

const chunks = JSON.parse(
  readFileSync(new URL('./chunks.json', import.meta.url), 'utf8'),
)

const questions = [
  { q: 'HashMap 为什么要对 hashCode 做异或移位？', anchor: '右移 16 位再和原值异或' },
  { q: '扩容时怎么把旧链表拆到新数组？', anchor: 'loHead/loTail' },
  { q: '链表长度到 8 就一定要树化吗？', anchor: '千万分之六' },
  { q: 'G1 里多大的对象会被当成大对象？', anchor: '对象大小超过 Region 的一半' },
  { q: 'G1 怎么预测一次回收要停多久？', anchor: '回收成本模型' },
  { q: '什么情况下 G1 会退化成 Full GC？', anchor: '最终退化成 Full GC' },
  { q: '三级缓存里存的为什么是工厂而不是对象？', anchor: 'getEarlyBeanReference' },
  { q: '二级缓存的意义是什么？', anchor: '二级缓存的作用是去重' },
  { q: '原型 Bean 的循环依赖能解决吗？', anchor: 'prototype 作用域的 Bean 也不支持' },
  { q: 'HNSW 的 m 参数控制什么？', anchor: '每个节点在图中保留的双向连接数' },
  { q: 'ef_construction 调大会怎样？', anchor: '建索引时的动态候选列表长度' },
  { q: 'ef_search 是建索引参数还是查询参数？', anchor: '运行时唯一需要调的旋钮' },
  { q: 'IVFFlat 为什么不能提前建索引？', anchor: '必须先有数据再建索引' },
  { q: '布隆过滤器能删除元素吗？', anchor: '不支持删除' },
  { q: '互斥重建缓存用什么命令加锁？', anchor: 'setnx' },
  { q: '雪崩时怎么把过期时间打散？', anchor: '把过期时间摊开' },
  { q: '缓存删除失败怎么补救？', anchor: '订阅 binlog 异步删除' },
  { q: 'REQUIRES_NEW 外层回滚会影响内层吗？', anchor: '外层回滚不影响它' },
  { q: '自调用失效除了拆 Bean 还能怎么解？', anchor: '开启 exposeProxy' },
  { q: '事务里 catch 住异常不抛出会怎样？', anchor: 'setRollbackOnly' },
  { q: 'Xms 和 Xmx 为什么要设成一样？', anchor: '避免堆反复伸缩' },
  { q: '容器里怎么按比例设置堆大小？', anchor: 'MaxRAMPercentage' },
  { q: 'GC 日志用哪个参数开启？', anchor: 'Xlog:gc' },
  { q: 'MySQL 什么情况下行锁会变成表锁？', anchor: '退化成表锁' },
  { q: '死锁检测的参数叫什么？', anchor: 'innodb_deadlock_detect' },
  { q: '间隙锁是为了解决什么问题？', anchor: '防止其他事务插入导致幻读' },
  { q: 'Redis 加锁为什么必须一条命令完成？', anchor: 'NX 保证只有 key 不存在时才设置成功' },
  { q: '解锁为什么必须用 Lua？', anchor: 'Lua 脚本保证原子性' },
  { q: '看门狗多久续期一次？', anchor: '每 10 秒续期一次' },
  { q: 'RedLock 的争议点在哪？', anchor: '依赖时钟假设' },
  { q: '令牌桶为什么允许突发流量？', anchor: '桶有容量上限' },
  { q: '固定窗口计数器在临界点会怎样？', anchor: '临界点会出现两倍流量' },
  { q: '滑动窗口相比固定窗口好在哪？', anchor: '按滑动区间统计' },
  { q: 'Guava RateLimiter 的预热是什么效果？', anchor: '冷启动时先以较低速率放行' },
  { q: '分片键选错会导致什么？', anchor: '数据倾斜' },
  { q: '为什么双倍扩容只迁移一半数据？', anchor: '只迁移一半' },
  { q: '雪花算法的序列号占几位？', anchor: '12 位序列号' },
  { q: 'Kafka 消费时怎么避免用户态拷贝？', anchor: 'sendfile' },
  { q: 'RocketMQ 的事务消息怎么实现？', anchor: '半消息加回查' },
  { q: 'RabbitMQ 的核心优势是什么？', anchor: '交换机支持灵活路由' },
  { q: 'HashMap 的默认初始容量是多少？', anchor: '默认初始容量是 16' },
  { q: 'JDK 7 的 HashMap 并发扩容会导致什么？', anchor: '环形链表' },
  { q: 'ConcurrentHashMap 在 JDK 7 分了多少段？', anchor: '16 个 Segment' },
  { q: '迁移中的桶会被标记成什么？', anchor: 'ForwardingNode' },
  { q: '子线程怎么继承父线程的 ThreadLocal？', anchor: 'InheritableThreadLocal' },
  { q: 'G1 的 Region 大小范围是多少？', anchor: '1 到 32 MB' },
  { q: 'ZGC 从哪个版本转为正式特性？', anchor: 'JDK 15 转为正式特性' },
  { q: 'Spring Boot 从哪个版本开始默认禁止循环依赖？', anchor: '2.6 之后默认禁止' },
  { q: 'NESTED 传播行为靠什么实现？', anchor: '使用数据库保存点实现' },
  { q: 'MANDATORY 传播行为有什么前提？', anchor: '必须已有事务' },
  { q: '缓存空值时 TTL 一般设多长？', anchor: '60 秒' },
  { q: 'HNSW 的 m 默认值是多少？', anchor: '默认 16' },
  { q: 'pgvector 里余弦距离对应哪个 operator class？', anchor: 'vector_cosine_ops' },
  { q: '元空间的上限用哪个参数控制？', anchor: 'MaxMetaspaceSize' },
  { q: '直接内存受什么限制？', anchor: '受物理内存限制' },
  { q: 'Tomcat 怎么做到应用之间隔离？', anchor: 'WebappClassLoader' },
  { q: '偏向锁从哪个版本开始默认关闭？', anchor: 'JDK 15 之后偏向锁默认关闭' },
  { q: 'AQS 的等待队列是什么结构？', anchor: 'CLH 双向队列' },
  { q: '线程池用无界队列有什么风险？', anchor: '任务堆积导致 OOM' },
  { q: '覆盖索引在执行计划里显示什么？', anchor: 'Using index' },
  { q: 'ReadView 里的 m_ids 是什么？', anchor: 'm_ids 是活跃事务列表' },
  { q: 'Kafka 的 min.insync.replicas 建议设多少？', anchor: 'min.insync.replicas 设为 2' },
  { q: 'ETag 相比 Last-Modified 强在哪？', anchor: '精度比时间戳高' },
  { q: 'JWT 的 alg 设成 none 会有什么后果？', anchor: 'alg 设成 none' },
  { q: '多阶段构建能把 Java 镜像降到多大？', anchor: '100 MB 以内' },
  { q: 'ArrayList 扩容是几倍？', anchor: '1.5 倍' },
  { q: 'CMS 并发模式失败会退化成什么？', anchor: 'Serial Old' },
  { q: 'Redis AOF 默认的刷盘策略是什么？', anchor: 'everysec' },
  { q: 'LinkedHashMap 靠哪个方法实现 LRU？', anchor: 'removeEldestEntry' },
  { q: 'Spring AOP 在 Boot 里默认用哪种代理？', anchor: '默认使用 CGLIB' },
]

if (process.argv.includes('--check')) {
  let bad = 0
  for (const item of questions) {
    const n = chunks.filter((c) => c.content.includes(item.anchor)).length
    if (n === 0) {
      bad++
      console.log(`MISS  ${item.q}  [${item.anchor}]`)
    }
  }
  console.log(`共 ${questions.length} 题，未匹配 ${bad} 题`)
  process.exit(0)
}

const key = (item) => `${item.noteId ?? item.id}:${item.chunkIndex}`

const started = Date.now()
let hits = 0
let mrr = 0
let skipped = 0
const misses = []
const details = []

for (const item of questions) {
  const truth = new Set(
    chunks.filter((c) => c.content.includes(item.anchor)).map(key),
  )
  if (truth.size === 0) {
    skipped++
    misses.push({ q: item.q, top: '锚点未匹配，标注失效' })
    continue
  }
  const res = await fetch(
    `${BASE}/api/notes/semantic?q=${encodeURIComponent(item.q)}&k=${K}`,
  )
  if (!res.ok) throw new Error(`检索失败 ${res.status}`)
  const list = await res.json()
  const rank = list.map(key).findIndex((k) => truth.has(k))

  if (rank >= 0) {
    hits++
    mrr += 1 / (rank + 1)
  } else {
    misses.push({
      q: item.q,
      top: list[0] ? `${list[0].title} #${list[0].chunkIndex}` : '无结果',
    })
  }
  details.push({ q: item.q, rank: rank >= 0 ? rank + 1 : 0 })
}

const total = questions.length - skipped
const elapsed = Date.now() - started

console.log(`BASE=${BASE}  片段=${chunks.length}  题数=${total}  跳过=${skipped}`)
console.log(`Hit@${K} = ${(hits / total).toFixed(3)} (${hits}/${total})`)
console.log(`MRR    = ${(mrr / total).toFixed(3)}`)
console.log(`平均单题耗时 = ${Math.round(elapsed / total)} ms`)
console.log(`随机基线 Hit@${K} ≈ ${(K / chunks.length).toFixed(3)}`)
if (misses.length) {
  console.log('未命中：')
  for (const m of misses) console.log(`  - ${m.q} → 首条 ${m.top}`)
}
if (process.env.RAG_VERBOSE === '1') {
  console.log('')
  for (const d of details) console.log(`${d.rank || '✗'}  ${d.q}`)
}
