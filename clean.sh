#!/bin/bash

# Define service names and image names
SERVICES=("account-service" "marketplace-service" "wallet-service")
IMAGES=("akashmaji/account-service:1.0" "akashmaji/marketplace-service:1.0" "akashmaji/wallet-service:1.0")

echo "Stopping and removing running containers..."
for service in "${SERVICES[@]}"; do
  if docker ps -a --format '{{.Names}}' | grep -q "^$service$"; then
    docker stop "$service" && docker rm "$service"
    echo "Stopped and removed: $service"
  else
    echo "Container $service not found."
  fi
done

echo "Removing Docker images..."
for image in "${IMAGES[@]}"; do
  if docker images -q "$image" &>/dev/null; then
    docker rmi "$image"
    echo "Removed image: $image"
  else
    echo "Image $image not found."
  fi
done

echo "Cleanup complete!"
