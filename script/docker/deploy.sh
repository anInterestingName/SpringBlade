#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
STACK=${1:-}
ACTION=${2:-up}

usage() {
	echo "Usage: sh deploy.sh <infra|app|ingress|network> <up|down|pull|config|deploy> [image-tag]"
	exit 1
}

ensure_network() {
	docker network inspect springblade-platform >/dev/null 2>&1 || docker network create springblade-platform
}

case "$STACK" in
	network)
		ensure_network
		;;
	infra)
		ensure_network
		case "$ACTION" in
			up) docker compose -p springblade-infra --env-file "$SCRIPT_DIR/infra/.env" -f "$SCRIPT_DIR/infra/compose.yml" up -d ;;
			down) docker compose -p springblade-infra --env-file "$SCRIPT_DIR/infra/.env" -f "$SCRIPT_DIR/infra/compose.yml" down ;;
			pull) docker compose -p springblade-infra --env-file "$SCRIPT_DIR/infra/.env" -f "$SCRIPT_DIR/infra/compose.yml" pull ;;
			config) docker compose -p springblade-infra --env-file "$SCRIPT_DIR/infra/.env" -f "$SCRIPT_DIR/infra/compose.yml" config --quiet ;;
			*) usage ;;
		esac
		;;
	app)
		ensure_network
		if [ "$ACTION" = "deploy" ]; then
			(cd "$SCRIPT_DIR/app" && sh deploy.sh "${3:-}")
		else
			case "$ACTION" in
				up) docker compose -p springblade-app --env-file "$SCRIPT_DIR/app/.env" -f "$SCRIPT_DIR/app/compose.yml" up -d ;;
				down) docker compose -p springblade-app --env-file "$SCRIPT_DIR/app/.env" -f "$SCRIPT_DIR/app/compose.yml" down ;;
				pull) docker compose -p springblade-app --env-file "$SCRIPT_DIR/app/.env" -f "$SCRIPT_DIR/app/compose.yml" pull ;;
				config) docker compose -p springblade-app --env-file "$SCRIPT_DIR/app/.env" -f "$SCRIPT_DIR/app/compose.yml" config --quiet ;;
				*) usage ;;
			esac
		fi
		;;
	ingress)
		ensure_network
		case "$ACTION" in
			up) docker compose -p springblade-ingress --env-file "$SCRIPT_DIR/ingress/.env" -f "$SCRIPT_DIR/ingress/compose.yml" up -d ;;
			down) docker compose -p springblade-ingress --env-file "$SCRIPT_DIR/ingress/.env" -f "$SCRIPT_DIR/ingress/compose.yml" down ;;
			pull) docker compose -p springblade-ingress --env-file "$SCRIPT_DIR/ingress/.env" -f "$SCRIPT_DIR/ingress/compose.yml" pull ;;
			config) docker compose -p springblade-ingress --env-file "$SCRIPT_DIR/ingress/.env" -f "$SCRIPT_DIR/ingress/compose.yml" config --quiet ;;
			*) usage ;;
		esac
		;;
	*) usage ;;
esac
