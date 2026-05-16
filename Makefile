.PHONY: up down build rebuild deploy pull logs ps setup

ENV      ?= local
ENV_FILE := --env-file .env.$(ENV)

# ホスト側のアップロードディレクトリを作成する（権限設定は Docker entrypoint が担当）
setup:
	@UPLOADS=$$(grep -s UPLOADS_LOCATION .env.$(ENV) | cut -d= -f2 | tr -d ' \r'); \
	UPLOADS=$${UPLOADS:-/srv/mapoker/data/uploads}; \
	echo "Ensuring $$UPLOADS/avatars ..."; \
	mkdir -p "$$UPLOADS/avatars" || true

up:
	docker compose $(ENV_FILE) up -d

down:
	docker compose down

build:
	docker compose build --no-cache

rebuild:
	docker compose down
	docker compose build --no-cache
	$(MAKE) setup ENV=$(ENV)
	docker compose $(ENV_FILE) up -d

# レジストリから最新イメージを pull して再起動（ローカルビルドなし）
pull:
	docker compose $(ENV_FILE) pull

deploy:
	docker compose $(ENV_FILE) pull
	docker compose down
	$(MAKE) setup ENV=$(ENV)
	docker compose $(ENV_FILE) up -d

logs:
	docker compose logs -f

ps:
	docker compose ps
