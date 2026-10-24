#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

compose() {
  docker compose -p langlez \
    -f postgresql.yml \
    -f redis.yml \
    -f kafka.yml \
    -f mongodb.yml \
    -f minio.yml \
    -f monitoring.yml \
    "$@"
}

mkdir -p volume/minio

compose up -d --wait

compose exec -T mongo mongosh --quiet --eval '
  try {
    rs.status()
    print("mongo rs0: 이미 초기화됨")
  } catch (e) {
    if (e.codeName !== "NotYetInitialized") throw e
    rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "localhost:27017" }] })
    print("mongo rs0: 초기화함")
  }
'

MINIO_BUCKET=langlez-attachments

mc() {
  docker run --rm --network langlez_langlez-net \
    -e MC_HOST_local=http://langlez:langlez-local@langlez-minio:9000 \
    -v "$PWD/config/minio:/policy:ro" \
    cgr.dev/chainguard/minio-client@sha256:5830e3a2faa7b2141c07675976240759afcda4cab7a9c25011adf5ca056fb142 \
    "$@"
}

if mc ls "local/$MINIO_BUCKET" >/dev/null 2>&1; then
  echo "minio 버킷 $MINIO_BUCKET: 이미 있음"
else
  mc mb "local/$MINIO_BUCKET"
  echo "minio 버킷 $MINIO_BUCKET: 만듦"
fi
mc anonymous set-json /policy/public-read.json "local/$MINIO_BUCKET"

compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'
