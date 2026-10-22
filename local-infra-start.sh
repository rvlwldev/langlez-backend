#!/bin/bash

echo "Starting all infrastructure containers..."

# Create network if not exists
docker network create langlez-net 2>/dev/null || true

# 모든 compose 파일을 -f 로 합쳐 단일 프로젝트(langlez)로 올린다.
# 파일별로 따로 up 하면 compose 가 서로를 orphan 으로 보고,
# Docker Desktop 컨테이너 탭에서도 그룹이 쪼개진다.
# mongodb 도 함께 올린다. 없어도 앱은 뜨지만(인덱스 생성이 기동 경로 밖으로 빠져 있다)
# chat 이 동작하지 않고 health 가 DOWN 이라, 빼두면 확인할 때마다 손으로 덧붙이게 된다.
docker compose -p langlez \
  -f docker/postgresql.yml \
  -f docker/redis.yml \
  -f docker/kafka.yml \
  -f docker/mongodb.yml \
  -f docker/monitoring.yml \
  up -d

echo "All Infrastructures is up and running"
