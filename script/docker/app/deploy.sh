#!/usr/bin/env sh

set -eu

IMAGE_TAG=${1:-}

if [ -z "$IMAGE_TAG" ]; then
	echo "Usage: sh deploy.sh <image-tag>" >&2
	exit 1
fi

case "$IMAGE_TAG" in
	*[!A-Za-z0-9._-]*)
		echo "Invalid image tag: $IMAGE_TAG" >&2
		exit 1
		;;
esac

if [ ! -f .env ]; then
	echo "Missing .env; copy .env.example and fill production values first." >&2
	exit 1
fi

tmp_file=$(mktemp)
if grep -q '^IMAGE_TAG=' .env; then
	sed "s/^IMAGE_TAG=.*/IMAGE_TAG=$IMAGE_TAG/" .env > "$tmp_file"
else
	cat .env > "$tmp_file"
	printf '\nIMAGE_TAG=%s\n' "$IMAGE_TAG" >> "$tmp_file"
fi
mv "$tmp_file" .env

docker compose -p springblade-app --env-file .env -f compose.yml config --quiet
docker compose -p springblade-app --env-file .env -f compose.yml pull
docker compose -p springblade-app --env-file .env -f compose.yml up -d --remove-orphans
docker compose -p springblade-app --env-file .env -f compose.yml ps

if ! command -v curl >/dev/null 2>&1; then
	echo "curl is required for the gateway liveness check." >&2
	exit 1
fi

liveness_url=$(sed -n 's/^GATEWAY_LIVENESS_URL=//p' .env | tail -n 1)
liveness_retries=$(sed -n 's/^GATEWAY_LIVENESS_RETRIES=//p' .env | tail -n 1)
liveness_interval=$(sed -n 's/^GATEWAY_LIVENESS_INTERVAL_SECONDS=//p' .env | tail -n 1)
liveness_url=${liveness_url:-http://127.0.0.1:8080/}
liveness_retries=${liveness_retries:-30}
liveness_interval=${liveness_interval:-10}

attempt=1
while [ "$attempt" -le "$liveness_retries" ]; do
	if curl --silent --show-error --output /dev/null --connect-timeout 5 "$liveness_url"; then
		echo "Gateway liveness check passed: $liveness_url"
		exit 0
	fi
	echo "Waiting for gateway liveness ($attempt/$liveness_retries)..."
	sleep "$liveness_interval"
	attempt=$((attempt + 1))
done

echo "Gateway liveness check failed after $liveness_retries attempts: $liveness_url" >&2
docker compose -p springblade-app --env-file .env -f compose.yml logs --tail=100 blade-gateway >&2
exit 1
