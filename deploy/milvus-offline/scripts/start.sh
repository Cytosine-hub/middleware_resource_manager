#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${BASE_DIR}"

if [[ -f "${BASE_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${BASE_DIR}/.env"
  set +a
fi

DATA_DIR="${MILVUS_DATA_DIR:-/data/milvus}"
if [[ "${MINIO_ROOT_USER:-minioadmin}" == "minioadmin" && "${MINIO_ROOT_PASSWORD:-minioadmin}" == "minioadmin" ]]; then
  echo "WARNING: Using Milvus 2.3.x default MinIO credentials. Keep MinIO ports bound to 127.0.0.1 and do not expose 9000/9001." >&2
fi

mkdir -p "${DATA_DIR}/etcd" "${DATA_DIR}/minio" "${DATA_DIR}/milvus"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

compose up -d etcd minio

echo "Waiting for etcd and MinIO to initialize..."
sleep 10

compose up -d standalone

echo "Milvus is starting. Run: ./scripts/healthcheck.sh"
