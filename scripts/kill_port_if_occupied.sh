#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-}"

if [[ -z "${PORT}" ]]; then
  echo "Usage: $0 <port>" >&2
  exit 1
fi

if ! [[ "${PORT}" =~ ^[0-9]+$ ]] || ((PORT < 1 || PORT > 65535)); then
  echo "Invalid port: ${PORT}" >&2
  exit 1
fi

get_listen_pids() {
  lsof -nP -t -iTCP:"${PORT}" -sTCP:LISTEN 2>/dev/null | sort -u || true
}

PIDS="$(get_listen_pids | tr '\n' ' ')"

if [[ -z "${PIDS// }" ]]; then
  echo "Port ${PORT} is free."
  exit 0
fi

echo "Port ${PORT} is occupied by process(es): ${PIDS}"
lsof -nP -iTCP:"${PORT}" -sTCP:LISTEN 2>/dev/null || true

for pid in ${PIDS}; do
  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill "${pid}" || true
  fi
done

sleep 1

REMAINING="$(get_listen_pids | tr '\n' ' ')"

if [[ -n "${REMAINING// }" ]]; then
  echo "Graceful stop did not fully release port ${PORT}, forcing kill: ${REMAINING}"
  for pid in ${REMAINING}; do
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" || true
    fi
  done
fi

FINAL_CHECK="$(get_listen_pids | tr '\n' ' ')"
if [[ -n "${FINAL_CHECK// }" ]]; then
  echo "Failed to release port ${PORT}. Remaining process(es): ${FINAL_CHECK}" >&2
  exit 1
fi

echo "Port ${PORT} has been released."
