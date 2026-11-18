#!/usr/bin/env bash
# End-to-end check of a RUNNING full stack (docker compose up). Used by CI and runnable by hand:
#
#     scripts/smoke-test.sh                       # against http://localhost:8088
#     scripts/smoke-test.sh https://my.domain     # against another address
#
# It talks only to the public web address, like a browser would.
set -uo pipefail

BASE="${1:-http://localhost:8088}"
fail=0
pass() { echo "  PASS  $1"; }
bad()  { echo "  FAIL  $1"; fail=1; }
check() { # check "description" expected actual
  if [ "$2" = "$3" ]; then pass "$1"; else bad "$1 (expected '$2', got '$3')"; fi
}
json() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }

echo "Smoke test against $BASE"

echo "- the web server"
page=$(curl -s "$BASE/")
case "$page" in *PulseWatch*) pass "index page is served";; *) bad "index page is served";; esac
check "client-side route falls back to index.html" "200" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/monitors/1")"
headers=$(curl -sI "$BASE/")
case "$headers" in *"Content-Security-Policy"*) pass "security headers are set";; *) bad "security headers are set";; esac

echo "- what must NOT be reachable from outside"
metrics=$(curl -s "$BASE/actuator/prometheus")
case "$metrics" in *pulsewatch_checks*|*jvm_memory*) bad "/actuator/prometheus is hidden";; *) pass "/actuator/prometheus is hidden";; esac
case "$(curl -s "$BASE/swagger-ui/index.html")" in *swagger*) bad "/swagger-ui is hidden";; *) pass "/swagger-ui is hidden";; esac

echo "- the API"
check "protected endpoint without a token" "401" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/monitors")"
email="smoke-$(date +%s)-$RANDOM@example.com"
reg=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"smoke-test-password\"}")
check "register" "201" "$(echo "$reg" | tail -1)"
token=$(echo "$reg" | head -1 | json "['token']")
auth=(-H "Authorization: Bearer $token")
check "list is empty for a new user" "0" "$(curl -s "$BASE/api/monitors" "${auth[@]}" | json ".__len__()")"

echo "- a monitor, end to end (needs the httpbin container of the local compose file)"
created=$(curl -s -X POST "$BASE/api/monitors" "${auth[@]}" -H 'Content-Type: application/json' \
  -d '{"name":"smoke","url":"http://httpbin:8080/status/200","intervalSeconds":10,"timeoutMs":3000}')
id=$(echo "$created" | json "['id']" 2>/dev/null || true)
if [ -z "$id" ]; then
  bad "create monitor (response: $created)"
else
  pass "create monitor $id"
  status="PENDING"
  for _ in $(seq 1 30); do
    status=$(curl -s "$BASE/api/monitors/overview" "${auth[@]}" | json "[0]['status']")
    [ "$status" = "UP" ] && break
    sleep 3
  done
  check "the first check ran and the monitor is UP" "UP" "$status"
  check "stats endpoint answers" "200" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/monitors/$id/stats" "${auth[@]}")"
fi

echo
if [ "$fail" = 0 ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED"; fi
exit $fail
