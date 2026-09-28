# WAF Log Analyzer

Lab: Juice Shop → Nginx (JSON access log) → Filebeat → Kafka (`raw-web-logs`) → Spring ingest/normalize → PostgreSQL → `GET /api/events`.

## Prerequisites

- Docker Compose
- Java 25 (only if you run the backend with `./gradlew bootRun` instead of the Compose service)
- Add to `/etc/hosts` (optional but useful):  
  `127.0.0.1 juice.lab.local shop.lab.local`

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

`app.llm.enabled` defaults to **false**. A new incident is stored with `explanation_status=SKIPPED`. Nothing on the ingest path calls Ollama. `POST /api/incidents/{id}/explain` returns **501** until Week 4.

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
