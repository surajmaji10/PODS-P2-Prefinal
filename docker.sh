#!/bin/bash

# Exit on error
set -e

# Define service names and folder paths
SERVICES=("account-service" "marketplace-service" "wallet-service")
IMAGES=("akashmaji/account-service:1.0" "akashmaji/marketplace-service:1.0" "akashmaji/wallet-service:1.0")
PORTS=(8080 8081 8082)

# Function to build Docker images
build_service() {
  local service="$1"
  local image="$2"

  echo "Building Docker image for $service..."
  cd "$service"
  docker build -t "$image" .
  cd ..
}

# Function to run a Docker container
run_service() {
  local service="$1"
  local image="$2"
  local port="$3"

  echo "Running $service on port $port..."
  docker run -p "$port":8080 --rm --name "$service" \
    --add-host=host.docker.internal:host-gateway \
    "$image" &
}

# Parse command-line arguments
case "$1" in
  "-1")
    build_service "${SERVICES[0]}" "${IMAGES[0]}"
    run_service "${SERVICES[0]}" "${IMAGES[0]}" "${PORTS[0]}"
    ;;
  "-2")
    build_service "${SERVICES[1]}" "${IMAGES[1]}"
    run_service "${SERVICES[1]}" "${IMAGES[1]}" "${PORTS[1]}"
    ;;
  "-3")
    build_service "${SERVICES[2]}" "${IMAGES[2]}"
    run_service "${SERVICES[2]}" "${IMAGES[2]}" "${PORTS[2]}"
    ;;
  "-all")
    for i in "${!SERVICES[@]}"; do
      build_service "${SERVICES[$i]}" "${IMAGES[$i]}"
      run_service "${SERVICES[$i]}" "${IMAGES[$i]}" "${PORTS[$i]}"
    done
    wait # Wait for all services to start
    ;;
  *)
    echo "Usage: $0 {-1|-2|-3|-all}"
    echo "  -1    Build & Run Account Service"
    echo "  -2    Build & Run Marketplace Service"
    echo "  -3    Build & Run Wallet Service"
    echo "  -all  Build & Run All Services"
    exit 1
    ;;
esac
