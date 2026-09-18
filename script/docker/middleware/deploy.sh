#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

ensure_network() {
	docker network inspect springblade-backend >/dev/null 2>&1 \
		|| docker network create springblade-backend >/dev/null
}

run_compose() {
	service=$1
	shift
	docker compose \
		-p "springblade-$service" \
		--env-file "$SCRIPT_DIR/$service/.env" \
		-f "$SCRIPT_DIR/$service/compose.yml" \
		"$@"
}

wait_healthy() {
	service=$1
	container_id=$(run_compose "$service" ps --all -q "$service")

	if [ -z "$container_id" ]; then
		echo "$service container was not created" >&2
		exit 1
	fi

	attempt=0
	while [ "$attempt" -lt 60 ]; do
		status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
		case "$status" in
			healthy|running)
				echo "$service is $status"
				return 0
				;;
			exited|dead|unhealthy)
				run_compose "$service" logs --tail=100 "$service" >&2
				exit 1
				;;
		esac
		attempt=$((attempt + 1))
		sleep 5
	done

	run_compose "$service" logs --tail=100 "$service" >&2
	echo "Timed out waiting for $service" >&2
	exit 1
}

ensure_network

for service in mysql redis nacos sentinel; do
	run_compose "$service" config --quiet
	run_compose "$service" pull
	run_compose "$service" up -d
	wait_healthy "$service"
done

for service in mysql redis nacos sentinel; do
	run_compose "$service" ps
done
