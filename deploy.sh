#!/bin/bash

# ============================================
# ProsperMentor Deployment Script
# ============================================
# This script deploys the ProsperMentor application to the production server.
# It protects against overlapping deploys, uploads to a temporary file first,
# validates the uploaded JAR, then swaps it into place atomically.
# ============================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_PROFILE="${1:-}"

load_deploy_env() {
    local candidate
    local candidates=()

    if [ -n "$DEPLOY_PROFILE" ]; then
        candidates+=("$SCRIPT_DIR/.deploy/${DEPLOY_PROFILE}.env")
        candidates+=("$SCRIPT_DIR/deploy.${DEPLOY_PROFILE}.env")
    else
        candidates+=("$SCRIPT_DIR/.deploy/default.env")
        candidates+=("$SCRIPT_DIR/.deploy/prod.env")
        candidates+=("$SCRIPT_DIR/deploy.env")
    fi

    for candidate in "${candidates[@]}"; do
        if [ -f "$candidate" ]; then
            # shellcheck disable=SC1090
            set -a
            source "$candidate"
            set +a
            return 0
        fi
    done

    return 1
}

if ! load_deploy_env && [ -z "${DEPLOY_SERVER:-}" ]; then
    echo "[ERROR] No deploy profile found."
    if [ -n "$DEPLOY_PROFILE" ]; then
        echo "Expected one of:"
        echo "  - $SCRIPT_DIR/.deploy/${DEPLOY_PROFILE}.env"
        echo "  - $SCRIPT_DIR/deploy.${DEPLOY_PROFILE}.env"
    else
        echo "Expected one of:"
        echo "  - $SCRIPT_DIR/.deploy/default.env"
        echo "  - $SCRIPT_DIR/.deploy/prod.env"
        echo "  - $SCRIPT_DIR/deploy.env"
    fi
    echo ""
    echo "Or export DEPLOY_SERVER, DEPLOY_USER, and DEPLOY_PASSWORD before running the script."
    exit 1
fi

# Configuration
SERVER="${DEPLOY_SERVER:?DEPLOY_SERVER is required}"
USER="${DEPLOY_USER:?DEPLOY_USER is required}"
PASSWORD="${DEPLOY_PASSWORD:?DEPLOY_PASSWORD is required}"
DOMAIN="${DEPLOY_DOMAIN:-enterprise.prospermentor.com}"
APP_NAME="prospermentor"
REMOTE_DIR="/opt/prospermentor"
JAR_FILE="build/libs/ProsperMentor-0.0.1-SNAPSHOT.jar"
SERVICE_NAME="prospermentor"
LOCAL_LOCK_DIR="/tmp/${APP_NAME}-deploy.lock"
REMOTE_LOCK_DIR="/tmp/${APP_NAME}-deploy.lock"
REMOTE_TMP_JAR="${REMOTE_DIR}/${APP_NAME}.jar.upload-$$"
SERVICE_START_TIMEOUT=90
SERVICE_CHECK_INTERVAL=3

# Resolve sshpass path explicitly (helps when script is run with sudo where PATH differs)
SSHPASS_BIN="$(command -v sshpass || true)"
if [ -z "$SSHPASS_BIN" ]; then
    for candidate in /opt/homebrew/bin/sshpass /usr/local/bin/sshpass; do
        if [ -x "$candidate" ]; then
            SSHPASS_BIN="$candidate"
            break
        fi
    done
fi

if [ -z "$SSHPASS_BIN" ]; then
    echo "[ERROR] sshpass is not installed or not found in PATH."
    echo "Install it first (macOS): brew install hudochenkov/sshpass/sshpass"
    exit 1
fi

if ! command -v rsync >/dev/null 2>&1; then
    echo "[ERROR] rsync is required but not installed."
    exit 1
fi

# SSH options tuned for password-based automation.
# ControlMaster is intentionally disabled here to avoid stale shared sessions
# across multiple deploy runs corrupting transfers or masking failures.
SSH_OPTS=(
    -o StrictHostKeyChecking=no
    -o UserKnownHostsFile=/dev/null
    -o LogLevel=ERROR
    -o PreferredAuthentications=password
    -o PubkeyAuthentication=no
    -o NumberOfPasswordPrompts=1
    -o ConnectTimeout=30
    -o ConnectionAttempts=1
    -o ServerAliveInterval=15
    -o ServerAliveCountMax=3
)

SSH_RETRIES=6
SSH_RETRY_DELAY=5
export LC_ALL=C
export LANG=C

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

cleanup() {
    local status=$?

    if [ -d "$LOCAL_LOCK_DIR" ]; then
        rmdir "$LOCAL_LOCK_DIR" >/dev/null 2>&1 || true
    fi

    "$SSHPASS_BIN" -p "$PASSWORD" ssh "${SSH_OPTS[@]}" "$USER@$SERVER" \
        "rm -f '$REMOTE_TMP_JAR'; rmdir '$REMOTE_LOCK_DIR' >/dev/null 2>&1 || true" >/dev/null 2>&1 || true

    exit "$status"
}

trap cleanup EXIT INT TERM

acquire_local_lock() {
    if mkdir "$LOCAL_LOCK_DIR" 2>/dev/null; then
        return 0
    fi

    print_error "Another deployment appears to be running."
    echo "  Lock directory: $LOCAL_LOCK_DIR"
    exit 1
}

acquire_remote_lock() {
    if ssh_exec "mkdir '$REMOTE_LOCK_DIR'" >/dev/null 2>&1; then
        return 0
    fi

    print_error "Another deployment appears to be running on the server."
    echo "  Remote lock directory: $REMOTE_LOCK_DIR"
    exit 1
}

run_with_ssh_retries() {
    local attempt=1
    local status=0

    while [ "$attempt" -le "$SSH_RETRIES" ]; do
        if "$@"; then
            return 0
        fi
        status=$?

        if [ "$status" -ne 255 ] || [ "$attempt" -eq "$SSH_RETRIES" ]; then
            return "$status"
        fi

        print_warning "SSH transport failed (attempt $attempt/$SSH_RETRIES). Retrying in ${SSH_RETRY_DELAY}s..."
        sleep "$SSH_RETRY_DELAY"
        attempt=$((attempt + 1))
    done

    return "$status"
}

ssh_exec() {
    run_with_ssh_retries env LC_ALL=C LANG=C "$SSHPASS_BIN" -p "$PASSWORD" ssh "${SSH_OPTS[@]}" "$USER@$SERVER" "$1"
}

rsync_copy() {
    local ssh_opts_joined=""
    local opt

    for opt in "${SSH_OPTS[@]}"; do
        ssh_opts_joined+=" $(printf '%q' "$opt")"
    done

    SSHPASS="$PASSWORD" \
    RSYNC_RSH="env LC_ALL=C LANG=C $(printf '%q' "$SSHPASS_BIN") -e ssh${ssh_opts_joined}" \
    env LC_ALL=C LANG=C rsync -av --inplace --partial --progress "$1" "$USER@$SERVER:$2"
}

get_local_file_size() {
    wc -c < "$1" | tr -d ' '
}

get_local_sha256() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        openssl dgst -sha256 "$1" | awk '{print $NF}'
    fi
}

get_remote_file_size() {
    ssh_exec "wc -c < '$1' | tr -d ' '"
}

get_remote_service_port() {
    local port

    port="$(ssh_exec "systemctl show '$SERVICE_NAME' --property=ExecStart | sed 's/^ExecStart=//' | grep -oE -- '--server\\.port=[0-9]+' | head -n 1 | cut -d= -f2")"
    if [ -z "$port" ]; then
        port="8081"
    fi
    echo "$port"
}

wait_for_service() {
    local elapsed=0
    local port="$1"

    while [ "$elapsed" -lt "$SERVICE_START_TIMEOUT" ]; do
        if [ "$(ssh_exec "systemctl is-active '$SERVICE_NAME' || true")" = "active" ] \
            && ssh_exec "ss -ltn '( sport = :$port )' | grep -q LISTEN"; then
            return 0
        fi

        sleep "$SERVICE_CHECK_INTERVAL"
        elapsed=$((elapsed + SERVICE_CHECK_INTERVAL))
    done

    return 1
}

acquire_local_lock
print_info "Starting deployment process..."

print_info "Step 1/7: Building JAR file..."
if [ -f "$JAR_FILE" ]; then
    print_info "Deleting old JAR file to ensure fresh build..."
    rm -f "$JAR_FILE"
fi

print_info "Building application..."
./gradlew clean bootJar

if [ ! -f "$JAR_FILE" ]; then
    print_error "JAR file not found after build: $JAR_FILE"
    exit 1
fi

LOCAL_SIZE="$(get_local_file_size "$JAR_FILE")"
LOCAL_SHA256="$(get_local_sha256 "$JAR_FILE")"
print_info "JAR file ready: $JAR_FILE"
print_info "Local JAR size: $LOCAL_SIZE bytes"
print_info "Local JAR sha256: $LOCAL_SHA256"

print_info "Step 2/7: Testing SSH connection..."
SSH_TEST_OUTPUT=""
if SSH_TEST_OUTPUT="$(ssh_exec "echo 'Connection successful'" 2>&1)"; then
print_info "SSH connection successful"
else
    print_error "Cannot connect to server. Please check:"
    echo "  - Server IP: $SERVER"
    echo "  - Username: $USER"
    echo "  - Password authentication is enabled on the server"
    echo "  - Local sshpass path: $SSHPASS_BIN"
    echo ""
    print_error "SSH returned:"
    echo "$SSH_TEST_OUTPUT"
    exit 1
fi

acquire_remote_lock

print_info "Step 3/7: Preparing remote directory..."
ssh_exec "mkdir -p '$REMOTE_DIR' && rm -f '$REMOTE_TMP_JAR'"

print_info "Step 4/7: Uploading JAR to temporary path..."
rsync_copy "$JAR_FILE" "$REMOTE_TMP_JAR"
REMOTE_SIZE="$(get_remote_file_size "$REMOTE_TMP_JAR")"
print_info "Remote temp JAR size: $REMOTE_SIZE bytes"

if [ "$LOCAL_SIZE" != "$REMOTE_SIZE" ]; then
    print_error "Remote upload size does not match local build."
    echo "  - Local:  $LOCAL_SIZE"
    echo "  - Remote: $REMOTE_SIZE"
    exit 1
fi

print_info "Step 5/7: Swapping JAR into place..."
ssh_exec "mv '$REMOTE_TMP_JAR' '$REMOTE_DIR/$APP_NAME.jar'"

SERVICE_PORT="$(get_remote_service_port)"
print_info "Detected service port: $SERVICE_PORT"

print_info "Step 6/7: Restarting service..."
ssh_exec "systemctl daemon-reload"
ssh_exec "systemctl enable '$SERVICE_NAME'"
ssh_exec "systemctl restart '$SERVICE_NAME'"

print_info "Step 7/7: Waiting for service readiness..."
if ! wait_for_service "$SERVICE_PORT"; then
    print_error "Service did not become ready within ${SERVICE_START_TIMEOUT}s."
    ssh_exec "systemctl status '$SERVICE_NAME' --no-pager || true"
    ssh_exec "journalctl -u '$SERVICE_NAME' -n 80 --no-pager || true"
    exit 1
fi

print_info "✓ Service is running successfully"
echo ""
print_info "==================== Deployment Complete ===================="
echo ""
print_info "Service Details:"
echo "  - Service Name: $SERVICE_NAME"
echo "  - Port: $SERVICE_PORT"
echo "  - Domain: $DOMAIN"
echo "  - JAR Location: $REMOTE_DIR/$APP_NAME.jar"
echo "  - JAR sha256: $LOCAL_SHA256"
echo ""
print_info "Useful Commands:"
echo "  - Check status: ssh root@$SERVER 'systemctl status $SERVICE_NAME'"
echo "  - View logs: ssh root@$SERVER 'journalctl -u $SERVICE_NAME -f'"
echo "  - Restart: ssh root@$SERVER 'systemctl restart $SERVICE_NAME'"
echo "  - Stop: ssh root@$SERVER 'systemctl stop $SERVICE_NAME'"
echo ""
print_info "Application should be accessible at: https://$DOMAIN"
echo ""
print_info "==================== Recent Logs ===================="
ssh_exec "journalctl -u '$SERVICE_NAME' -n 30 --no-pager"

print_info "Deployment completed successfully!"
