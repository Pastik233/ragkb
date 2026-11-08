import { readFileSync } from 'node:fs'

const chunks = JSON.parse(
  readFileSync(new URL('./chunks-real.json', import.meta.url), 'utf8'),
)

const kw = /转专业|奖学金|绩点|违纪|处分|休学|复学|缓考|重修|选课|学士学位|考勤|请假|助学金|补考|四级|毕业设计|学分|退学|留级|旷课|诚信/

for (const ch of chunks) {
  if (kw.test(ch.content)) {
    const preview = ch.content.replace(/\s+/g, ' ').slice(0, 66)
    console.log(`${ch.chunkIndex} | ${preview}`)
  }
}
