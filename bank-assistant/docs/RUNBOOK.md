# Operations runbook

Audience: on-call engineer. Each entry: symptom → diagnose → act. Correlate with
`requestId` (response header `X-Request-Id`), `sessionId`, `turnId` across both services.

## 1. Health at a glance

```bash
curl -s https://<host>/api/../actuator/health            # via edge (should be 404: not exposed)
docker compose exec backend curl -s localhost:8081/actuator/health
curl -s "$RAGLAB_URL/health"                            # the engine itself
docker compose ps
```

## 2. "Service IA temporairement indisponible" to customers

1. `docker compose logs --tail=200 backend | grep -E "RAGLab|Cannot reach"`.
2. Is the engine reachable? **Assistant IA → Santé & diagnostic** (or
   `GET /api/admin/agent/diagnostics`). Typical causes: wrong `RAGLAB_URL` from inside the
   backend container (container-local `localhost`), RAGLab down, or 401 from a
   `RAGLAB_TOKEN` mismatch.
3. Engine up but slow: check its own latency/status page; the backend budgets minutes for a
   turn, so p95 slowness is the engine's, not the edge's.
4. Mitigation: fix the address/token (console or `RAGLAB_URL`) or bring the service back.
   While it is down customers get an honest refusal, never a fabricated answer.

## 3. Answers are wrong / outdated

1. Which document? The **stored** exchange carries the citations (source + version) for
   the admin journal — the customer sees none, by design; check **Console RAGLab →
   Documents** (did the newer version get indexed there?).
2. If a document was withdrawn or re-versioned and the old answer persists, the semantic
   cache is RAGLab's — invalidate it there, and record the action in the console journal.
3. If a business number is wrong: fix it in **Faits gouvernés** (facts beat documents at
   answer build). Changes apply on the next turn; no restart.
4. If a question is filed under the wrong domain/intent in the stored exchanges, adjust the
   exemplars in **Taxonomie** and seal a release; the newest seal is the version in force.

### 3.5 A visitor is refused although the corpus holds the answer

A refusal is the engine's verdict on its own evidence, not a fact about the bank. RAGLab is
the only engine, so there is no second opinion to ask; what the edge does is make sure the
customer reads the bank's own sentence (`RefusalMessage`, fr/ar/en) instead of a paragraph
about "the supplied documents".

1. `/admin/monitoring` shows the refusal and its reason
   (`RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE`, `RAGLAB_REFUSED_NO_CONTEXT`, …).
2. The engine's index is behind its corpus → publish/rebuild through **Console RAGLab →
   Documents**, then record it (`POST /api/admin/agent/corpus/rebuild/raglab`).
3. Retrieval finds no candidate at all → the passage really is not indexed. Index it.
   Never invent the missing content.
4. A 401 or "Aucune adresse configurée" is configuration, not content: fix `RAGLAB_URL` /
   `RAGLAB_TOKEN` (§2 of `docs/ASSISTANT_IA.md`).

The refusal reason, the retrieved sources and the trace stay stored on the exchange, so
the journal still shows what happened on a turn whose answer was replaced — what is not
stored is the engine's refusal prose, which no customer needed to read either.

## 4. A document is not answering / index looks stale

1. Ingestion is **RAGLab's job** now. Start at **Console RAGLab → Documents**: the row's
   state and any error text are whatever that service reports.
2. Upload again (or re-run its index action) from that screen; the console records the
   action and the reason in its journal.
3. If the service says the index was rebuilt out of band, record it here
   (`POST /api/admin/agent/corpus/rebuild/raglab`) so the next operator knows why the
   numbers moved.
4. Still wrong after a successful index? Open **Qualité des réponses**: search the question,
   inspect the chunks, and check whether the quote exists in the corpus at all.

## 5. Quota / abuse

- Symptoms: many 429s in backend logs (`New-session rate limit`) or `chat_turns_total{outcome="quota"}` spikes.
- Per-session limits are in `PublicChatController` constants; IP rate limit for new
  sessions likewise. Block abusive IPs at the edge; don't raise limits under attack.

## 6. Database

- **Restore drill**: restore latest base backup + WAL to a scratch instance, point a
  staging backend at it, run `./e2e/run.sh` read-only checks. Quarterly.
- **Flyway failed on boot**: backend exits; read the failing migration in the log; fix
  forward with a new migration (never edit an applied one); if the migration partially
  applied, `flyway repair` semantics are handled by re-running after manual fix.
- **Disk**: chunks table growth; archive old document versions (they are kept for audit
  of past answers) only per the retention policy agreed with compliance.

## 7. Secrets rotation

| Secret | Impact | Procedure |
|---|---|---|
| `JWT_SECRET` | all staff sessions invalid | rolling restart of backend in a window |
| `RAGLAB_TOKEN` | edge↔engine 401s during skew | set on RAGLab first (accepts new), then the backend; restart the backend |
| provider keys | none if valid | rotate inside RAGLab's own deployment |

## 8. Security incident

1. **One compromised account:** `POST /api/users/{id}/revoke-sessions` (SUPER_ADMIN) or
   reset the password/role in the admin UI — both revoke every refresh token of that
   user (`refresh_tokens.revoke_reason = admin_revoked`). Access tokens die within
   `JWT_EXPIRATION_MS` (1 h in prod).
   `SELECT subject, revoke_reason, count(*) FROM refresh_tokens WHERE revoke_reason='reuse_detected' GROUP BY 1,2;`
   lists token-theft signals (a consumed refresh token was replayed).
2. **Widespread compromise:** rotate `JWT_SECRET` immediately (kills all sessions), then
   the other secrets.
3. Preserve logs (JSON, with correlation ids) and the `business_fact_audit` /
   `catalog_release` tables — they are the regulatory trail of what the assistant knew.
4. Disable the public chat at the edge (feature flag / 503 on `/api/chat/*`) if content
   integrity is in doubt.
5. Post-incident: re-run `docs/THREAT_PASS_6.2.md` checklist.

Housekeeping: `DELETE FROM refresh_tokens WHERE expires_at < now() - interval '30 days';`
(monthly; rows are kept after revocation for audit).

## 9. Regulatory audit requests

Answer "what did the assistant say and why, on date D":
- `messages` (question, answer, sources, intention, timestamps) by `session_id`;
- cited documents + versions (`documents`; the ingestion journal is historical —
  ingestion happens in RAGLab now);
- governed facts as of D (`business_fact` + `business_fact_audit`);
- catalogue release in force at D (`catalog_release.sealed_at`).
