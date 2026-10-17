# WAF Log Analyzer

Lab: Juice Shop → Nginx (JSON access log) → Filebeat → Kafka (`raw-web-logs`) → Spring ingest/normalize → PostgreSQL → `GET /api/events`.

## Prerequisites

- Docker Compose
- Java 25 (only if you run the backend with `./gradlew bootRun` instead of the Compose service)
- Add to `/etc/hosts` (optional but useful):  
  `127.0.0.1 juice.lab.local shop.lab.local`

## Operations

`make help` lists the commands.

Day-to-day (API on the host):

```bash
make up
make backend          # LLM off → new incidents SKIPPED
# APP_LLM_ENABLED=true make backend   # optional host Ollama
```

Full Compose (backend image + SOC UI). The image build vendors host Gradle via `make prepare-docker-gradle` so Docker does **not** download `gradle-*-bin.zip`:

```bash
make stack-ui         # http://127.0.0.1:3001  (LLM off)
make stack-llm        # same + Compose profile llm (Ollama)
```

`make ui` starts Vite at http://127.0.0.1:5173.

## Week 1 — start lab

```bash
docker compose up -d --build
# wait until spring-security-backend is healthy (first build can take several minutes)

# traffic through Nginx
curl -sS -o /dev/null -w "%{http_code}\n" -H 'Host: juice.lab.local' http://127.0.0.1/
curl -sS -o /dev/null -w "%{http_code}\n" -H 'Host: shop.lab.local' http://127.0.0.1/
sleep 3

# login (Week 3). Lab users: admin/admin, analyst/analyst, viewer/viewer.
TOKEN=$(curl -sS -X POST 'http://127.0.0.1:8080/api/oauth/tokens' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

# normalized events (Bearer required)
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/events'
```

Optional smoke scripts:

```bash
chmod +x datasets/probes/*.sh
./datasets/probes/run.sh
./datasets/probes/browse_clean.sh
```

### Backend on host (infra in Compose)

```bash
docker compose up -d postgres kafka kafka-init nginx filebeat juice-shop
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || echo /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home)
./gradlew bootRun
```

- Kafka (host): `localhost:29092`
- Postgres: `localhost:5432` / user `waf` / password `waf` / db `waf_analyzer`

### Verify Kafka topic (AC-1)

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic raw-web-logs --from-beginning --max-messages 1 --timeout-ms 10000
```

## Smoke checklist (Week 1 DoD)

1. Request via Nginx → message on `raw-web-logs` within ~5s.
2. Spring consumer persists `web_events`.
3. `GET /api/events` with `Authorization: Bearer` returns the event (`host`, `path`, `appId` / `appName`, `status`). Unauthenticated calls return 401.
4. `Host: shop.lab.local` maps to a different `appId` than `juice.lab.local`.

## Redis cache

Compose includes `redis` (port `6379`). Backend stores caches there:

| Key | Content |
|-----|---------|
| `waf:cache:rules:enabled` | Enabled detection rules (JSON) |
| `waf:cache:rules:pattern:{id}` | Regex pattern text + version |
| `waf:cache:apps:enabled` | Application host patterns (JSON) |

TTL: see `app.cache.ttl` in `application.yaml`.

## Week 2 — detect + score

After ingest, each event is evaluated against seeded regex rules (SQLI / XSS / PATH_TRAVERSAL).
Hits are stored in `detection_hits`; `web_events.risk_score` follows solution-design §7.4:

```text
base = min(100, sum(hit.weight))
freq_bonus = 0 if < 2 DetectionHits for same client_ip in 5 minutes
           = min(20, 2 * (count - 1)) otherwise
status_bonus = 5 if status ∈ {403,404,500} AND base > 0
score = min(100, base + freq_bonus + status_bonus)
```

```bash
# clean → score 0, no hits
curl -sS -o /dev/null -H 'Host: juice.lab.local' 'http://127.0.0.1/rest/products/search?q=apple'
sleep 2
# SQLi-like query → hits + score > 0
curl -sS -o /dev/null -G -H 'Host: juice.lab.local' \
  --data-urlencode 'q=1 OR 1=1' \
  'http://127.0.0.1/rest/products/search'
# XSS
curl -sS -o /dev/null -G -H 'Host: juice.lab.local' \
  --data-urlencode 'q=<script>x</script>' \
  'http://127.0.0.1/rest/products/search'
# Path traversal (query — Nginx often rejects literal ../ in path; PT rules scan RAW)
curl -sS -o /dev/null -G -H 'Host: juice.lab.local' \
  --data-urlencode 'file=../../etc/passwd' \
  'http://127.0.0.1/rest/products/search'
sleep 2
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/events' | head
# pick an id with score > 0
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/events/ID'
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/rules?enabled=true' | head
```

## Week 3 — alert, incident, JWT, SSE

Every `/api/**` route except `POST /api/oauth/tokens` requires a Bearer token.
Seeded lab users (BCrypt in Flyway V1):

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin` | ADMIN (rules write, triage, read) |
| `analyst` | `analyst` | ANALYST (triage + read, no rule write) |
| `viewer` | `viewer` | VIEWER (read + SSE) |

`app.llm.enabled` / `APP_LLM_ENABLED` defaults to **false**. A new incident is stored with `explanation_status=SKIPPED`. Nothing on the ingest path calls Ollama. With LLM enabled (`make stack-llm` or `APP_LLM_ENABLED=true`), a **newly created** incident is `PENDING` and explained after commit. `POST /api/incidents/{id}/explain` returns **202** and retries asynchronously.

Alert when `risk_score >= application.risk_threshold` (default 60), one alert per event. Incident policy (§7.5):

- `CRITICAL` (score ≥ 85) → `promote_reason=IMMEDIATE`
- `MEDIUM`/`HIGH` → attach to an OPEN incident for the same `(app_id, client_ip)` inside 5 minutes, otherwise wait until **3** unattached alerts in that window, then `AGGREGATE`
- fewer than 3 medium/high alerts stay on `GET /api/alerts` with `incidentId=null`

```bash
# token from the Week 1 login snippet above

# realtime (does not wait for an incident)
curl -N -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/stream/alerts'

# after a probe that scores >= 60
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/alerts'
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/incidents'
curl -sS -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/stats/overview'

# triage
curl -sS -X PATCH -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"ACK"}' 'http://127.0.0.1:8080/api/incidents/1'

# disable a rule; the enabled-rule cache is dropped so the next event uses the new row
curl -sS -X PATCH -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"enabled":false}' 'http://127.0.0.1:8080/api/rules/1'
```

Kafka topic `security-alerts` gets one JSON message per new alert (`incidentId` is null until that alert is promoted).

## Week 4 — SOC UI, labelled probes, optional Ollama

**Ethics:** probe scripts only call the **self-hosted** Juice Shop in this local Compose lab (`http://127.0.0.1` with `Host: juice.lab.local` or `shop.lab.local`). Do not point them at remote or third-party sites.

```bash
# Vendors ~/.gradle …/gradle-9.7.1 into backend/docker-gradle, then builds images
make stack-ui
# open http://127.0.0.1:3001  login admin / admin

# or Vite on the host (proxies /api to 127.0.0.1:8080; leave VITE_API_BASE_URL empty)
cd frontend && npm install && npm run dev
```

Probes: **37** scenarios in `scenarios.yaml` (**31** `expected=alert`, **6** clean). Browse: **50** benign GETs in `browse_clean.sh` (confirmed `=== done 50 ===`).

```bash
./datasets/probes/run.sh
./datasets/probes/browse_clean.sh
```

## Week 5 — đánh giá, báo cáo, slide

Labels: `datasets/ground_truth.csv` (37 probe + 50 browse). Eval scripts và số đo:

| Artifact | Path |
|----------|------|
| Juice TP/FP/FN | `datasets/eval/juice-lab.md` |
| OWASP Benchmark (external) | `datasets/eval/owasp-benchmark/` |
| Latency | `datasets/eval/latency.md` |
| Báo cáo nộp | `docs/report/final-report.md` (+ PDF) |
| Slide 8–10 phút | `docs/slides/demo-week05.md` |

```bash
# Lab sạch (xóa volume Postgres / Redis / nginx-logs) — bắt buộc trước Benchmark
make reset
make stack-ui                 # http://127.0.0.1:3001  admin/admin  (LLM off → SKIPPED)

./datasets/probes/browse_clean.sh
./datasets/probes/run.sh
python3 datasets/eval/score_juice.py

# Benchmark: tải expectedresults + sources (xem datasets/eval/owasp-benchmark/README.md)
make reset && make stack-ui
python3 datasets/eval/owasp-benchmark/run_eval.py

python3 datasets/eval/measure_latency.py
```

Tắt LLM (mặc định): incident mới có `explanation_status=SKIPPED`, alert/SSE vẫn chạy. Bật: `make stack-llm`.

### LLM explain (two paths)

| Mode | Command | New incident `explanation_status` |
|------|---------|-----------------------------------|
| Off (default) | `make stack-ui` | `SKIPPED` — alerts/SSE still work |
| On (host Ollama) | `APP_LLM_ENABLED=true OLLAMA_BASE_URL=http://host.docker.internal:11434 OLLAMA_MODEL=qwen3.5:9b docker compose up -d spring-security-backend` | `PENDING` → `READY` or `FAILED` |
| On (Compose Ollama) | Stop host Ollama on `:11434`, then `make stack-llm` and `docker compose exec ollama ollama pull llama3.2` | same |

SSE alerts are never blocked by the model. Attaching a later alert to an existing incident does **not** re-explain. If explain `FAILED`, `POST /api/incidents/{id}/explain` returns **202** and runs async. The SOC incident screen shows *AI hỗ trợ, có thể không chính xác*.

If the lab machine cannot pull a model, `FAILED` or `SKIPPED` still satisfies Week 4 AC-8/AC-9 — record that in the demo notes.

Explain prompt: `backend/src/main/resources/prompts/incident-explain.txt` (override with `LLM_PROMPT_FILE`).
