#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------
# 一键验证本地 PostgreSQL + pgvector + 项目记忆写入链路
# 默认连接参数与 application.yml 保持一致，可通过环境变量覆盖
# ---------------------------------------------------------
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-postgres}"
PGUSER="${PGUSER:-bytedance}"
PGPASSWORD="${PGPASSWORD:-123456}"
export PGPASSWORD

APP_URL="${APP_URL:-http://localhost:8123/api}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
SQL_FILE="${SQL_FILE:-docs/sql/memory_v2_init.sql}"
if [[ "${SQL_FILE}" != /* ]]; then
  SQL_FILE="${PROJECT_ROOT}/${SQL_FILE}"
fi

CONV_ID="${CONV_ID:-mem_demo_$(date +%s)}"
TOP_K="${TOP_K:-5}"
TURNS="${TURNS:-12}"
WAIT_MS="${WAIT_MS:-800}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] 缺少命令: $cmd"
    exit 1
  fi
}

run_psql() {
  psql \
    "host=${PGHOST} port=${PGPORT} dbname=${PGDATABASE} user=${PGUSER}" \
    -v ON_ERROR_STOP=1 \
    "$@"
}

print_step() {
  echo
  echo "========================================================="
  echo "$1"
  echo "========================================================="
}

require_cmd psql
require_cmd curl

print_step "Step 1/4: 初始化 memory 表结构"
if [[ ! -f "${SQL_FILE}" ]]; then
  echo "[ERROR] SQL 文件不存在: ${SQL_FILE}"
  exit 1
fi
run_psql -f "${SQL_FILE}"
echo "[OK] 已执行 ${SQL_FILE}"

print_step "Step 2/4: 直写 conversation_memory_store 测试数据"
run_psql -c "
INSERT INTO conversation_memory_store (content, metadata, embedding)
VALUES (
  '用户: 我喜欢西湖附近约会，预算300',
  '{\"conversation_id\":\"${CONV_ID}\",\"memory_type\":\"conversation\",\"user_id\":\"u001\"}'::json,
  ('[' || array_to_string(array_fill(0.001::real, ARRAY[1536]), ',') || ']')::vector(1536)
);
"

run_psql -c "
SELECT id, metadata::jsonb->>'conversation_id' AS conversation_id, left(content, 40) AS preview
FROM conversation_memory_store
WHERE metadata::jsonb->>'conversation_id' = '${CONV_ID}'
ORDER BY id DESC
LIMIT 5;
"

print_step "Step 3/4: 通过应用调试接口触发三层记忆写入（可选）"
SEED_PAYLOAD=$(cat <<EOF
{"conversationId":"${CONV_ID}","turns":${TURNS},"waitMs":${WAIT_MS},"query":"西湖 预算 纪念日","topK":${TOP_K}}
EOF
)

if curl -sf "${APP_URL}/debug/memory/window?conversationId=${CONV_ID}" >/dev/null 2>&1; then
  echo "[INFO] 检测到调试接口可用，开始 seed..."
  curl -sS -X POST "${APP_URL}/debug/memory/seed" \
    -H "Content-Type: application/json" \
    -d "${SEED_PAYLOAD}" \
    | sed -e 's/\\n/\n/g'
else
  echo "[WARN] 调试接口不可用，跳过 seed。"
  echo "      需启动应用并开启：--app.debug.memory.enabled=true --app.memory.vector.enabled=true --app.memory.vector.store=pgvector"
fi

print_step "Step 4/4: 查看应用侧 raw 向量数据（可选）"
if curl -sf "${APP_URL}/debug/memory/vector/raw?conversationId=${CONV_ID}&limit=20" >/dev/null 2>&1; then
  curl -sS "${APP_URL}/debug/memory/vector/raw?conversationId=${CONV_ID}&limit=20" | sed -e 's/\\n/\n/g'
else
  echo "[WARN] raw 接口不可用，跳过。"
fi

echo
echo "[DONE] 记忆写入验证完成，conversationId=${CONV_ID}"
echo "       可手动执行 SQL 检查："
echo "       SELECT count(*) FROM conversation_memory_store WHERE metadata::jsonb->>'conversation_id'='${CONV_ID}';"
