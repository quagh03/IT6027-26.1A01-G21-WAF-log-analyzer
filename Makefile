# Lab commands. `make` prints the list.
# Day to day: make up, then make backend in another terminal.
# Full Compose UI: make stack-ui (vendors host Gradle so the image build does not download it).

.DEFAULT_GOAL := help

COMPOSE := docker compose
API     := http://127.0.0.1:8080
INFRA   := postgres redis kafka kafka-init kafka-ui nginx filebeat juice-shop

JAVA_HOME ?= $(shell /usr/libexec/java_home -v 25 2>/dev/null || echo /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home)
APP_LLM_ENABLED ?= false

GRADLE_HOME_DIST := $(shell ls -d "$(HOME)/.gradle/wrapper/dists/gradle-9.7.1-bin/"*/gradle-9.7.1 2>/dev/null | head -n 1)
GRADLE_BIN := $(if $(GRADLE_HOME_DIST),$(GRADLE_HOME_DIST)/bin/gradle,)
ifeq ($(strip $(GRADLE_BIN)),)
GRADLE := ./gradlew
else
GRADLE := $(GRADLE_BIN)
endif

.PHONY: help up down stop reset destroy ps logs backend backend-stop ui ui-stop test probes browse \
	token rules prepare-docker-gradle stack stack-ui stack-llm smoke eval-juice eval-benchmark \
	eval-latency simulate-monitoring

# Always include optional profiles so ui/llm containers are not left behind.
COMPOSE_DOWN := $(COMPOSE) --profile ui --profile llm

help:
	@printf '%s\n' \
		'Lab' \
		'  make up                 Hạ tầng Compose. Không build image backend, không profile llm' \
		'  make down / stop        Dừng toàn bộ Compose (kể cả ui/llm), giữ volume' \
		'  make reset / destroy    Dừng Compose + xóa volume (Postgres, Redis, nginx-logs, ollama)' \
		'  make ps                 Trạng thái container' \
		'  make logs svc=nginx     Log một service. Bỏ svc để xem tất cả' \
		'' \
		'App trên host' \
		'  make backend            Spring bootRun, cổng 8080, LLM mặc định tắt (SKIPPED)' \
		'  make backend-stop       Dừng process đang nghe 8080' \
		'  make ui                 Vite tại http://127.0.0.1:5173' \
		'  make ui-stop            Dừng process đang nghe 5173' \
		'' \
		'Kiểm tra' \
		'  make smoke              GET / qua Nginx với Host juice và shop' \
		'  make token              In JWT admin' \
		'  make probes             scenarios.yaml qua Nginx' \
		'  make browse             50 request sạch' \
		'  make simulate-monitoring  Random probe chain (Ctrl+C dừng)' \
		'  make test               Unit test backend' \
		'  make rules              Eval rule offline' \
		'  make eval-juice         TP/FP/FN Juice (cần lab up)' \
		'  make eval-benchmark     OWASP Benchmark external (lab sạch khuyến nghị)' \
		'  make eval-latency       Đo request → alert' \
		'' \
		'Image trong Compose' \
		'  make prepare-docker-gradle  Chép Gradle 9.7.1 từ máy vào backend/docker-gradle' \
		'  make stack              Compose đầy đủ, gồm spring-security-backend' \
		'  make stack-ui           stack + profile ui tại http://127.0.0.1:3001' \
		'  make stack-llm          stack-ui + LLM qua Ollama trên host (không pull image ollama)'

up:
	$(COMPOSE) up -d $(INFRA)

# Stop lab containers; keep named volumes (data survives).
down stop:
	$(COMPOSE_DOWN) down --remove-orphans

# Stop lab and destroy volumes (clean slate for eval / demo).
reset destroy:
	$(COMPOSE_DOWN) down -v --remove-orphans

ps:
	$(COMPOSE) ps

logs:
	$(COMPOSE) logs -f --tail=200 $(svc)

backend:
	cd backend && JAVA_HOME="$(JAVA_HOME)" APP_LLM_ENABLED="$(APP_LLM_ENABLED)" "$(GRADLE)" bootRun

backend-stop:
	@pid=$$(lsof -nP -t -iTCP:8080 -sTCP:LISTEN || true); \
	if [ -n "$$pid" ]; then kill $$pid && echo "stopped $$pid"; else echo "8080 is free"; fi

ui:
	cd frontend && { [ -d node_modules ] || npm install; } && npm run dev -- --host 127.0.0.1 --port 5173

ui-stop:
	@pid=$$(lsof -nP -t -iTCP:5173 -sTCP:LISTEN || true); \
	if [ -n "$$pid" ]; then kill $$pid && echo "stopped $$pid"; else echo "5173 is free"; fi

test:
	cd backend && JAVA_HOME="$(JAVA_HOME)" "$(GRADLE)" test

probes:
	./datasets/probes/run.sh

browse:
	./datasets/probes/browse_clean.sh

token:
	@curl -fsS -X POST '$(API)/api/oauth/tokens' \
		-H 'Content-Type: application/json' \
		-d '{"username":"admin","password":"admin"}' \
		| python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'

rules:
	cd detection-rule-generator && \
		if [ -x .venv/bin/python ]; then .venv/bin/python -m pipeline evaluate; else python3 -m pipeline evaluate; fi

prepare-docker-gradle:
	@if [ -z "$(GRADLE_HOME_DIST)" ]; then \
		echo "Gradle 9.7.1 not found under ~/.gradle/wrapper/dists. Run ./backend/gradlew once on the host."; \
		exit 1; \
	fi
	rm -rf backend/docker-gradle
	mkdir -p backend/docker-gradle
	cp -R "$(GRADLE_HOME_DIST)/." backend/docker-gradle/
	@test -x backend/docker-gradle/bin/gradle
	@echo "Vendored Gradle $$(backend/docker-gradle/bin/gradle -q --version 2>/dev/null | awk '/^Gradle /{print $$2; exit}') → backend/docker-gradle"

stack: prepare-docker-gradle
	$(COMPOSE) up -d --build

stack-ui: prepare-docker-gradle
	$(COMPOSE) --profile ui up -d --build

# LLM via host Ollama (fast / GPU). Requires `ollama serve` on :11434.
# Optional Compose Ollama: APP_LLM_ENABLED=true OLLAMA_BASE_URL=http://ollama:11434 \
#   $(COMPOSE) --profile ui --profile llm up -d --build
stack-llm: prepare-docker-gradle
	APP_LLM_ENABLED=true \
	OLLAMA_BASE_URL=$${OLLAMA_BASE_URL:-http://host.docker.internal:11434} \
	OLLAMA_MODEL=$${OLLAMA_MODEL:-qwen3.5:9b} \
		$(COMPOSE) --profile ui up -d --build

smoke:
	curl -fsS -o /dev/null -w 'juice %{http_code}\n' -H 'Host: juice.lab.local' http://127.0.0.1/
	curl -fsS -o /dev/null -w 'shop %{http_code}\n' -H 'Host: shop.lab.local' http://127.0.0.1/

eval-juice:
	python3 datasets/eval/score_juice.py

eval-benchmark:
	python3 datasets/eval/owasp-benchmark/run_eval.py

eval-latency:
	python3 datasets/eval/measure_latency.py

# Random probe chain for live SOC monitoring demo (Ctrl+C to stop).
simulate-monitoring:
	python3 datasets/probes/simulate_monitoring.py
