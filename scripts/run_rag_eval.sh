#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8123/api}"
PAYLOAD_FILE="${2:-scripts/rag_eval_sample.json}"

if [[ ! -f "$PAYLOAD_FILE" ]]; then
  echo "payload file not found: $PAYLOAD_FILE" >&2
  exit 1
fi

RESP="$(curl -sS -X POST "${BASE_URL}/debug/rag-eval/run" \
  -H "Content-Type: application/json" \
  --data-binary "@${PAYLOAD_FILE}")"

if command -v jq >/dev/null 2>&1; then
  echo "$RESP" | jq
else
  echo "$RESP"
fi

