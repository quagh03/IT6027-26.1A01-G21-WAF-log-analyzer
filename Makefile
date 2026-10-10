# Lab commands. `make` prints the list.
# Day to day: make up, then make backend in another terminal.
# Do not start Compose profile llm (it binds port 11434 and clashes with host Ollama).

.DEFAULT_GOAL := help

COMPOSE := docker compose
API     := http://127.0.0.1:8080
INFRA   := postgres redis kafka kafka-init kafka-ui nginx filebeat juice-shop

JAVA_HOME ?= $(shell /usr/libexec/java_home -v 25 2>/dev/null || echo /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home)
APP_LLM_ENABLED ?= true

GRADLE_BIN := $(shell ls -d "$(HOME)/.gradle/wrapper/dists/gradle-9.7.1-bin/"*/gradle-9.7.1/bin/gradle 2>/dev/null | head -n 1)
ifeq ($(strip $(GRADLE_BIN)),)
GRADLE := ./gradlew
else
GRADLE := $(GRADLE_BIN)
endif

.PHONY: help up down reset ps logs backend backend-stop ui ui-stop test probes browse token rules stack stack-ui smoke

help:
	@printf '%s\n' \
		'Lab' \
		'  make up                 Hạ tầng Compose. Không build image backend, không profile llm' \
		'  make down               Dừng Compose, giữ volume' \
		'  make reset              Dừng Compose và xóa volume (Postgres, Redis, log Nginx)' \
		'  make ps                 Trạng thái container' \
		'  make logs svc=nginx     Log một service. Bỏ svc để xem tất cả' \
		'' \
		'App trên host' \
		'  make backend            Spring bootRun, cổng 8080, LLM mặc định bật' \
		'  make backend-stop       Dừng process đang nghe 8080' \
		'  make ui                 Vite tại http://127.0.0.1:5173' \
		'  make ui-stop            Dừng process đang nghe 5173' \
		'' \
		'Kiểm tra' \
		'  make smoke              GET / qua Nginx với Host juice và shop' \
		'  make token              In JWT admin' \
		'  make probes             scenarios.yaml qua Nginx' \
		'  make browse             50 request sạch' \
		'  make test               Unit test backend' \
		'  make rules              Eval rule offline' \
		'' \
		'Image trong Compose (bước tải Gradle có thể treo)' \
		'  make stack              Compose đầy đủ, gồm spring-security-backend' \
		'  make stack-ui           stack cộng profile ui tại http://127.0.0.1:3001'

up:
	$(COMPOSE) up -d $(INFRA)

down:
	$(COMPOSE) down

reset:
	$(COMPOSE) down -v

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

stack:
	$(COMPOSE) up -d --build

stack-ui:
	$(COMPOSE) --profile ui up -d --build

smoke:
	curl -fsS -o /dev/null -w 'juice %{http_code}\n' -H 'Host: juice.lab.local' http://127.0.0.1/
	curl -fsS -o /dev/null -w 'shop %{http_code}\n' -H 'Host: shop.lab.local' http://127.0.0.1/
