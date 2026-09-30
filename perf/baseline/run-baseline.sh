#!/usr/bin/env bash
# Records the migration performance and API baseline for docs/migration/baseline.md.
#
#   JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 LABEL=java11 perf/baseline/run-baseline.sh
#
# Starts throwaway Postgres 14 and Redis 7 containers (same images as demos-coghealth-ehr-data),
# lets Flyway create and seed an empty schema, then measures startup, idle memory, latency and
# throughput with perf/k6/baseline.js, and finally captures the API contract with capture_api.py.
# Results go to docs/migration/baseline/$LABEL.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LABEL="${LABEL:-java11}"
OUT="${OUT:-$ROOT/docs/migration/baseline/$LABEL}"
JAVA_HOME="${JAVA_HOME:?set JAVA_HOME to the JDK under test}"
JAVA_OPTS="${JAVA_OPTS:--Xmx1g}"
PORT="${PORT:-8080}"
PG_PORT="${PG_PORT:-55432}"
REDIS_PORT="${REDIS_PORT:-56379}"
STARTUP_RUNS="${STARTUP_RUNS:-5}"
IDLE_SECONDS="${IDLE_SECONDS:-30}"
RATE="${RATE:-200}"
VUS="${VUS:-32}"
DURATION="${DURATION:-60s}"
SKIP_BUILD="${SKIP_BUILD:-0}"
KEEP_INFRA="${KEEP_INFRA:-0}"
PG_CONTAINER=ehr-baseline-pg
REDIS_CONTAINER=ehr-baseline-redis
BASE_URL="http://localhost:$PORT/api"
LOG_DIR="$ROOT/target/baseline-logs/$LABEL"
APP_PID=""
CREATED_CONTAINERS=()

for p in "$PORT" "$PG_PORT" "$REDIS_PORT"; do
  listener="$(ss -ltnH "sport = :$p")"
  if [ -n "$listener" ]; then
    echo "port $p is already in use; stop its listener before running the baseline" >&2
    exit 1
  fi
done

existing_containers="$(docker ps -a --format '{{.Names}}' | awk -v pg="$PG_CONTAINER" -v redis="$REDIS_CONTAINER" '$0 == pg || $0 == redis')"
if [ -n "$existing_containers" ]; then
  echo "refusing to run: existing baseline container(s) $existing_containers may be left over from KEEP_INFRA=1; remove them manually before rerunning" >&2
  exit 1
fi

mkdir -p "$OUT"

cleanup() {
  stop_app
  if [ "${#CREATED_CONTAINERS[@]}" -gt 0 ] && [ "$KEEP_INFRA" != "1" ]; then
    docker rm -f "${CREATED_CONTAINERS[@]}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

stop_app() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID"
    wait "$APP_PID" 2>/dev/null || true
  fi
  APP_PID=""
}

now_ms() { date +%s%3N; }

metric() {
  # metric <name> [tag] -> first measurement value
  local url="$BASE_URL/actuator/metrics/$1"
  [ -n "${2:-}" ] && url="$url?tag=$2"
  curl -sf "$url" | jq -r '.measurements[0].value'
}

rss_kb() { awk '/^VmRSS:/ {print $2}' "/proc/$APP_PID/status"; }
hwm_kb() { awk '/^VmHWM:/ {print $2}' "/proc/$APP_PID/status"; }

start_app() {
  # start_app <log file>; sets APP_PID, WALL_MS, STARTED_S and JVM_S
  local log="$1" t0
  t0=$(now_ms)
  DB_URL="jdbc:postgresql://localhost:$PG_PORT/coghealth" DB_USERNAME=coghealth DB_PASSWORD="$PG_PASSWORD" \
    SPRING_REDIS_PORT="$REDIS_PORT" SERVER_PORT="$PORT" MEDCHART_SECURITY_JWT_SECRET="$JWT_SECRET" \
    "$JAVA_HOME/bin/java" $JAVA_OPTS -jar "$JAR" >"$log" 2>&1 &
  APP_PID=$!
  for _ in $(seq 1 1200); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      echo "app exited during startup; last 20 lines of $log:" >&2
      tail -n 20 "$log" >&2
      return 1
    fi
    if curl -sf -o /dev/null "$BASE_URL/actuator/health"; then
      if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "app exited during startup; last 20 lines of $log:" >&2
        tail -n 20 "$log" >&2
        return 1
      fi
      WALL_MS=$(($(now_ms) - t0))
      read -r STARTED_S JVM_S < <(grep -m1 -oE 'Started MedchartEhrApplication in [0-9.]+ seconds \(JVM running for [0-9.]+\)' "$log" \
        | sed -E 's/.* in ([0-9.]+) seconds \(JVM running for ([0-9.]+)\)/\1 \2/')
      return 0
    fi
    sleep 0.1
  done
  echo "app not healthy after 120s, see $log" >&2
  return 1
}

sample_memory() {
  # sample_memory <csv> ; samples every second until killed
  echo "epoch_ms,rss_kb,heap_used_bytes,heap_committed_bytes" >"$1"
  while kill -0 "$APP_PID" 2>/dev/null; do
    echo "$(now_ms),$(rss_kb),$(metric jvm.memory.used area:heap),$(metric jvm.memory.committed area:heap)" >>"$1"
    sleep 1
  done
}

memory_stats() {
  # memory_stats <csv> -> JSON with max/median of each column in MiB
  python3 - "$1" <<'PY'
import csv, json, statistics, sys
rows = list(csv.DictReader(open(sys.argv[1])))
def col(name, div):
    vals = [float(r[name]) / div for r in rows if r[name] not in ("", "null")]
    return {"median_mib": round(statistics.median(vals), 1), "max_mib": round(max(vals), 1), "samples": len(vals)}
print(json.dumps({"rss": col("rss_kb", 1024), "heap_used": col("heap_used_bytes", 1048576),
                  "heap_committed": col("heap_committed_bytes", 1048576)}))
PY
}

gc_snapshot() {
  # MAX is Micrometer's rolling-window max, not per phase; it can include pauses from before the phase started.
  curl -sf "$BASE_URL/actuator/metrics/jvm.gc.pause" | jq -c '{count: (.measurements[] | select(.statistic=="COUNT") | .value), total_s: (.measurements[] | select(.statistic=="TOTAL_TIME") | .value), max_s: (.measurements[] | select(.statistic=="MAX") | .value)}'
}

run_k6() {
  # run_k6 <profile>
  local profile="$1" mem_csv="$OUT/memory-$1.csv" sampler gc_before gc_after log_before log_after
  gc_before=$(gc_snapshot)
  log_before=$(stat -c %s "$APP_LOG")
  sample_memory "$mem_csv" & sampler=$!
  k6 run --quiet -e BASE_URL="$BASE_URL" -e PROFILE="$profile" -e RATE="$RATE" -e VUS="$VUS" -e DURATION="$DURATION" \
    -e SUMMARY_OUT="$OUT/k6-$profile.json" "$ROOT/perf/k6/baseline.js" | tee "$OUT/k6-$profile.txt"
  kill "$sampler"; wait "$sampler" 2>/dev/null || true
  gc_after=$(gc_snapshot)
  log_after=$(stat -c %s "$APP_LOG")
  jq -n --argjson mem "$(memory_stats "$mem_csv")" --argjson before "$gc_before" --argjson after "$gc_after" \
    --argjson log_bytes "$((log_after - log_before))" \
    '{memory: $mem, gc_pause: {count: ($after.count - $before.count), total_s: ($after.total_s - $before.total_s), max_s: $after.max_s},
      app_log_mib_written: ($log_bytes / 1048576)}' \
    >"$OUT/resources-$profile.json"
}

# ---- environment -------------------------------------------------------------------------
# Throwaway credentials for the disposable containers and an HS512-sized JWT key (the default in
# application.yml is too short for HS512, so /api/auth/login fails without an override).
PG_PASSWORD="$(openssl rand -hex 16)"
JWT_SECRET="$(openssl rand -hex 64)"
{
  echo "recorded_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git_commit: $(git -C "$ROOT" rev-parse --short HEAD)"
  echo "java: $("$JAVA_HOME/bin/java" -version 2>&1 | head -2 | tr '\n' ' ')"
  echo "java_opts: $JAVA_OPTS"
  echo "maven: $(mvn -v -B 2>/dev/null | head -1 | sed 's/\x1b\[[0-9;]*m//g')"
  echo "k6: $(k6 version | head -1)"
  echo "docker: $(docker --version)"
  echo "os: $(. /etc/os-release && echo "$PRETTY_NAME") kernel $(uname -r)"
  echo "cpu: $(nproc) x $(lscpu | sed -n 's/^Model name: *//p')"
  echo "memory: $(free -g | awk '/^Mem:/ {print $2}') GiB"
  echo "load: rate=$RATE req/s (latency), vus=$VUS (throughput), duration=$DURATION each, after 30s warmup at 50 req/s"
  echo "topology: app, Postgres and Redis containers, and k6 all on the same host (loopback)"
} >"$OUT/environment.txt"
cat "$OUT/environment.txt"

if [ "$SKIP_BUILD" != "1" ]; then
  (cd "$ROOT" && JAVA_HOME="$JAVA_HOME" mvn -q -B clean package -DskipTests)
fi
JAR="$(ls "$ROOT"/target/medchart-ehr-api-*.jar | grep -v '\.original$' | head -1)"
mkdir -p "$LOG_DIR"

# docker create returns the id before anything can fail to start, so cleanup removes exactly what this run created.
CREATED_CONTAINERS+=("$(docker create --name "$PG_CONTAINER" -e POSTGRES_DB=coghealth -e POSTGRES_USER=coghealth \
  -e POSTGRES_PASSWORD="$PG_PASSWORD" -p "127.0.0.1:$PG_PORT:5432" postgres:14-alpine)")
CREATED_CONTAINERS+=("$(docker create --name "$REDIS_CONTAINER" -p "127.0.0.1:$REDIS_PORT:6379" redis:7-alpine)")
docker start "${CREATED_CONTAINERS[@]}" >/dev/null
until docker exec "$PG_CONTAINER" pg_isready -U coghealth -d coghealth >/dev/null 2>&1; do sleep 0.5; done
sleep 2

# ---- startup -----------------------------------------------------------------------------
echo "run,flyway,wall_ms_to_healthy,started_in_s,jvm_running_for_s,application_ready_time_s" >"$OUT/startup.csv"
for run in $(seq 0 "$STARTUP_RUNS"); do
  flyway=$([ "$run" = 0 ] && echo "migrate_empty_schema" || echo "schema_up_to_date")
  start_app "$LOG_DIR/startup-$run.log"
  ready=$(metric application.ready.time)
  echo "$run,$flyway,$WALL_MS,$STARTED_S,$JVM_S,$ready" | tee -a "$OUT/startup.csv"
  if [ "$run" -lt "$STARTUP_RUNS" ]; then stop_app; fi
done

APP_LOG="$LOG_DIR/startup-$STARTUP_RUNS.log"

# ---- idle memory -------------------------------------------------------------------------
sleep "$IDLE_SECONDS"
jq -n --arg rss "$(rss_kb)" --arg hwm "$(hwm_kb)" --arg used "$(metric jvm.memory.used area:heap)" \
  --arg committed "$(metric jvm.memory.committed area:heap)" --arg nonheap "$(metric jvm.memory.used area:nonheap)" \
  --arg threads "$(metric jvm.threads.live)" --arg classes "$(metric jvm.classes.loaded)" --arg idle "$IDLE_SECONDS" \
  '{idle_seconds_after_start: ($idle|tonumber), rss_mib: (($rss|tonumber)/1024), rss_peak_mib: (($hwm|tonumber)/1024),
    heap_used_mib: (($used|tonumber)/1048576), heap_committed_mib: (($committed|tonumber)/1048576),
    nonheap_used_mib: (($nonheap|tonumber)/1048576), live_threads: ($threads|tonumber), classes_loaded: ($classes|tonumber)}' \
  | tee "$OUT/idle.json"

# ---- load --------------------------------------------------------------------------------
k6 run --quiet -e BASE_URL="$BASE_URL" -e PROFILE=warmup "$ROOT/perf/k6/baseline.js" >/dev/null
run_k6 latency
run_k6 throughput
jq -n --arg hwm "$(hwm_kb)" '{rss_peak_mib_whole_run: (($hwm|tonumber)/1024)}' >"$OUT/rss-peak.json"

# ---- API contract ------------------------------------------------------------------------
mkdir -p "$OUT/api"
python3 "$ROOT/perf/baseline/capture_api.py" "$BASE_URL" "$OUT/api" | tee "$LOG_DIR/capture-api.txt"

stop_app
echo "baseline written to $OUT (application logs in $LOG_DIR)"
