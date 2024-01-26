#!/bin/bash

# Base directory
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

# Source and compile directories
SRC_DIR="$BASE_DIR/src"
COMPILE_DIR="$BASE_DIR/compiled"

# Function to compile code
compile_service() {
    local service=$1
    echo "Compiling $service..."
    mkdir -p "$COMPILE_DIR/$service"
    cd "$SRC_DIR/$service"
    javac -cp ".:$BASE_DIR/lib/*" *.java
    mv "$SRC_DIR/$service"/*.class "$COMPILE_DIR/$service"
    echo "Compilation of $service complete."
    cd "$BASE_DIR"
}

# Function to start a service
start_service() {
    local service=$1
    echo "Starting $service..."
    cd "$COMPILE_DIR/$service"
    pwd
    java -cp ".:$service:$BASE_DIR/lib/*" "$service"
}

# Check command-line arguments
case "$1" in
    -c)
        compile_service "UserService"
        compile_service "ProductService"
        ;;
    -u)
        start_service "UserService"
        ;;
    -p)
        start_service "ProductService"
        ;;
    *)
        echo "Usage: $0 {-c|-u|-p}"
        exit 1
        ;;
esac
