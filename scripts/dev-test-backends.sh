#!/usr/bin/env bash
# Brings up the disposable Redis and PostgreSQL the integration lanes need, and
# prints the two environment variables that switch those lanes on.
#
# Both lanes SKIP without these, loudly — a distributed test reported as green
# when it never ran is worse than one that does not exist. So this script is how
# the skip is turned into a real run:
#
#   eval "$(scripts/dev-test-backends.sh)"
#   cd backend/mobile-api && npm test
#
# Everything lives under a scratch directory and listens on non-default ports, so
# it cannot touch a real database or a real cache. Stop it with --stop.

set -euo pipefail

STATE_DIR="${SUPERNUTRITION_TEST_STATE:-${TMPDIR:-/tmp}/supernutrition-test-backends}"
REDIS_PORT="${SUPERNUTRITION_TEST_REDIS_PORT:-6399}"
PG_PORT="${SUPERNUTRITION_TEST_PG_PORT:-55432}"
PG_DB="sn_test"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Homebrew keeps both keg-only; fall back to PATH for other installations.
redis_server="$(command -v redis-server || echo /usr/local/opt/redis/bin/redis-server)"
redis_cli="$(command -v redis-cli || echo /usr/local/opt/redis/bin/redis-cli)"
pg_bin="$(command -v pg_ctl >/dev/null 2>&1 && dirname "$(command -v pg_ctl)" || echo /usr/local/opt/postgresql@16/bin)"

say() { printf '%s\n' "$1" >&2; }

stop() {
  "$redis_cli" -p "$REDIS_PORT" shutdown nosave >/dev/null 2>&1 || true
  LC_ALL=C "$pg_bin/pg_ctl" -D "$STATE_DIR/pg" stop >/dev/null 2>&1 || true
  say "stopped redis:$REDIS_PORT and postgres:$PG_PORT"
}

if [ "${1:-}" = "--stop" ]; then
  stop
  exit 0
fi

for tool in "$redis_server" "$pg_bin/initdb"; do
  if [ ! -x "$tool" ]; then
    say "missing: $tool"
    say "install with: brew install redis postgresql@16"
    exit 1
  fi
done

mkdir -p "$STATE_DIR"

# --- redis ------------------------------------------------------------------
# No persistence: this is a coordination store for a test run, and a dump file
# would let one run's keys leak into the next.
if ! "$redis_cli" -p "$REDIS_PORT" ping >/dev/null 2>&1; then
  mkdir -p "$STATE_DIR/redis"
  "$redis_server" --port "$REDIS_PORT" --save '' --appendonly no --daemonize yes \
    --dir "$STATE_DIR/redis" --logfile "$STATE_DIR/redis/redis.log"
  sleep 1
  say "started redis on $REDIS_PORT"
else
  say "redis already listening on $REDIS_PORT"
fi

# --- postgres ---------------------------------------------------------------
if ! "$pg_bin/psql" -h 127.0.0.1 -p "$PG_PORT" -U postgres -tAc 'select 1' >/dev/null 2>&1; then
  if [ ! -d "$STATE_DIR/pg" ]; then
    # --locale=C because initdb refuses to guess when LANG is unset or odd, which
    # is the usual state inside a tool-driven shell.
    LC_ALL=C "$pg_bin/initdb" -D "$STATE_DIR/pg" -U postgres --auth=trust --locale=C >/dev/null
  fi
  LC_ALL=C "$pg_bin/pg_ctl" -D "$STATE_DIR/pg" -o "-p $PG_PORT" -l "$STATE_DIR/pg/server.log" start >/dev/null
  sleep 2
  say "started postgres on $PG_PORT"
else
  say "postgres already listening on $PG_PORT"
fi

if ! "$pg_bin/psql" -h 127.0.0.1 -p "$PG_PORT" -U postgres -lqt | cut -d'|' -f1 | grep -qw "$PG_DB"; then
  "$pg_bin/psql" -h 127.0.0.1 -p "$PG_PORT" -U postgres -c "CREATE DATABASE $PG_DB" >/dev/null
  say "created database $PG_DB"
fi

# Migrations are applied in filename order and are idempotent enough to re-run:
# a second application fails on existing objects, which is why the table count
# below is the check rather than the exit status.
for migration in "$repo_root"/backend/mobile-api/packages/database/src/migrations/*.sql; do
  "$pg_bin/psql" -h 127.0.0.1 -p "$PG_PORT" -U postgres -d "$PG_DB" -v ON_ERROR_STOP=1 \
    -f "$migration" >/dev/null 2>&1 || true
done

tables="$("$pg_bin/psql" -h 127.0.0.1 -p "$PG_PORT" -U postgres -d "$PG_DB" -tAc \
  "select count(*) from information_schema.tables where table_schema='public'")"
if [ "$tables" -lt 15 ]; then
  say "schema looks incomplete: $tables tables. Check $STATE_DIR/pg/server.log"
  exit 1
fi
say "schema ready: $tables tables"

# stdout carries only the exports, so `eval "$(…)"` works.
printf 'export REDIS_TEST_URL=redis://127.0.0.1:%s\n' "$REDIS_PORT"
printf 'export DATABASE_TEST_URL=postgres://postgres@127.0.0.1:%s/%s\n' "$PG_PORT" "$PG_DB"
