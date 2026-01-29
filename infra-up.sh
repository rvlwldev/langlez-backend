#!/bin/bash

echo "Starting all infrastructure containers..."

# Create network if not exists
docker network create langlez-net 2>/dev/null || true

# Start Infrastructure
docker compose -f docker/mysql.yml up -d
docker compose -f docker/mongodb.yml up -d
docker compose -f docker/redis-sentinel.yml up -d
docker compose -f docker/kafka-cluster.yml up -d
docker compose -f docker/monitoring.yml up -d

# Start Gateway
docker compose -f docker/nginx.yml up -d

echo "Infrastructure & Gateway are up and running!"
echo "Network: langlez-net"
