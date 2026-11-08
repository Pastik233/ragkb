import { readFileSync } from 'node:fs'

const BASE = process.env.RAG_BASE ?? 'http://localhost:8080'
const K = Number(process.env.RAG_K ?? 5)

const chunks = JSON.parse(
  readFileSync(new URL('./chunks.json', import.meta.url), 'utf8'),
)

const direct = [
  { q: 'HashMap 什么时候扩容？阈值是多少？', anchor: '扩容阈值等于容量乘以负载因子' },
  { q: 'HashMap 的链表在什么条件下转成红黑树？', anchor: '树化阈值是 8' },
  { q: 'ConcurrentHashMap 在高并发下怎么统计元素个数？', anchor: 'CounterCell 数组' },
  { q: 'ConcurrentHashMap 为什么不允许 null 值？', anchor: '不允许 null 键和 null 值' },
  { q: 'ThreadLocal 用完不 remove 会怎样？', anchor: 'value 仍然被 Entry 强引用' },
  { q: 'G1 怎么控制单次停顿时间？', anchor: 'MaxGCPauseMillis' },
  { q: 'ZGC 的停顿时间能做到什么水平？', anchor: '1 毫秒以内' },
  { q: 'Spring 三级缓存分别存的是什么？', anchor: 'singletonFactories' },
  { q: '同一个类里调用自己的事务方法为什么失效？', anchor: '自调用绕过了代理对象' },
  { q: 'REQUIRES_NEW 和 REQUIRED 有什么区别？', anchor: '挂起当前事务' },
  { q: '热点 key 突然过期导致请求打到数据库，怎么解决？', anchor: '逻辑过期' },
  { q: '大量 key 同时过期怎么办？', anchor: '随机偏移' },
  { q: 'HNSW 索引的 m 参数调大有什么影响？', anchor: '每个节点保留的邻居数量' },
  { q: 'pgvector 查询为什么不能按 SELECT 别名排序？', anchor: '不要用 SELECT 的别名' },
  { q: 'JDK 8 之后方法区用什么实现？', anchor: '元空间' },
  { q: '什么情况下双亲委派会被打破？', anchor: '线程上下文类加载器' },
  { q: 'synchronized 的锁有哪几种状态？', anchor: '无锁、偏向锁、轻量级锁、重量级锁' },
  { q: 'AQS 的 state 在 ReentrantLock 里代表什么？', anchor: '重入次数' },
  { q: '线程池提交任务后是先扩容还是先入队？', anchor: '核心线程未满就新建核心线程' },
  { q: '联合索引 (a,b,c) 单独查 b 能用上索引吗？', anchor: '最左前缀原则' },
  { q: 'MVCC 怎么判断一个版本对当前事务可见？', anchor: 'min_trx_id' },
  { q: 'Kafka 生产者怎么配置才能不丢消息？', anchor: 'acks=all' },
  { q: 'HTTP 的 no-cache 和 no-store 有什么不同？', anchor: 'no-store 才是完全不存储' },
  { q: 'JWT 的 payload 能放密码吗？', anchor: '不是加密' },
  { q: 'Docker 构建时指令顺序为什么影响缓存命中？', anchor: '变化频率低的放前面' },
  { q: '线上 CPU 打满，怎么定位到具体代码？', anchor: 'nid 是十六进制' },
]

const oblique = [
  { q: '我往 Map 里放第 13 个元素时它突然变慢了，为什么？', anchor: '扩容阈值等于容量乘以负载因子' },
  { q: '桶里的节点越挂越多，JDK 8 会怎么处理？', anchor: '树化阈值是 8' },
  { q: '并发容器不加全局锁，怎么知道现在有多少个元素？', anchor: 'CounterCell 数组' },
  { q: '为什么这个并发 Map 连 null 都不能存？', anchor: '不允许 null 键和 null 值' },
  { q: '线程池里的线程为什么带着上一个请求的数据？', anchor: 'value 仍然被 Entry 强引用' },
  { q: '服务每隔一会儿卡一下，日志里停顿约 200 毫秒，可能用的哪个回收器？', anchor: 'MaxGCPauseMillis' },
  { q: '哪种回收器的停顿时间不随堆变大而增加？', anchor: '1 毫秒以内' },
  { q: '两个 Bean 互相依赖，为什么用字段注入不会死循环？', anchor: 'singletonFactories' },
  { q: '在同一个类里给方法加事务注解，为什么没生效？', anchor: '自调用绕过了代理对象' },
  { q: '想让日志在业务回滚后依然保留，事务该怎么配？', anchor: '挂起当前事务' },
  { q: '有人拿不存在的 id 疯狂请求，数据库压力很大，怎么办？', anchor: '逻辑过期' },
  { q: '为什么凌晨整点缓存会集体失效？', anchor: '随机偏移' },
  { q: '向量索引里那个控制邻居数量的参数，调大了有什么代价？', anchor: '每个节点保留的邻居数量' },
  { q: '为什么把排序改成别名之后，查询突然变慢了？', anchor: '不要用 SELECT 的别名' },
  { q: '永久代后来被什么取代了？', anchor: '元空间' },
  { q: '为什么自己写一个 java.lang.String 不会生效？', anchor: '线程上下文类加载器' },
  { q: '锁膨胀的顺序是怎样的？', anchor: '无锁、偏向锁、轻量级锁、重量级锁' },
  { q: '公平锁和非公平锁在代码上差在哪一行？', anchor: '重入次数' },
  { q: '队列还没满，为什么线程数不往上加？', anchor: '核心线程未满就新建核心线程' },
  { q: '为什么 where b = 1 用不上索引？', anchor: '最左前缀原则' },
  { q: '同一个事务里两次查询结果不一样，可能是什么隔离级别？', anchor: 'min_trx_id' },
  { q: '消息刚发出去 leader 就挂了，怎么保证不丢？', anchor: 'acks=all' },
  { q: '为什么改一行代码要重新装一遍依赖？', anchor: '变化频率低的放前面' },
  { q: 'CPU 打满时怎么把线程号对上具体代码？', anchor: 'nid 是十六进制' },
]

const key = (item) => `${item.noteId ?? item.id}:${item.chunkIndex}`

async function search(question) {
  const res = await fetch(
    `${BASE}/api/notes/semantic?q=${encodeURIComponent(question)}&k=${K}`,
  )
  if (!res.ok) throw new Error(`检索失败 ${res.status}`)
  return res.json()
}

async function evaluate(name, items, verbose) {
  let hits = 0
  let mrr = 0
  let skipped = 0
  const misses = []

  for (const item of items) {
    const truth = new Set(
      chunks.filter((c) => c.content.includes(item.anchor)).map(key),
    )
    if (truth.size === 0) {
      skipped++
      continue
    }
    const list = await search(item.q)
    const rank = list.map(key).findIndex((k) => truth.has(k))
    if (rank >= 0) {
      hits++
      mrr += 1 / (rank + 1)
    } else {
      misses.push({ q: item.q, top: list[0] ? `${list[0].title} #${list[0].chunkIndex}` : '无结果' })
    }
    if (verbose) {
      console.log(`${rank >= 0 ? '✓' : '✗'} 名次=${rank >= 0 ? rank + 1 : '-'}  ${item.q}`)
    }
  }

  const total = items.length - skipped
  console.log(`\n【${name}】题目 ${total} 道，跳过 ${skipped}`)
  console.log(`  Hit@${K} = ${(hits / total).toFixed(3)} (${hits}/${total})`)
  console.log(`  MRR    = ${(mrr / total).toFixed(3)}`)
  if (misses.length) {
    console.log('  未命中：')
    for (const m of misses) console.log(`    - ${m.q}  →  首条 ${m.top}`)
  }
  return { hits, total, mrr }
}

await evaluate('直接提问', direct, false)
await evaluate('口语化提问', oblique, true)

console.log(`\n语料片段数 ${chunks.length}，随机基线 Hit@${K} ≈ ${(K / chunks.length).toFixed(3)}`)
