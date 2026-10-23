# Al Baraka Bank Assistant

Trilingual (FR/AR/EN) customer assistant for Al Baraka Bank Tunisia, built as a
governed RAG system: every answer is assembled from **governed business facts** and
**versioned, cited documents**, passes a grounding gate and an output PII scrub, and is
fully auditable by the bank's staff.

```
browser ─▶ nginx (Angular SPA, /api proxy) ─▶ Spring Boot edge ─▶ RAGLab (external, http://<ip>:<port>)
             security headers, SSE           auth, quotas,        retrieval → grounded answer → gates
                                             persistence, admin
                                                    │
                                                    ▼
                                              Postgres (schema owner)
```

| Component | Stack | Path |
|---|---|---|
| Backend | Java 21, Spring Boot 4, Flyway, JWT | `backend/` |
| Frontend | Angular 21 (standalone), Vitest | `frontend/` |
| Assistant engine | RAGLab, an external service the console addresses by ip:port | not in this repo |
| E2E | compose stack + smoke | `docker-compose.e2e.yml`, `e2e/` |

RAGLab is the **only** engine: this repository no longer ships an inference service.
It answers every customer turn, owns its own index and its own document screens, and
the backend never writes to it beyond the console's explicit actions.

## Run it

```bash
cp .env.docker.example .env      # set RAGLAB_URL (+ RAGLAB_TOKEN) and JWT_SECRET
docker compose up --build        # http://localhost:8080

# No RAGLab yet? The stack still starts: UI, auth, governance and the client
# portal work, and a chat turn is refused honestly instead of answered.

# A development population (staff at every permission level, four customers with
# accounts, cards, movements and financings) comes with the test stack, which
# turns DEV_SEED on; docker-compose.yml leaves it off, so a real deployment
# boots with no account and no known password.
docker compose -f docker-compose.test.yml up --build
```

The customer schema and those accounts are described in
[docs/CUSTOMER_DATA_MODEL.md](docs/CUSTOMER_DATA_MODEL.md).

Full-stack test on a workstation (seeded accounts, smoke suite): [HOST_TESTING.md](HOST_TESTING.md).
Details: [DOCKER.md](DOCKER.md). Without Docker: [VERIFY_LOCAL.md](VERIFY_LOCAL.md).

## Develop

```bash
# backend
cd backend && mvn -B verify
# frontend
cd frontend && npm ci && npm test -- --watch=false && npm run build
```

CI (`.github/workflows/ci.yml`) runs both on every push; the backend's coverage gate and
the frontend suite are hard gates. The end-to-end smoke (`e2e/run.sh`) needs a reachable
RAGLab, so it runs against a deployed environment rather than every build.

## Operate

- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — environments, secrets, TLS, backups, migrations, launch plan
- [docs/RUNBOOK.md](docs/RUNBOOK.md) — incidents, rotation, audit requests
- [docs/SONAR.md](docs/SONAR.md) — static analysis: the configuration in this repo, the local Docker one-liner, and the CI job that switches on with two secrets
- [docs/CUSTOMER_PII_REPORT.md](docs/CUSTOMER_PII_REPORT.md) — every personal datum the platform holds about a customer, who may see it, what leaves the bank, and the gaps to close before production
- [docs/ASSISTANT_IA.md](docs/ASSISTANT_IA.md) — running the Assistant IA section: connecting RAGLab at `<ip>:<port>`, its document screens, models &amp; keys, watching production
- [docs/THREAT_PASS_6.2.md](docs/THREAT_PASS_6.2.md) — security checklist
- [PLAN_TO_PROD.md](PLAN_TO_PROD.md) — the phased plan and what was decided
- [AGENTS.md](AGENTS.md) — working contract for contributors and coding agents

## Governance model (why this design)

- **Facts beat documents.** Rates, fees, eligibility live in `business_fact`, edited by
  `SUPER_ADMIN`/`ADMIN_DOCUMENTS` with a mandatory reason and a full audit trail.
- **Documents are versioned.** RAGLab owns publishing and versioning; every answer cites
  the passages it used, and the console can show that trace per exchange.
- **Routing is a sealed catalog.** Exemplars are curated in the admin UI and sealed into
  numbered releases; the newest seal is the version in force.
- **Refuse rather than guess.** No sources → honest knowledge-gap; out-of-scope → polite
  refusal; ungrounded text is never streamed.

## License

Proprietary — all rights reserved. See [LICENSE](LICENSE).
