import { useCallback, useEffect, useMemo, useState } from 'react'

async function api(path, options = {}) {
  const res = await fetch(path, options)
  if (!res.ok) {
    let message = `请求失败（${res.status}）`
    try {
      const body = await res.json()
      message = body.message || body.error || message
    } catch {
      // 响应不是 JSON，保留默认信息
    }
    throw new Error(message)
  }
  if (res.status === 204) return null
  return res.json()
}

function fmtTime(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleString('zh-CN', { hour12: false })
}

/**
 * 列表接口只回 200 字预览，展开时再按需拉整篇正文：
 * 一篇上万字的文档不至于让 GET /api/notes 把响应撑成几 MB。
 */
function NoteBody({ id, preview, textLength }) {
  const [open, setOpen] = useState(false)
  const [full, setFull] = useState(null)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState('')

  const long = (textLength ?? 0) > (preview?.length ?? 0)

  async function toggle() {
    if (open) {
      setOpen(false)
      return
    }
    setOpen(true)
    if (full !== null || loading) return
    setLoading(true)
    setFailed('')
    try {
      const note = await api(`/api/notes/${id}`)
      setFull(note.content ?? '')
    } catch (err) {
      setFailed(err.message)
    } finally {
      setLoading(false)
    }
  }

  let text = preview
  if (open) {
    if (loading) text = '正在加载正文……'
    else if (failed) text = `正文加载失败：${failed}`
    else text = full
  }

  return (
    <p className="note-body">
      {text}
      {long && (
        <button className="link" onClick={toggle}>
          {open ? '收起' : '展开全文'}
        </button>
      )}
    </p>
  )
}

export default function App() {
  const [notes, setNotes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('')

  const [tab, setTab] = useState('ask')

  const [question, setQuestion] = useState('')
  const [asking, setAsking] = useState(false)
  const [reply, setReply] = useState(null)

  const [query, setQuery] = useState('')
  const [searching, setSearching] = useState(false)
  const [hits, setHits] = useState(null)

  const [uploading, setUploading] = useState(false)
  const [uploadInfo, setUploadInfo] = useState('')

  const [draft, setDraft] = useState({ title: '', content: '' })
  const [creating, setCreating] = useState(false)

  const loadNotes = useCallback(async () => {
    setError('')
    try {
      setNotes(await api('/api/notes'))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadNotes()
  }, [loadNotes])

  const visibleNotes = useMemo(() => {
    const key = filter.trim().toLowerCase()
    if (!key) return notes
    return notes.filter(
      (note) =>
        note.title?.toLowerCase().includes(key) ||
        note.preview?.toLowerCase().includes(key),
    )
  }, [notes, filter])

  async function handleAsk(event) {
    event.preventDefault()
    const value = question.trim()
    if (!value) return
    setAsking(true)
    setError('')
    setReply(null)
    try {
      setReply(
        await api('/api/ask', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ q: value, k: 5 }),
        }),
      )
    } catch (err) {
      setError(err.message)
    } finally {
      setAsking(false)
    }
  }

  async function handleSearch(event) {
    event.preventDefault()
    const value = query.trim()
    if (!value) return
    setSearching(true)
    setError('')
    try {
      setHits(await api(`/api/notes/semantic?q=${encodeURIComponent(value)}&k=8`))
    } catch (err) {
      setError(err.message)
    } finally {
      setSearching(false)
    }
  }

  async function handleUpload(event) {
    const file = event.target.files?.[0]
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    setUploading(true)
    setError('')
    setUploadInfo('')
    try {
      const note = await api('/api/documents', { method: 'POST', body: form })
      setUploadInfo(`已导入《${note.title}》，${note.textLength} 字，状态 ${note.indexStatus}`)
      await loadNotes()
    } catch (err) {
      setError(err.message)
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  async function handleCreate(event) {
    event.preventDefault()
    if (!draft.title.trim()) return
    setCreating(true)
    setError('')
    try {
      await api('/api/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(draft),
      })
      setDraft({ title: '', content: '' })
      await loadNotes()
    } catch (err) {
      setError(err.message)
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('删除这条笔记及其全部片段？')) return
    try {
      await api(`/api/notes/${id}`, { method: 'DELETE' })
      setHits(null)
      setReply(null)
      await loadNotes()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <h1>企业文档 RAG 问答系统</h1>
          <p className="muted">Tika 解析 · 递归切片 · 混合检索 + 重排</p>
        </div>
        <span className="pill">{notes.length} 篇笔记</span>
      </header>

      {error && (
        <div className="alert">
          <span>{error}</span>
          <button className="link" onClick={() => setError('')}>
            关闭
          </button>
        </div>
      )}

      <div className="layout">
        <aside className="sidebar">
          <section className="card">
            <h2>导入文档</h2>
            <label className={`dropzone ${uploading ? 'busy' : ''}`}>
              <input type="file" onChange={handleUpload} disabled={uploading} hidden />
              <strong>{uploading ? '解析并切片中……' : '点击选择文件'}</strong>
              <span className="muted">PDF / Word / Excel / PPT / Markdown，≤ 20 MB</span>
            </label>
            {uploadInfo && <p className="ok">{uploadInfo}</p>}
          </section>

          <section className="card">
            <h2>新建笔记</h2>
            <form className="stack" onSubmit={handleCreate}>
              <input
                value={draft.title}
                onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                placeholder="标题"
              />
              <textarea
                value={draft.content}
                onChange={(e) => setDraft({ ...draft, content: e.target.value })}
                placeholder="正文，支持 Markdown"
                rows={3}
              />
              <button disabled={creating || !draft.title.trim()}>
                {creating ? '保存并切片中…' : '保存并切片'}
              </button>
            </form>
          </section>

          <section className="card list-card">
            <div className="card-head">
              <h2>笔记</h2>
              <span className="muted">{visibleNotes.length}</span>
            </div>
            <input
              className="filter"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="按标题或正文过滤"
            />
            {loading ? (
              <p className="muted">加载中……</p>
            ) : visibleNotes.length === 0 ? (
              <p className="muted">没有匹配的笔记。</p>
            ) : (
              <ul className="list">
                {visibleNotes.map((note) => (
                  <li key={note.id} className="note">
                    <div className="note-head">
                      <strong title={note.title}>{note.title}</strong>
                      <button
                        className="link danger"
                        onClick={() => handleDelete(note.id)}
                      >
                        删除
                      </button>
                    </div>
                    <NoteBody
                      id={note.id}
                      preview={note.preview}
                      textLength={note.textLength}
                    />
                    <div className="note-meta">
                      {note.indexStatus && (
                        <span className={`status ${note.indexStatus.toLowerCase()}`}>
                          {note.indexStatus}
                        </span>
                      )}
                      {note.sourceType === 'FILE' && <span>文档</span>}
                      {note.createdAt && <span>{fmtTime(note.createdAt)}</span>}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>

        <main className="workspace">
          <div className="tabs">
            <button
              className={tab === 'ask' ? 'active' : ''}
              onClick={() => setTab('ask')}
            >
              问答
            </button>
            <button
              className={tab === 'search' ? 'active' : ''}
              onClick={() => setTab('search')}
            >
              语义检索
            </button>
          </div>

          {tab === 'ask' ? (
            <section className="panel">
              <form className="ask-form" onSubmit={handleAsk}>
                <input
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  placeholder="问点什么，例如：转专业需要什么条件？"
                />
                <button disabled={asking}>{asking ? '检索并生成中…' : '提问'}</button>
              </form>

              {!reply && !asking && (
                <p className="hint">
                  先用混合检索找出相关片段，再让模型只依据这些片段作答；答案里的【1】【2】对应下方引用。
                </p>
              )}

              {asking && <p className="muted">正在检索并生成，通常 1～3 秒……</p>}

              {reply && (
                <>
                  <div className="answer">{reply.answer}</div>
                  {reply.sources?.length > 0 && (
                    <ol className="sources">
                      {reply.sources.map((source, index) => (
                        <li key={`${source.id}-${source.chunkIndex}-${index}`}>
                          <div className="src-head">
                            <span className="badge">[{index + 1}]</span>
                            <strong>{source.title}</strong>
                            <span className="muted">
                              片段 {source.chunkIndex} · 距离{' '}
                              {Number(source.distance).toFixed(4)}
                            </span>
                          </div>
                          <p>{source.content}</p>
                        </li>
                      ))}
                    </ol>
                  )}
                </>
              )}
            </section>
          ) : (
            <section className="panel">
              <form className="ask-form" onSubmit={handleSearch}>
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="用一句话描述要找的内容，例如：奖学金评定条件"
                />
                <button disabled={searching}>{searching ? '检索中…' : '检索'}</button>
              </form>

              {!hits && !searching && (
                <p className="hint">
                  只做检索、不调用模型，返回最相关的 8 个片段，适合快速定位原文后自己判断。
                </p>
              )}

              {hits && hits.length === 0 && <p className="muted">没有匹配的片段。</p>}

              {hits && hits.length > 0 && (
                <ul className="list hits">
                  {hits.map((hit, index) => (
                    <li key={`${hit.id}-${hit.chunkIndex}-${index}`} className="note">
                      <div className="note-head">
                        <strong>{hit.title}</strong>
                        <span className="muted">
                          片段 {hit.chunkIndex} · 距离 {Number(hit.distance).toFixed(4)}
                        </span>
                      </div>
                      <p className="note-body">{hit.content}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </main>
      </div>
    </div>
  )
}
