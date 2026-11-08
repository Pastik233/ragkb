import { readFileSync } from 'node:fs'

const BASE = process.env.RAG_BASE ?? 'http://localhost:8080'
const K = Number(process.env.RAG_K ?? 5)

const chunks = JSON.parse(
  readFileSync(new URL('./chunks.json', import.meta.url), 'utf8'),
)

const questions = [
  { q: 'InitiatingHeapOccupancyPercent 这个参数控制什么？', anchor: 'InitiatingHeapOccupancyPercent' },
  { q: 'Xlog 系列参数怎么配 GC 日志？', anchor: 'Xlog:gc' },
  { q: 'setnx 在缓存重建里起什么作用？', anchor: 'setnx' },
  { q: 'MaxRAMPercentage 在容器里怎么用？', anchor: 'MaxRAMPercentage' },
  { q: 'innodb_deadlock_detect 默认是开的吗？', anchor: 'innodb_deadlock_detect' },
  { q: 'loHead 和 hiHead 是干什么的？', anchor: 'loHead/loTail' },
  { q: 'G1HeapRegionSize 怎么设置？', anchor: 'G1HeapRegionSize' },
  { q: 'getEarlyBeanReference 什么时候被调用？', anchor: 'getEarlyBeanReference' },
  { q: 'hnsw.ef_search 怎么调？', anchor: '运行时唯一需要调的旋钮' },
  { q: 'AopContext.currentProxy 解决什么问题？', anchor: '开启 exposeProxy' },
  { q: 'hasQueuedPredecessors 判断什么？', anchor: 'hasQueuedPredecessors' },
  { q: 'DB_TRX_ID 记录的是什么东西？', anchor: 'DB_TRX_ID' },
  { q: 'CounterCell 解决什么竞争？', anchor: 'CounterCell' },
  { q: 'TransmittableThreadLocal 解决什么场景？', anchor: 'TransmittableThreadLocal' },
  { q: 'WebappClassLoader 的作用是什么？', anchor: 'WebappClassLoader' },
  { q: 'MetaspaceSize 是元空间的初始大小吗？', anchor: '首次触发元空间 GC 的阈值' },
  { q: 'min.insync.replicas 一般配置成多少？', anchor: 'min.insync.replicas' },
  { q: 'removeEldestEntry 返回 true 会怎样？', anchor: 'removeEldestEntry' },
  { q: '执行计划里的 Using index 表示什么？', anchor: 'Using index' },
  { q: 'ForwardingNode 标记有什么作用？', anchor: 'ForwardingNode' },
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

let hits = 0
let mrr = 0
const misses = []

for (const item of questions) {
  const truth = new Set(
    chunks.filter((c) => c.content.includes(item.anchor)).map(key),
  )
  if (truth.size === 0) {
    misses.push({ q: item.q, top: '锚点未匹配' })
    continue
  }
  const res = await fetch(
    `${BASE}/api/notes/semantic?q=${encodeURIComponent(item.q)}&k=${K}`,
  )
  const list = await res.json()
  const rank = list.map(key).findIndex((k) => truth.has(k))
  if (rank >= 0) {
    hits++
    mrr += 1 / (rank + 1)
  } else {
    misses.push({ q: item.q, top: list[0] ? `${list[0].title} #${list[0].chunkIndex}` : '无结果' })
  }
}

const total = questions.length
console.log(`BASE=${BASE}  标识符题 ${total} 道`)
console.log(`Hit@${K} = ${(hits / total).toFixed(3)} (${hits}/${total})`)
console.log(`MRR    = ${(mrr / total).toFixed(3)}`)
for (const m of misses) console.log(`  ✗ ${m.q} → 首条 ${m.top}`)
