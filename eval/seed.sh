#!/usr/bin/env bash
# 导入全部评测语料（corpus ~ corpus5）并导出片段快照，然后执行：node eval/run-eval.mjs
set -euo pipefail

BASE=${RAG_BASE:-http://localhost:8080}
CONTAINER=${PG_CONTAINER:-rag-postgres}
DB_USER=${PG_USER:-root}
DB_NAME=${PG_DB:-ragkb}
DB_PASS=${PGPASSWORD:-root123}
cd "$(dirname "$0")"

count=0
for dir in corpus corpus2 corpus3 corpus4 corpus5; do
  [ -d "$dir" ] || continue
  for file in "$dir"/*.md; do
    [ -e "$file" ] || continue
    printf '%-38s ' "$file"
    curl -s -X POST "$BASE/api/documents" -F "file=@$file" | head -c 80
    echo
    count=$((count + 1))
  done
done
echo "已导入 $count 个语料文件"

docker exec -e PGPASSWORD="$DB_PASS" "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tA -c "
  SELECT json_agg(json_build_object(
           'noteId', c.note_id,
           'chunkIndex', c.chunk_index,
           'content', c.content))
  FROM note_chunks c
  JOIN notes n ON n.id = c.note_id
  WHERE n.title LIKE 'bench-%' OR n.title LIKE 'hard-%';
" > chunks.json

echo "已导出 chunks.json"