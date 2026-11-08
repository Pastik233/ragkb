import { readFileSync } from 'node:fs'

const BASE = process.env.RAG_BASE ?? 'http://localhost:8080'
const K = Number(process.env.RAG_K ?? 5)

const chunks = JSON.parse(
  readFileSync(new URL('./chunks-real.json', import.meta.url), 'utf8'),
)

const questions = [
  { q: '哪些学生不能申请转专业？', anchors: ['新生入学未满一学期的', '本科四年级（含四年级）以上'] },
  { q: '大二学生转专业有什么限制？', anchors: ['专业接收大二年级学生不多'] },
  { q: '转专业获批后还能反悔吗？', anchors: ['获准转专业后不得放弃'] },
  { q: '休学期间学籍怎么处理？', anchors: ['应为其保留学籍'] },
  { q: '什么情况下不予复学？', anchors: ['申请复学经复查不合格'] },
  { q: '跨校联合培养期间学籍怎么算？', anchors: ['跨校联合培养项目'] },
  { q: '旷考会怎么处理？', anchors: ['凡旷考的学生'] },
  { q: '课程考核不合格怎么办？', anchors: ['补考重修制'] },
  { q: '实验或作业没完成能参加考核吗？', anchors: ['取消其考核资格'] },
  { q: '请假超过 3 天需要谁批准？', anchors: ['学院教学院长批准'] },
  { q: '未经请假缺课怎么处理？', anchors: ['以旷课论处'] },
  { q: '使用手机等设备作弊怎么处分？', anchors: ['使用通讯设备或其他器材作弊'] },
  { q: '对处分决定不服，多久有复查结果？', anchors: ['15 日内作出复查结论'] },
  { q: '辅修专业总共要修多少学分？', anchors: ['超过 48学分'] },
  { q: '辅修办法适用于哪一级学生？', anchors: ['适用于 2025 级及以后'] },
  { q: '学士学位名单是怎么产生的？', anchors: ['授予学士学位的建议名单'] },
  { q: '选课人数超过容量怎么办？', anchors: ['超容筛选'] },
  { q: '英语分级考试按什么分班？', anchors: ['排名前20%'] },
  { q: '成绩中等的学生修哪个英语模块？', anchors: ['成绩排名前 60%-95%'] },
  { q: 'CET-4 成绩不理想下一学期学什么？', anchors: ['成绩低于 425分'] },
  { q: '在校学习年限最多几年？', anchors: ['不得超过 6年'] },
  { q: '推免生的 GPA 怎么计算？', anchors: ['平均学分绩点(GPA)'] },
  { q: '综合测评总分怎么构成？', anchors: ['F=E×20%+Z×65%+T×15%'] },
  { q: '校长奖学金什么时候评选？', anchors: ['第八学期开学后八周内'] },
  { q: '毕业论文用外文撰写有什么要求？', anchors: ['1000字的详细摘要'] },
  { q: '实习缺勤多少学时不能参加考核？', anchors: ['累计超过三分之一实践总学时'] },
  { q: '学业预警是干什么的？', anchors: ['学业预警'] },
  { q: '学生证坐火车有什么优惠限制？', anchors: ['四个单程'] },
  { q: '转学和转专业分别走什么程序？', anchors: ['转学申请程序', '获准转专业后不得放弃'] },
  { q: '休学和联合培养期间学籍都怎么处理？', anchors: ['应为其保留学籍', '跨校联合培养项目'] },
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

const started = Date.now()
let sumRecall = 0
let sumMrr = 0
let sumHit = 0
let totalRelevant = 0
const misses = []
const details = []

for (const item of questions) {
  const relevant = new Set(
    chunks.filter((c) => item.anchors.some((a) => c.content.includes(a))).map(key),
  )
  const res = await fetch(
    `${BASE}/api/notes/semantic?q=${encodeURIComponent(item.q)}&k=${K}`,
  )
  if (!res.ok) throw new Error(`检索失败 ${res.status}`)
  const list = await res.json()
  const top = list.map(key).slice(0, K)

  const hits = top.filter((k) => relevant.has(k)).length
  const recall = hits / relevant.size
  const firstRank = top.findIndex((k) => relevant.has(k))
  const mrr = firstRank >= 0 ? 1 / (firstRank + 1) : 0

  sumRecall += recall
  sumMrr += mrr
  sumHit += firstRank >= 0 ? 1 : 0
  totalRelevant += relevant.size
  details.push({ q: item.q, recall, rank: firstRank + 1 })
  if (recall < 1) {
    misses.push({ q: item.q, top: list[0] ? `${list[0].title.slice(0, 12)} #${list[0].chunkIndex}` : '无结果', hits, rel: relevant.size })
  }
}

const n = questions.length
const elapsed = Date.now() - started

console.log(`BASE=${BASE}  片段=${chunks.length}  题数=${n}  平均相关片段=${(totalRelevant / n).toFixed(1)}`)
console.log(`Recall@${K} = ${(sumRecall / n).toFixed(3)}`)
console.log(`Hit@${K}    = ${(sumHit / n).toFixed(3)}`)
console.log(`MRR        = ${(sumMrr / n).toFixed(3)}`)
console.log(`平均单题耗时 = ${Math.round(elapsed / n)} ms`)
console.log(`随机基线 Hit@${K} ≈ ${(K / chunks.length).toFixed(3)}`)
if (process.env.RAG_VERBOSE === '1') {
  console.log('')
  for (const d of details) console.log(`recall=${d.recall.toFixed(2)} rank=${d.rank}  ${d.q}`)
} else if (misses.length) {
  console.log('未满分：')
  for (const m of misses) console.log(`  - ${m.q} → ${m.hits}/${m.rel}，首条 ${m.top}`)
}
