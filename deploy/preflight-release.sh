#!/usr/bin/env bash
# Read-only production release checks. Never prints secret values.
set -Eeuo pipefail
deploy=/opt/cxj-blog/deploy
cd "$deploy"
for tool in docker python3 mktemp; do command -v "$tool" >/dev/null; done
test -f .env
dc=(docker compose --project-directory "$deploy" --env-file "$deploy/.env" -f "$deploy/docker-compose.prod.yml")
for file in docker-compose.flow.yml docker-compose.frontend.flow.yml; do
  if test -f "$file"; then dc+=(-f "$deploy/$file"); fi
done
tmp=$(mktemp -d)
chmod 700 "$tmp"
trap 'rm -f "$tmp/runtime.yml" "$tmp/check.py"; rmdir "$tmp"' EXIT
cat > "$tmp/runtime.yml" <<'YAML'
services:
  backend:
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
YAML
sql() {
  "${dc[@]}" exec -T postgres sh -c 'exec psql -X -w -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1' <<< "BEGIN READ ONLY; $1; COMMIT;" | sed '/^BEGIN$/d; /^COMMIT$/d'
}
echo '=== Database ==='
sql "SELECT 'postgres=' || current_setting('server_version'); SELECT 'migration=' || version || ',success=' || success FROM flyway_schema_history ORDER BY installed_rank; SELECT 'role=' || role || ',count=' || count(*) FROM sys_user WHERE deleted_at IS NULL GROUP BY role"
schema=$(sql "SELECT max(version::int) FROM flyway_schema_history WHERE success")
case "$schema" in 2|3|4) ;; *) echo 'BLOCKED: unexpected migration version'; exit 1;; esac
conflicts=$(sql "SELECT count(*) FROM sys_user WHERE lower(email)='1158189673@qq.com' AND deleted_at IS NULL AND role<>'ADMIN'")
if test "$conflicts" != 0; then echo 'BLOCKED: administrator email belongs to a non-admin; manual ownership review required'; exit 1; fi
demotions=$(sql "SELECT count(*) FROM sys_user WHERE role='ADMIN' AND lower(email)<>'1158189673@qq.com'")
echo "V4_other_admin_accounts_changed_to_USER=$demotions"
has_password=$(sql "SELECT count(*) FROM sys_user u WHERE lower(email)='1158189673@qq.com' AND deleted_at IS NULL AND role='ADMIN' AND status='ACTIVE' AND to_jsonb(u)->>'password_hash' IS NOT NULL")
disabled=$(sql "SELECT count(*) FROM sys_user WHERE lower(email)='1158189673@qq.com' AND deleted_at IS NULL AND status<>'ACTIVE'")
if test "$disabled" != 0; then echo 'BLOCKED: administrator account is disabled'; exit 1; fi
cat > "$tmp/check.py" <<'PY'
import json, sys, re
from urllib.parse import urlsplit
env = json.load(sys.stdin)['services']['backend'].get('environment', {})
errors = []
def require(name, valid):
    print(('OK: ' if valid else 'BLOCKED: ') + name)
    if not valid: errors.append(name)
def value(name): return str(env.get(name) or '')
require('production profile', value('SPRING_PROFILES_ACTIVE') == 'prod')
if sys.argv[1] == '0':
    password = value('ADMIN_INITIAL_PASSWORD')
    require('ADMIN_INITIAL_PASSWORD configured with valid length', len(password) >= 8 and len(password.encode('utf-8')) <= 72 and bool(password.strip()))
else: print('OK: existing active administrator has a password')
require('AUTH_EMAIL_ENABLED=true for complete authentication release', value('AUTH_EMAIL_ENABLED').lower() == 'true')
require('GEETEST_ENABLED=true for complete authentication release', value('GEETEST_ENABLED').lower() == 'true')
require('AUTH_CODE_SECRET configured (32+ characters)', len(value('AUTH_CODE_SECRET')) >= 32)
for key in ['MAIL_FROM','TENCENTCLOUD_SECRET_ID','TENCENTCLOUD_SECRET_KEY','GEETEST_CAPTCHA_ID','GEETEST_CAPTCHA_KEY']:
    require(key + ' configured', bool(value(key).strip()))
require('SES_REGION supported', value('SES_REGION') in ['ap-hongkong','ap-guangzhou'])
require('SES_TEMPLATE_ID positive integer', value('SES_TEMPLATE_ID').isdigit() and int(value('SES_TEMPLATE_ID')) > 0)
require('COS_ENABLED boolean', value('COS_ENABLED').lower() in ('', 'true', 'false'))
if value('COS_ENABLED').lower() == 'true':
    for key in ['COS_SECRET_ID', 'COS_SECRET_KEY']:
        require(key + ' configured', bool(value(key).strip()))
    require('COS_REGION valid', bool(re.fullmatch(r'[a-z]+-[a-z]+(?:-[a-z]+)?', value('COS_REGION'))))
    require('COS_BUCKET valid', bool(re.fullmatch(r'[a-z0-9-]+-[0-9]+', value('COS_BUCKET'))))
    url = urlsplit(value('COS_PUBLIC_BASE_URL'))
    require('COS_PUBLIC_BASE_URL HTTPS origin', url.scheme == 'https' and bool(url.hostname)
            and not url.username and not url.password and not url.query and not url.fragment and url.path in ('', '/'))
else: print('INFO: COS image uploads disabled')
sys.exit(1 if errors else 0)
PY
echo '=== Candidate runtime configuration (values are not printed) ==='
"${dc[@]}" -f "$tmp/runtime.yml" config --format json | python3 "$tmp/check.py" "$has_password"
echo '=== Nginx syntax ==='
"${dc[@]}" exec -T nginx nginx -t
echo 'PREFLIGHT_PASS: configuration presence only; actual SES/Geetest delivery and backup restore still require verification.'
