#!/usr/bin/env bash
# Run on the production host, not on the Flow build node.
set -Eeuo pipefail
umask 077

archive=${1:?Usage: bash deploy-backend.sh /absolute/path/package.tgz}
deploy=/opt/cxj-blog/deploy
base="$deploy/docker-compose.prod.yml"
override="$deploy/docker-compose.flow.yml"
frontend_override="$deploy/docker-compose.frontend.flow.yml"
for tool in docker tar sha256sum flock mktemp python3; do command -v "$tool" >/dev/null; done
test -f "$archive"
test -f "$base"
test -f "$deploy/.env"
cd "$deploy"

# Shared by all future production deployment scripts, including frontend.
exec 9>"$deploy/.deployment.lock"
flock -w 600 9
bash "$deploy/preflight-release.sh"
dc=(docker compose --project-directory "$deploy" --env-file "$deploy/.env" -f "$base")
if test -f "$override"; then dc+=(-f "$override"); fi
if test -f "$frontend_override"; then dc+=(-f "$frontend_override"); fi
"${dc[@]}" config --quiet
backend_id=$("${dc[@]}" ps -q backend)
test -n "$backend_id" || { echo 'Existing backend is required for first adoption and rollback.'; exit 1; }
old_image=$(docker inspect --format '{{.Image}}' "$backend_id")
for service in postgres frontend nginx; do
  id=$("${dc[@]}" ps -q "$service")
  test -n "$id" && test "$(docker inspect --format '{{.State.Running}}' "$id")" = true
done

mkdir -p "$deploy/releases" "$deploy/backups"
release=$(mktemp -d "$deploy/releases/backend-XXXXXXXX")
if test -f "$override"; then cp "$override" "$release/previous-backend-compose.yml"; fi
members=$(tar -tzf "$archive")
member=$(printf '%s\n' "$members" | grep -E '^(\./)?app\.jar$')
test "$(printf '%s\n' "$member" | wc -l)" -eq 1
# Extract only the expected regular JAR member via stdout; no archive paths are written.
tar -xOzf "$archive" "$member" > "$release/app.jar"
test -s "$release/app.jar"
chmod 644 "$release/app.jar"
digest=$(sha256sum "$release/app.jar")
digest=${digest%% *}
new_image="cxj-blog-backend:flow-$digest"
cat > "$release/Dockerfile" <<'DOCKERFILE'
FROM eclipse-temurin:21-jre-alpine
ENV SPRING_PROFILES_ACTIVE=prod
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY app.jar /app/app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
DOCKERFILE
docker build -t "$new_image" "$release"

# Keep a protected logical backup before Flyway can change the schema.
backup="$deploy/backups/$(basename "$release").dump"
"${dc[@]}" exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
"${dc[@]}" exec -T postgres pg_restore --list < "$backup" > /dev/null
printf 'Database backup: %s\n' "$backup"
schema_before=$("${dc[@]}" exec -T postgres sh -c 'exec psql -X -w -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "SELECT max(version::int) FROM flyway_schema_history WHERE success"')
case "$schema_before" in 2|3|4) ;; *) echo 'Unexpected schema version; stop for migration review.' >&2; exit 1;; esac

write_override() {
  local tmp
  tmp=$(mktemp "$deploy/.flow-XXXXXXXX")
  printf 'services:\n  backend:\n    image: "%s"\n' "$1" > "$tmp"
  cat >> "$tmp" <<'ENVIRONMENT'
    environment:
      SPRING_PROFILES_ACTIVE: prod
      ADMIN_INITIAL_PASSWORD: ${ADMIN_INITIAL_PASSWORD:-}
      AUTH_EMAIL_ENABLED: ${AUTH_EMAIL_ENABLED:-false}
      AUTH_CODE_SECRET: ${AUTH_CODE_SECRET:-}
      MAIL_FROM: ${MAIL_FROM:-}
      TENCENTCLOUD_SECRET_ID: ${TENCENTCLOUD_SECRET_ID:-}
      TENCENTCLOUD_SECRET_KEY: ${TENCENTCLOUD_SECRET_KEY:-}
      SES_REGION: ${SES_REGION:-ap-hongkong}
      SES_TEMPLATE_ID: ${SES_TEMPLATE_ID:-219249}
      GEETEST_ENABLED: ${GEETEST_ENABLED:-false}
      GEETEST_CAPTCHA_ID: ${GEETEST_CAPTCHA_ID:-}
      GEETEST_CAPTCHA_KEY: ${GEETEST_CAPTCHA_KEY:-}
      COS_ENABLED: ${COS_ENABLED:-false}
      COS_SECRET_ID: ${COS_SECRET_ID:-}
      COS_SECRET_KEY: ${COS_SECRET_KEY:-}
      COS_REGION: ${COS_REGION:-ap-nanjing}
      COS_BUCKET: ${COS_BUCKET:-}
      COS_PUBLIC_BASE_URL: ${COS_PUBLIC_BASE_URL:-}
ENVIRONMENT
  mv "$tmp" "$override"
}
dc=(docker compose --project-directory "$deploy" --env-file "$deploy/.env" -f "$base" -f "$override")
if test -f "$frontend_override"; then dc+=(-f "$frontend_override"); fi
healthy() {
  local response
  for ((attempt=0; attempt<36; attempt++)); do
    if response=$("${dc[@]}" exec -T frontend wget -T 5 -qO- http://backend:8080/actuator/health 2>/dev/null) &&
      printf '%s' "$response" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then return 0; fi
    sleep 5
  done
  return 1
}
reload_nginx() {
  "${dc[@]}" exec -T nginx nginx -t && "${dc[@]}" exec -T nginx nginx -s reload
}
rollback() {
  local result=$?
  trap - ERR
  set +e
  local schema_after
  schema_after=$("${dc[@]}" exec -T postgres sh -c 'exec psql -X -w -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "SELECT max(version::int) FROM flyway_schema_history WHERE success"')
  if test "$schema_before" != 4 && { test "$schema_after" = 4 || test -z "$schema_after"; }; then
    echo 'Deployment failed after a potentially incompatible migration. Old image rollback is blocked; restore requires manual database/application recovery.' >&2
    printf 'Protected backup: %s; previous image: %s\n' "$backup" "$old_image" >&2
    exit "$result"
  fi
  echo 'Deployment failed; attempting rollback of application image. Database is NOT automatically restored.' >&2
  if test -f "$release/previous-backend-compose.yml"; then
    cp "$release/previous-backend-compose.yml" "$override"
  else
    printf 'services:\n  backend:\n    image: "%s"\n' "$old_image" > "$override"
  fi
  "${dc[@]}" up -d --no-deps --no-build --pull never backend
  if healthy && reload_nginx; then
    echo 'Previous image restored and healthy; this pipeline still fails.' >&2
  else
    echo 'Rollback health check failed; manual intervention required.' >&2
  fi
  exit "$result"
}
trap rollback ERR
write_override "$new_image"
"${dc[@]}" config --quiet
"${dc[@]}" up -d --no-deps --no-build --pull never backend
healthy
reload_nginx
trap - ERR
printf 'DEPLOY_SUCCESS image=%s\n' "$new_image"
