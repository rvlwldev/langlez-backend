#!/bin/bash

echo "Starting all infrastructure containers..."

# Create network if not exists
docker network create langlez-net 2>/dev/null || true

# 모든 compose 파일을 -f 로 합쳐 단일 프로젝트(langlez)로 올린다.
# 파일별로 따로 up 하면 compose 가 서로를 orphan 으로 보고,
# Docker Desktop 컨테이너 탭에서도 그룹이 쪼개진다.
docker compose -p langlez \
  -f docker/postgresql.yml \
  -f docker/mongodb.yml \
  -f docker/redis-sentinel.yml \
  -f docker/kafka.yml \
  -f docker/monitoring.yml \
  up -d

echo "Infrastructure is up and running!"
echo "Project: langlez"
echo "Network: langlez-net"
