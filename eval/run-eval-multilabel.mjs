import { readFileSync } from 'node:fs'

const BASE = process.env.RAG_BASE ?? 'http://localhost:8080'
const K = Number(process.env.RAG_K ?? 5)

const chunks = JSON.parse(
  readFileSync(new URL('./chunks.json', import.meta.url), 'utf8'),
)

// 每题标 2~4 个相关片段；锚点落在哪个片段，哪个片段就算相关
const questions = [
  { q: 'HashMap 扩容是怎么做的？', anchors: ['扩容阈值等于容量乘以负载因子', 'loHead/loTail'] },
  { q: 'G1 怎么控制回收停顿？', anchors: ['MaxGCPauseMillis', '回收成本模型'] },
  { q: 'Spring 怎么解决循环依赖？', anchors: ['singletonFactories', 'getEarlyBeanReference', '@Lazy'] },
  { q: 'Spring 的事务传播行为有哪些？', anchors: ['挂起当前事务', '使用数据库保存点实现', '必须已有事务'] },
  { q: '缓存穿透、击穿、雪崩分别怎么处理？', anchors: ['布隆', '逻辑过期', '把过期时间摊开'] },
  { q: '向量索引有哪些参数可以调？', anchors: ['每个节点在图中保留的双向连接数', '建索引时的动态候选列表长度', '运行时唯一需要调的旋钮', '必须先有数据再建索引'] },
  { q: 'JVM 内存和堆大小怎么配置？', anchors: ['元空间', 'MaxRAMPercentage', '避免堆反复伸缩'] },
  { q: 'MySQL 的锁有哪些坑？', anchors: ['退化成表锁', 'innodb_deadlock_detect', '防止其他事务插入导致幻读'] },
  { q: 'MVCC 和隔离级别是怎么配合的？', anchors: ['m_ids 是活跃事务列表', 'readOnly'] },
  { q: '消息队列怎么保证可靠？', anchors: ['acks=all', '零拷贝', '半消息加回查'] },
  { q: 'Redis 分布式锁要注意什么？', anchors: ['Lua 脚本保证原子性', '每 10 秒续期一次', '临时顺序节点'] },
  { q: '限流算法怎么选？', anchors: ['桶有容量上限', '临界点会出现两倍流量', '冷启动时先以较低速率放行'] },
  { q: '分库分表有哪些难点？', anchors: ['数据倾斜', '只迁移一半', '跨库 join'] },
  { q: '接口幂等有哪些实现方式？', anchors: ['唯一键方案最直接', 'Token 方案用于防止重复提交', '乐观锁用版本号'] },
  { q: 'HTTP 协议这些年解决了什么问题？', anchors: ['二进制分帧', 'TCP 层队头阻塞', '流与流之间独立重传', 'no-store 才是完全不存储'] },
  { q: '服务雪崩怎么防？', anchors: ['熔断器有三种状态', '舱壁隔离', '超时是最后一道防线'] },
  { q: '线上问题怎么定位？', anchors: ['MDC', 'W3C Trace Context', '采样率'] },
  { q: '测试应该怎么分层？', anchors: ['单元测试要隔离外部依赖', 'Testcontainers', '@WebMvcTest'] },
  { q: '分布式事务有哪些方案？', anchors: ['undo log', 'redo log', '两阶段提交'] },
  { q: 'ArrayList 和 LinkedHashMap 各适合什么场景？', anchors: ['1.5 倍', 'removeEldestEntry'] },
]

if (process.argv.includes('--check')) {
  let bad = 0
  for (const item of questions) {
    const n = new Set(
      chunks.filter((c) => item.anchors.some((a) => c.content.includes(a))).map(
        (c) => `${c.noteId}:${c.chunkIndex}`,
      ),
    ).size
    if (n === 0) {
      bad++
      console.log(`MISS  ${item.q}  ${JSON.stringify(item.anchors)}`)
    }
  }
  console.log(`共 ${questions.length} 题，未匹配 ${bad} 题`)
  process.exit(0)
}

const key = (item) => `${item.noteId ?? item.id}:${item.chunkIndex}`

let sumRecall = 0
let sumMrr = 0
let sumHit = 0
let totalRelevant = 0
const rows = []

for (const item of questions) {
  const relevant = new Set(
    chunks.filter((c) => item.anchors.some((a) => c.content.includes(a))).map(key),
  )
  const res = await fetch(
    `${BASE}/api/notes/semantic?q=${encodeURIComponent(item.q)}&k=${K}`,
  )
  const list = await res.json()
  const returned = list.map(key)
  const top = returned.slice(0, K)

  const hits = top.filter((k) => relevant.has(k)).length
  const recall = relevant.size === 0 ? 0 : hits / relevant.size
  const firstRank = top.findIndex((k) => relevant.has(k))
  const mrr = firstRank >= 0 ? 1 / (firstRank + 1) : 0

  sumRecall += recall
  sumMrr += mrr
  sumHit += firstRank >= 0 ? 1 : 0
  totalRelevant += relevant.size
  rows.push({ q: item.q, relevant: relevant.size, hits, recall, rank: firstRank + 1 })
}

const n = questions.length
console.log(`BASE=${BASE}  片段=${chunks.length}  题数=${n}  平均相关片段=${(totalRelevant / n).toFixed(1)}`)
console.log(`Recall@${K} = ${(sumRecall / n).toFixed(3)}`)
console.log(`Hit@${K}    = ${(sumHit / n).toFixed(3)}`)
console.log(`MRR        = ${(sumMrr / n).toFixed(3)}`)
if (process.env.RAG_VERBOSE === '1') {
  console.log('')
  for (const r of rows) {
    console.log(`recall=${r.recall.toFixed(2)} (${r.hits}/${r.relevant}) rank=${r.rank || '-'}  ${r.q}`)
  }
}
