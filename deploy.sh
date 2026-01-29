#!/bin/bash

# ==============================================================================
# Langlez Zero-Downtime Deployment Script (Blue/Green)
# ==============================================================================

# Configuration
DOCKER_COMPOSE_FILE="docker/app.yml"
NGINX_CONF_DIR="docker/nginx/conf.d"
NGINX_CONTAINER="langlez-nginx"
SERVICE_BLUE="app-blue"
SERVICE_GREEN="app-green"

echo "Starting deployment..."

# 1. Check current running service
CURRENT_URL=$(grep "set \$service_url" $NGINX_CONF_DIR/service-url.inc | awk '{print $3}')

if [[ "$CURRENT_URL" == *"app-blue"* ]]; then
    CURRENT_SERVICE=$SERVICE_BLUE
    TARGET_SERVICE=$SERVICE_GREEN
    TARGET_PORT=8082
else
    CURRENT_SERVICE=$SERVICE_GREEN
    TARGET_SERVICE=$SERVICE_BLUE
    TARGET_PORT=8081
fi

echo "Current service: $CURRENT_SERVICE"
echo "Target service: $TARGET_SERVICE"

# 2. Build and start new container
echo "Building and starting $TARGET_SERVICE container..."
docker compose -f $DOCKER_COMPOSE_FILE up -d --build $TARGET_SERVICE

# 3. Health Check
echo "Starting health check ($TARGET_SERVICE)..."
MAX_RETRIES=10
COUNT=0
IS_HEALTHY=0

while [ $COUNT -lt $MAX_RETRIES ]; do
    sleep 5
    # Check Actuator health endpoint via localhost port
    RESPONSE=$(curl -s http://localhost:$TARGET_PORT/actuator/health)
    STATUS=$(echo $RESPONSE | grep -o '"status":"UP"')

    if [ ! -z "$STATUS" ]; then
        echo "$TARGET_SERVICE is healthy (UP)."
        IS_HEALTHY=1
        break
    fi

    echo "Waiting... ($COUNT/$MAX_RETRIES)"
    COUNT=$((COUNT+1))
done

if [ $IS_HEALTHY -eq 0 ]; then
    echo "Deployment failed: Health check failed for $TARGET_SERVICE."
    echo "Cleaning up failed container..."
    docker compose -f $DOCKER_COMPOSE_FILE stop $TARGET_SERVICE
    exit 1
fi

# 4. Switch traffic (Nginx)
echo "Switching traffic: $CURRENT_SERVICE -> $TARGET_SERVICE"
echo "set \$service_url http://$TARGET_SERVICE:8080;" > $NGINX_CONF_DIR/service-url.inc

echo "Reloading Nginx configuration..."
docker exec $NGINX_CONTAINER nginx -s reload

# 5. Stop old container
echo "Stopping old service ($CURRENT_SERVICE)..."
docker compose -f $DOCKER_COMPOSE_FILE stop $CURRENT_SERVICE

echo "Deployment completed successfully!"