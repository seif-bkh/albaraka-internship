# Threat pass — Phase 6.2 (re-audit of the new design)

**Date:** 2026-09-03 · **Baseline:** AUDIT.md (commit `b278b88`) · **Reviewed:** branch
`arena/01a06850-bank-assistant` after 3.7 / 5.4 / 6.1.

Method: every finding in AUDIT.md §Security re-checked against the current tree
(`git grep`, controller-by-controller review of authorisation, secret scan of the whole
history, upload path review). Items fixed in this pass are marked **fixed now**.

## Trust boundaries

```
browser ──HTTPS──▶ nginx (frontend) ──/api/──▶ Spring Boot edge ──X-Service-Token──▶ agent (internal only)
                                                     │  ▲                              │
                                                     │  └── X-Ingestion-Token ─────────┘ (worker → /api/internal)
                                                     └──▶ Postgres(pgvector) / Redis (internal network)
```

The agent is never published (compose: no host port; K8s: ClusterIP only). All customer
traffic goes through the Java edge, which owns quota, persistence and auth.

## Checklist

| # | Threat / control | Status | Evidence |
|---|---|---|---|
| S1 | Wildcard CORS | ✅ closed | `SecurityConfig` uses `app.cors.allowed-origins` (env, comma-list). `*` only in `AllowedHeaders`, which is safe. nginx same-origin proxy means no preflight in prod. |
> **Context change (2026-10-22).** The in-house Python agent, its ingestion queue and Redis
> were removed; RAGLab (external) answers every turn. Rows S2, S4 and S8 below concern code
> that no longer exists and are kept for the audit trail. The surviving attack surface is the
> Spring Boot edge, its Postgres schema, and the service-token-protected calls to RAGLab.

| S2 | Second unauthenticated chat door (`/api/v1/chat` Python) | ✅ closed | Agent exposes only `/invoke`, `/invoke/stream`, `/health`, `/metrics`, all behind `X-Service-Token` except health/metrics. |
| S3 | Legacy `ChatController` → `AgentService` calling a non-existent `question` contract without service token | ✅ **fixed now** | Deleted both (dead code, wrong payload, no token, no quota). |
| S4 | Service/ingestion token compared with `==` (timing oracle) | ✅ **fixed now** | `hmac.compare_digest` (agent) and `MessageDigest.isEqual` (`IngestionResultController`). |
| S5 | Committed secrets / history | ✅ clean | Only `.env.example` / `.env.docker.example` ever added; regex scan for API keys/AWS/`password=` finds nothing outside tests. `JWT_SECRET` is required (`:?`) in compose. |
| S6 | Dev scratch with a shared password (`create_users.py`, `PasswordGenerator.java` — `azerty123`) | ✅ **fixed now** | Removed from the repo. Seed users must be created via the admin UI or a one-off script outside the tree. |
| S7 | Uploads: path traversal, MIME/extension, size | ✅ closed | `DocumentController.storeFile`: basename only, extension + MIME allow-lists, 25 MB (`spring.servlet.multipart`), UUID stored name, `target.getParent().equals(dir)` guard. Upload dir is never a static webroot. |
| S8 | Uploads: content sniffing (magic bytes) | ⚠️ open (low) | MIME comes from the client. The Python ingestion parses with a PDF/DOCX library that rejects garbage, so impact is DoS-on-worker at worst. Add magic-byte check if uploads ever come from non-staff. |
| S9/S10 | PII leaves the model unscrubbed | ✅ closed | `PiiScrubber.scrub` is the last gate in both sync and stream paths (`server/app.py`). |
| S11 | Admin endpoints authorisation | ✅ closed | All controllers under `/api/admin/**`, `/api/documents`, `/api/users`, `/api/stats`, `/api/clients` carry `@PreAuthorize`; governance writes restricted to `SUPER_ADMIN`/`ADMIN_DOCUMENTS`; deactivations need a reason (audit trail). |
| S12 | JWT handling | ✅ | HS256 with 256-bit secret from env, stateless access tokens; refresh tokens persisted by `jti`, single-use rotation with family reuse detection, logout + admin revocation (3.6); expired/revoked token → 401 not 500. |
| S13 | Public chat abuse | ✅ | Per-session token + message quota checked **before** the agent call; new-session IP rate limit; `X-Forwarded-For` honoured only from nginx. |
| S14 | Browser hardening headers | ✅ **fixed now** | nginx: `server_tokens off`, `X-Content-Type-Options`, `X-Frame-Options DENY`, `Referrer-Policy`, `Permissions-Policy`, CSP (`script-src 'self'`, `frame-ancestors 'none'`). Spring: frame deny + referrer policy. |
| S15 | Metrics exposure | ✅ | `/actuator/prometheus` requires `SUPER_ADMIN` unless `METRICS_PUBLIC=true` (internal scrape network). Agent `/metrics` is internal-only by network. |
| S16 | Swagger UI public | ⚠️ accepted for now | `/v3/api-docs`, `/swagger-ui` are `permitAll`. They expose schema, not data. Gate behind a profile before internet exposure (tracked in 8.2). |
| S17 | Redis without auth | ⚠️ tracked 8.2 | Compose Redis is on the internal network without password; production must set `requirepass` + TLS. |
| S18 | `chat_history*.html` (7 MB + 1 MB session transcripts) in the repo | ✅ removed (2026-10-22) | `git rm` + `.gitignore` entry. The earlier "no customer data, still noise" verdict was **wrong on one point**: `chat_history.html` contained a **live Groq API key** pasted into the transcript, plus the old `motdepasse123` DB password. Deletion does not purge Git history — the key must be revoked at the provider. |
| S19 | Log injection via correlation headers | ✅ | `CorrelationFilter.sanitize` allows ≤ 64 chars of `[A-Za-z0-9._:-]`; anything else is replaced by a minted id. |
| S20 | Prompt injection via retrieved documents | ✅ mitigated | Answers are built structurally from retrieval + governed facts and pass the grounding verifier; ungrounded output is refused, never streamed. |

## Residual risks (owner decisions)

1. **S8** magic-byte sniffing on upload — cheap, do it if non-staff can ever upload.
2. **S16** Swagger in prod — hide behind `springdoc.api-docs.enabled=false` in the prod profile.
3. **S17** Redis auth/TLS — in 8.2 environment split.
4. ~~**S18** delete `chat_history.html` from Git.~~ Done 2026-10-22; rotate the Groq key that was inside it.
