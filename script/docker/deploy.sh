#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
STACK=${1:-}
ACTION=${2:-up}

usage() {
	echo "Usage: sh deploy.sh <infra|ingress|network> <up|down|pull|config>"
	echo "       sh deploy.sh app <service> <up|down|pull|config>"
	exit 1
}

ensure_network() {
	docker network inspect springblade-platform >/dev/null 2>&1 || docker network create springblade-platform
}

ensure_app_networks() {
	docker network inspect springblade-backend >/dev/null 2>&1 || docker network create springblade-backend
	docker network inspect springblade-ingress >/dev/null 2>&1 || docker network create springblade-ingress
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
		SERVICE=${2:-}
		APP_ACTION=${3:-config}
		[ -n "$SERVICE" ] || usage
		APP_DIR="$SCRIPT_DIR/app/$SERVICE"
		[ -d "$APP_DIR" ] || { echo "Unknown app service: $SERVICE" >&2; exit 1; }
		[ -f "$APP_DIR/.env" ] || { echo "Missing $APP_DIR/.env" >&2; exit 1; }
		ensure_app_networks
		case "$APP_ACTION" in
			up) docker compose -p "springblade-$SERVICE" --env-file "$APP_DIR/.env" -f "$APP_DIR/compose.yml" up -d ;;
			down) docker compose -p "springblade-$SERVICE" --env-file "$APP_DIR/.env" -f "$APP_DIR/compose.yml" down ;;
			pull) docker compose -p "springblade-$SERVICE" --env-file "$APP_DIR/.env" -f "$APP_DIR/compose.yml" pull ;;
			config) docker compose -p "springblade-$SERVICE" --env-file "$APP_DIR/.env" -f "$APP_DIR/compose.yml" config --quiet ;;
			*) usage ;;
		esac
		;;
	ingress)
		ensure_app_networks
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
