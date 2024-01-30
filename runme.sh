#!/bin/bash

# Base directory
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

# Source and compile directories
SRC_DIR="$BASE_DIR/src"
mkdir -p compiled
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

make_db(){
    cd "$COMPILE_DIR"
    mkdir -p Database
    cd Database

    # Check if the info.db file does not exist and then create it
    if [ ! -f "info.db" ]; then
        sqlite3 info.db "VACUUM;"
    fi
    cd ..
    cd ..
    
}   

run_workload_parser() {
    local file=$1
    echo "Running workload parser with file: $file"
    python3 "$BASE_DIR/workloadParser.py" "$file"
}
# Check command-line arguments
case "$1" in
    -c) 
        make_db
        compile_service "UserService"
        compile_service "ProductService"
        compile_service "OrderService"
        ;;
    -u)
        start_service "UserService"
        ;;
    -p)
        start_service "ProductService"
        ;;

    -o)
        start_service "OrderService"
        ;;
    
    -w)
        if [ -z "$2" ]; then
            echo "Please provide a filename. Usage: $0 -w <filename>"
            exit 1
        else
            run_workload_parser "$2"
        fi
        ;;
    *)
        echo "Usage: $0 {-c|-u|-p|-o|-w <filename>}"
        exit 1
        ;;
esac
