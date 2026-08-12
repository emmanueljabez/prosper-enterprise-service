#!/bin/bash

set -euo pipefail

DEPLOY_SCRIPT="${1:-deploy.sh}"

assert_contains() {
    local needle="$1"

    if ! grep -Fq "$needle" "$DEPLOY_SCRIPT"; then
        echo "Expected $DEPLOY_SCRIPT to contain: $needle" >&2
        return 1
    fi
}

assert_not_contains() {
    local needle="$1"

    if grep -Fq "$needle" "$DEPLOY_SCRIPT"; then
        echo "Expected $DEPLOY_SCRIPT not to contain: $needle" >&2
        return 1
    fi
}

assert_contains 'DEFAULT_SERVICE_PORT="${DEPLOY_SERVICE_PORT:-8080}"'
assert_contains 'DEPLOY_HEALTH_PATH="${DEPLOY_HEALTH_PATH:-/api/admin/migration/health}"'
assert_contains 'curl -fsS'
assert_contains 'http://127.0.0.1:${port}${DEPLOY_HEALTH_PATH}'
assert_not_contains 'port="8081"'

echo "deploy.sh readiness checks passed"
