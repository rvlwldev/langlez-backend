#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

compose() {
  docker compose -p langlez \
    -f postgresql.yml \
    -f redis.yml \
    -f kafka.yml \
    -f mongodb.yml \
    -f monitoring.yml \
    "$@"
}

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

compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'
