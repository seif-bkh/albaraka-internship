#!/usr/bin/env bash
# End-to-end smoke (7.3) against the docker-compose.e2e.yml stack, through nginx
# exactly as a browser would: public chat (sync + SSE), admin login, governed
# facts CRUD with audit trail, security headers, metrics and correlation ids.
# Requires: curl, python3 (json), and a reachable RAGLab (RAGLAB_URL on the
# backend). The chat checks assert a grounded answer from the bank's corpus, so
# they fail, correctly, when no engine is wired: that is the deployment state,
# not a regression. Exit code != 0 on the first failed check.
set -euo pipefail

BASE="${E2E_BASE_URL:-http://127.0.0.1:8080}"
ADMIN_EMAIL="${E2E_ADMIN_EMAIL:-admin@albaraka.tn}"
ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-postgres}"
pass=0; fail=0
ok()   { pass=$((pass+1)); echo "  ✅ $1"; }
bad()  { fail=$((fail+1)); echo "  ❌ $1"; [ -n "${2:-}" ] && echo "     $2"; }
json() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1], {"d": d}))' "$1"; }

echo "▶ SPA + security headers"
hdrs=$(curl -sS -D - -o /dev/null "$BASE/")
echo "$hdrs" | grep -qi "^content-security-policy:" && ok "CSP present" || bad "CSP missing"
echo "$hdrs" | grep -qi "^x-frame-options: DENY" && ok "X-Frame-Options DENY" || bad "X-Frame-Options missing"
echo "$hdrs" | grep -qi "^server: nginx/" && bad "nginx version leaked" || ok "server_tokens off"

echo "▶ Backend health through the proxy"
curl -fsS "$BASE/api/../actuator/health" >/dev/null 2>&1 && bad "actuator reachable via path traversal" || ok "no traversal to actuator"
rid=$(curl -sS -D - -o /dev/null "$BASE/api/chat/history?sessionId=none" | grep -i "^x-request-id:" | tr -d '\r' | cut -d' ' -f2)
[ -n "$rid" ] && ok "X-Request-Id echoed ($rid)" || bad "X-Request-Id missing"

echo "▶ Public chat — sync"
SID="e2e-$(date +%s)"
resp=$(curl -sS -H 'Content-Type: application/json' -X POST "$BASE/api/chat/message" \
  -d "{\"sessionId\":\"$SID\",\"message\":\"c'est quoi le financement Mourabaha\",\"langue\":\"fr\"}")
echo "$resp" | json 'len(d["reponse"]) > 0' | grep -q True && ok "answer returned" || bad "no answer" "$resp"
echo "$resp" | json 'd["reponse"]' | grep -q "Mourabaha" && ok "answer grounded on the corpus" || bad "answer not from corpus" "$resp"
echo "$resp" | json 'd["nombreMessages"] == 1 and d["messagesLimite"] > 1' | grep -q True && ok "quota counters travel with the answer" || bad "quota counters wrong" "$resp"

echo "▶ Public chat — out of scope is refused"
resp=$(curl -sS -H 'Content-Type: application/json' -X POST "$BASE/api/chat/message" \
  -d "{\"sessionId\":\"$SID\",\"message\":\"donne-moi une recette de cuisine\",\"langue\":\"fr\"}")
echo "$resp" | json 'd["reponse"]' | grep -qi "ne peux pas" && ok "out-of-scope refusal" || bad "did not refuse" "$resp"

echo "▶ Public chat — SSE stream"
sse=$(curl -sS -N -H 'Content-Type: application/json' -H 'Accept: text/event-stream' -X POST "$BASE/api/chat/message/stream" \
  -d "{\"sessionId\":\"$SID\",\"message\":\"c'est quoi le compte d'épargne\",\"langue\":\"fr\"}" --max-time 60)
echo "$sse" | grep -q '"type": *"delta"' && ok "delta frames" || bad "no delta frame" "$sse"
echo "$sse" | grep -q '"type": *"done"' && ok "done frame" || bad "no done frame" "$sse"

echo "▶ Admin login + refresh rotation + logout"
login=$(curl -sS -H 'Content-Type: application/json' -X POST "$BASE/api/auth/login" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"motDePasse\":\"$ADMIN_PASSWORD\"}")
tok=$(echo "$login" | json 'd["token"]'); rt=$(echo "$login" | json 'd["refreshToken"]')
[ -n "$tok" ] && ok "admin JWT issued" || bad "login failed" "$login"
ref=$(curl -sS -H 'Content-Type: application/json' -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$rt\"}")
rt2=$(echo "$ref" | json 'd["refreshToken"]')
[ -n "$rt2" ] && [ "$rt2" != "$rt" ] && ok "refresh rotates the refresh token" || bad "refresh did not rotate" "$ref"
code=$(curl -sS -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$rt\"}")
[ "$code" = "401" ] && ok "replayed refresh token rejected (401)" || bad "replay returned $code"
curl -sS -H 'Content-Type: application/json' -X POST "$BASE/api/auth/logout" -d "{\"refreshToken\":\"$rt2\"}" >/dev/null
code=$(curl -sS -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$rt2\"}")
[ "$code" = "401" ] && ok "logged-out refresh token rejected (401)" || bad "post-logout refresh returned $code"

echo "▶ Client portal (/api/client/me — V8)"
ccode=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/api/client/me")
[ "$ccode" = "401" ] && ok "client profile needs auth (401 anonymous)" || bad "anonymous got $ccode on /api/client/me"
clogin=$(curl -sS -H 'Content-Type: application/json' -X POST "$BASE/api/auth/login" \
  -d '{"email":"client@albaraka.tn","motDePasse":"client123"}')
ctok=$(echo "$clogin" | json 'd["token"]')
[ -n "$ctok" ] && ok "client JWT issued" || bad "client login failed" "$clogin"
cauth=(-H "Authorization: Bearer $ctok" -H 'Content-Type: application/json')
ref=$(curl -sS "${cauth[@]}" "$BASE/api/client/me" | json 'd["referenceClient"]')
[ -n "$ref" ] && ok "client profile returned (ref $ref)" || bad "profile missing referenceClient"
iban=$(curl -sS "${cauth[@]}" "$BASE/api/client/me/comptes" | json 'len(d)')
[ "$iban" -ge 1 ] 2>/dev/null && ok "client comptes listed ($iban)" || bad "comptes empty or malformed"
# Staff must never read the client portal: admin token → 403.
scode=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $tok" "$BASE/api/client/me")
[ "$scode" = "403" ] && ok "staff rejected on client portal (403)" || bad "staff got $scode on /api/client/me"

echo "▶ Governed facts"
auth=(-H "Authorization: Bearer $tok" -H 'Content-Type: application/json')
code=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/api/admin/facts")
[ "$code" = "401" ] && ok "facts need auth (401 anonymous)" || bad "anonymous got $code on /api/admin/facts"
key="e2e_$(date +%s)"
created=$(curl -sS "${auth[@]}" -X POST "$BASE/api/admin/facts" \
  -d "{\"domain\":\"financement\",\"key\":\"$key\",\"value\":\"valeur e2e\",\"factType\":\"terms\",\"language\":\"fr\"}")
fid=$(echo "$created" | json 'd["id"]')
[ -n "$fid" ] && ok "fact created (#$fid)" || bad "fact create failed" "$created"
code=$(curl -sS -o /dev/null -w '%{http_code}' "${auth[@]}" -X PUT "$BASE/api/admin/facts/$fid" \
  -d "{\"domain\":\"financement\",\"key\":\"$key\",\"value\":\"valeur 2\",\"factType\":\"terms\",\"language\":\"fr\"}")
[ "$code" = "400" ] && ok "update without reason rejected (400)" || bad "update without reason returned $code"
curl -sS "${auth[@]}" -X PUT "$BASE/api/admin/facts/$fid" \
  -d "{\"domain\":\"financement\",\"key\":\"$key\",\"value\":\"valeur 2\",\"factType\":\"terms\",\"language\":\"fr\",\"reason\":\"e2e\"}" >/dev/null
curl -sS "${auth[@]}" -X DELETE "$BASE/api/admin/facts/$fid" -d '{"reason":"e2e cleanup"}' >/dev/null
hist=$(curl -sS "${auth[@]}" "$BASE/api/admin/facts/$fid/history")
echo "$hist" | json '[h["action"] for h in d]' | grep -q "DEACTIVATE.*UPDATE.*CREATE" && ok "audit trail CREATE→UPDATE→DEACTIVATE" || bad "audit trail wrong" "$hist"

echo "▶ Metrics"
if [ -n "${E2E_METRICS_URL:-}" ]; then
  m=$(curl -fsS "$E2E_METRICS_URL" 2>/dev/null || echo "")
else
  m=$(docker compose -f docker-compose.e2e.yml exec -T backend curl -fsS http://localhost:8081/actuator/prometheus 2>/dev/null || echo "")
fi
echo "$m" | grep -q 'chat_turns_total{' && ok "chat_turns_total exported" || bad "chat_turns_total missing"

echo
echo "e2e: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
