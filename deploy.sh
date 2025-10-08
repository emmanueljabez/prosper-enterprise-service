#!/bin/bash

# ============================================
# ProsperMentor Deployment Script
# ============================================
# This script deploys the ProsperMentor application to the production server
# Server: 109.123.250.133
# Domain: app.prospermentor.com
# ============================================

set -e  # Exit on any error

# Configuration
SERVER="109.123.250.133"
USER="root"
PASSWORD="Inc0rr3ct@123!"
DOMAIN="app.prospermentor.com"
APP_NAME="prospermentor"
REMOTE_DIR="/opt/prospermentor"
JAR_FILE="build/libs/ProsperMentor-0.0.1-SNAPSHOT.jar"
SERVICE_NAME="prospermentor"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to execute SSH commands
ssh_exec() {
    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USER@$SERVER" "$1"
}

# Function to copy files to server
scp_copy() {
    sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no "$1" "$USER@$SERVER:$2"
}

print_info "Starting deployment process..."

# Step 1: Build the JAR file
print_info "Step 1/6: Building JAR file..."
if [ -f "$JAR_FILE" ]; then
    print_info "JAR file already exists. Skipping build."
else
    print_info "Building application..."
    ./gradlew clean bootJar
fi

if [ ! -f "$JAR_FILE" ]; then
    print_error "JAR file not found after build: $JAR_FILE"
    exit 1
fi

print_info "JAR file ready: $JAR_FILE"

# Step 2: Test SSH connection
print_info "Step 2/6: Testing SSH connection..."
if ssh_exec "echo 'Connection successful'" > /dev/null 2>&1; then
    print_info "SSH connection successful"
else
    print_error "Cannot connect to server. Please check:"
    echo "  - Server IP: $SERVER"
    echo "  - Username: $USER"
    echo "  - Password authentication is enabled on the server"
    exit 1
fi

# Step 3: Create remote directory and transfer JAR
print_info "Step 3/6: Creating remote directory and transferring JAR..."
ssh_exec "mkdir -p $REMOTE_DIR"
print_info "Uploading JAR file (this may take a moment)..."
scp_copy "$JAR_FILE" "$REMOTE_DIR/$APP_NAME.jar"
print_info "JAR file uploaded successfully"

# Step 4: Check for existing service and determine port
print_info "Step 4/6: Checking existing services..."
EXISTING_SERVICE=$(ssh_exec "systemctl list-units --type=service | grep -E 'prospermentor|app' || echo ''")
print_info "Existing services: ${EXISTING_SERVICE:-None}"

# Check what port the existing app.prospermentor.app service uses
EXISTING_PORT=$(ssh_exec "grep -r 'proxy_pass' /etc/nginx/conf.d/app.prospermentor.app.conf 2>/dev/null | grep -oP 'localhost:\K[0-9]+' || echo '8080'")
print_info "Existing service uses port: $EXISTING_PORT"

# Set new service port (default 8081 if existing is 8080)
if [ "$EXISTING_PORT" = "8080" ]; then
    NEW_PORT=8081
else
    NEW_PORT=8080
fi
print_info "New service will use port: $NEW_PORT"


# Reload systemd and start the service
print_info "Reloading systemd daemon..."
ssh_exec "systemctl daemon-reload"

print_info "Enabling $SERVICE_NAME service..."
ssh_exec "systemctl enable $SERVICE_NAME"

print_info "Starting $SERVICE_NAME service..."
ssh_exec "systemctl restart $SERVICE_NAME"

# Wait a moment for service to start
sleep 3

# Check service status
print_info "Checking service status..."
SERVICE_STATUS=$(ssh_exec "systemctl is-active $SERVICE_NAME")

if [ "$SERVICE_STATUS" = "active" ]; then
    print_info "✓ Service is running successfully!"
else
    print_error "Service failed to start. Checking logs..."
    ssh_exec "journalctl -u $SERVICE_NAME -n 50 --no-pager"
    exit 1
fi

# Display service info
print_info "==================== Deployment Complete ===================="
echo ""
print_info "Service Details:"
echo "  - Service Name: $SERVICE_NAME"
echo "  - Port: $NEW_PORT"
echo "  - Domain: $DOMAIN"
echo "  - JAR Location: $REMOTE_DIR/$APP_NAME.jar"
echo ""
print_info "Useful Commands:"
echo "  - Check status: ssh root@$SERVER 'systemctl status $SERVICE_NAME'"
echo "  - View logs: ssh root@$SERVER 'journalctl -u $SERVICE_NAME -f'"
echo "  - Restart: ssh root@$SERVER 'systemctl restart $SERVICE_NAME'"
echo "  - Stop: ssh root@$SERVER 'systemctl stop $SERVICE_NAME'"
echo ""
print_info "Application should be accessible at: http://$DOMAIN"
echo ""
print_info "==================== Showing Recent Logs ===================="
ssh_exec "journalctl -u $SERVICE_NAME -n 30 --no-pager"

print_info "Deployment completed successfully!"