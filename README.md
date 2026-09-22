# WAF Log Analyzer

Lab: Juice Shop → Nginx (JSON access log) → Filebeat → Kafka (`raw-web-logs`) → Spring ingest/normalize → PostgreSQL.

## Quick start (Week 1 ingest)

```bash
docker compose up -d --build
# wait for postgres + kafka + spring-security-backend healthy

curl -sS -H 'Host: juice.lab.local' http://127.0.0.1/
sleep 3
curl -sS 'http://127.0.0.1:8080/api/events' | head
```

Backend local (infra already up):

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || echo /opt/homebrew/opt/openjdk@25)
./gradlew bootRun
```

Kafka bootstrap from host: `localhost:29092`. DB: `localhost:5432` / `waf` / `waf` / db `waf_analyzer`.

## Smoke checklist

1. Request via Nginx creates a line in topic `raw-web-logs` (&lt; 5s).
2. Spring consumer persists `web_events`.
3. `GET /api/events` returns the event (`host`, `path`, `appId`).
4. `Host: shop.lab.local` maps to a different `appId` than `juice.lab.local`.
