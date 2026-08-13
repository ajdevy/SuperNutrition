#!/usr/bin/env bash
#
# Negative fixtures for backend/benchmark/run-benchmark.mjs.
#
# The benchmark picks the production model roster: config/model-profiles.toml
# cites BENCHMARK-REPORT.md by name and records which models were chosen from
# it. A benchmark that reports failure as success therefore selects models on
# evidence that was never gathered.
#
# Every fixture runs against a local stub. Nothing here touches the network or
# needs a provider key.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$repo_root/backend/benchmark/run-benchmark.mjs"
work="$(mktemp -d)"
stub_pid=""

cleanup() {
    if test -n "$stub_pid"; then kill "$stub_pid" 2>/dev/null || true; fi
    rm -rf "$work"
}
trap cleanup EXIT

failures=0
checks=0

fail() {
    printf '  FAIL: %s\n' "$1" >&2
    failures=$((failures + 1))
}

pass() {
    printf '  ok: %s\n' "$1"
}

check() {
    checks=$((checks + 1))
}

# A one-pixel JPEG is enough: no fixture gets far enough to decode it.
mkdir -p "$work/images" "$work/out"
printf '\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xd9' \
    >"$work/images/meal.jpg"

# Port 1 is reserved and refuses immediately, so "provider unreachable" needs no
# stub process of its own.
UNREACHABLE="http://127.0.0.1:1/v1"

run_benchmark() {
    local base="$1"
    shift
    env OPENROUTER_API_KEY='sk-fixture-not-a-real-key' \
        BENCHMARK_BASE_URL="$base" \
        BENCHMARK_IMAGE_DIR="$work/images" \
        BENCHMARK_OUT_DIR="$work/out" \
        "$@" \
        node "$script"
}

printf 'benchmark negative fixtures\n'

# ── Fixture 1 ──────────────────────────────────────────────────────────────
# Every request fails and the script still exits 0.
#
# `main().catch(console.error)` turns a thrown failure into a log line and a
# success exit code, and the per-image `catch` inside the loop swallows the
# rest. A caller that runs this in a pipeline and checks the exit status is told
# the benchmark succeeded when not one measurement was taken.
check
set +e
run_benchmark "$UNREACHABLE" >"$work/f1.log" 2>&1
status=$?
set -e
if test "$status" -eq 0; then
    fail "fixture 1: every request failed and the script exited 0"
else
    pass "fixture 1: a run with no successful measurement exits $status"
fi

# ── Fixture 2 ──────────────────────────────────────────────────────────────
# The report must not present success-only averages as the model's performance.
#
# Avg latency, avg cost and avg confidence are computed over successful results
# only and printed next to the success rate with no qualification. A model that
# answers one request in ten and is fast on that one outranks a model that
# answers all ten, which is exactly backwards for choosing a production roster.
#
# This needs a provider that actually answers: with no successes the averages
# block never runs and there is nothing to mislabel.
check
node -e '
const { createServer } = require("node:http");
const body = JSON.stringify({
  model: "stub/model",
  provider: "stub",
  choices: [{ message: { content: JSON.stringify({ foods: [{ label: "salad", confidence: 0.9 }] }) } }],
  usage: { prompt_tokens: 10, completion_tokens: 5 },
});
const server = createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(body);
});
server.listen(0, "127.0.0.1", () => {
  require("node:fs").writeFileSync(process.argv[1], String(server.address().port));
});
' "$work/ok-port" &
ok_pid=$!

for _ in $(seq 1 50); do
    test -s "$work/ok-port" && break
    sleep 0.1
done

if ! test -s "$work/ok-port"; then
    fail "fixture 2: answering stub never came up"
else
    rm -rf "$work/out"
    mkdir -p "$work/out"
    set +e
    run_benchmark "http://127.0.0.1:$(cat "$work/ok-port")/v1" >"$work/f2.log" 2>&1
    ok_status=$?
    set -e
    kill "$ok_pid" 2>/dev/null || true

    if test "$ok_status" -ne 0; then
        fail "fixture 2: a run where every request succeeded exited $ok_status"
    elif ! test -f "$work/out/BENCHMARK-REPORT.md"; then
        fail "fixture 2: no report was written to inspect"
    elif grep -qi 'successful requests only' "$work/out/BENCHMARK-REPORT.md"; then
        pass "fixture 2: averages are labelled as covering successful requests only"
    else
        fail "fixture 2: report presents success-only averages without saying so"
    fi
fi

# ── Fixture 3 ──────────────────────────────────────────────────────────────
# A provider that accepts the connection and never answers must not hang the
# run forever.
#
# fetch() has no timeout by default. A stalled TLS connection to a model
# endpoint blocks the whole benchmark on that one request, with no output and
# no way to tell it apart from a slow model.
check
node -e '
const { createServer } = require("node:http");
// Accept, then never respond.
const server = createServer(() => {});
server.listen(0, "127.0.0.1", () => {
  require("node:fs").writeFileSync(process.argv[1], String(server.address().port));
});
' "$work/port" &
stub_pid=$!

for _ in $(seq 1 50); do
    test -s "$work/port" && break
    sleep 0.1
done

if ! test -s "$work/port"; then
    fail "fixture 3: stalling stub never came up"
else
    port="$(cat "$work/port")"
    start="$(date +%s)"
    set +e
    # 25s ceiling: comfortably above a sane per-request timeout and far below
    # the four-models x one-image wait an untimed fetch would produce.
    run_benchmark "http://127.0.0.1:$port/v1" BENCHMARK_REQUEST_TIMEOUT_MS=2000 \
        >"$work/f3.log" 2>&1 &
    bench_pid=$!
    elapsed=0
    while kill -0 "$bench_pid" 2>/dev/null && test "$elapsed" -lt 25; do
        sleep 1
        elapsed=$(($(date +%s) - start))
    done
    if kill -0 "$bench_pid" 2>/dev/null; then
        kill -9 "$bench_pid" 2>/dev/null || true
        wait "$bench_pid" 2>/dev/null || true
        set -e
        fail "fixture 3: a stalled provider hung the run past 25s (no fetch timeout)"
    else
        wait "$bench_pid" 2>/dev/null
        set -e
        pass "fixture 3: a stalled provider is abandoned after the request timeout"
    fi
fi

printf '\n%d checks, %d failures\n' "$checks" "$failures"
test "$failures" -eq 0
