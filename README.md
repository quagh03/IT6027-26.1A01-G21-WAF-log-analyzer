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

# normalized events
curl -sS 'http://127.0.0.1:8080/api/events'
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
3. `GET /api/events` returns the event (`host`, `path`, `appId` / `appName`, `status`).
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
curl -sS 'http://127.0.0.1:8080/api/events' | head
# pick an id with score > 0
curl -sS 'http://127.0.0.1:8080/api/events/ID'
curl -sS 'http://127.0.0.1:8080/api/rules?enabled=true' | head
```
