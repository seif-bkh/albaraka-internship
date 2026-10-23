# Dynamic staff roles and permissions — implementation record

**Owner:** Arena Agent Mode
**Started:** 2026-10-14
**Status:** Implementation complete; frontend and backend CI green; PostgreSQL migration and host smoke still pending
**Approved scope:** User approved the plan on 2026-10-14 and asked for end-to-end completion without additional approval pauses.

## Goal

Replace fixed staff-role gating in the Document Management and RAGLab/Assistant IA areas with database-backed role definitions. Super Admin can create, edit, deactivate, and assign roles composed from a code-defined permission catalogue. The permission catalogue itself remains fixed because every permission must map to a server-enforced action.

Customer account access (`UTILISATEUR`) and unrelated back-office modules remain unchanged.

## Approved capability model

### Documents

- `DOCUMENTS_READ`
- `DOCUMENTS_CREATE`
- `DOCUMENTS_UPDATE`
- `DOCUMENTS_DELETE`
- `DOCUMENTS_PROCESS` — re-ingestion, re-indexing, and cache invalidation
- `DOCUMENTS_INGESTION_READ` — ingestion journal/status visibility

### RAGLab

- `RAGLAB_CONSOLE_VIEW` — read-only status/configuration inspection and non-mutating operations
- `RAGLAB_CONSOLE_RUN` — model-backed query/retrieval operations, separate from settings changes
- `RAGLAB_CONSOLE_MANAGE` — all RAGLab Console actions; includes View and Run, including settings/keys/models, engine, RAGLab feed, ingestion/index actions, evaluations, and diagnostics

The `RAGLAB_CONSOLE_RUN` tier preserves the existing ability for supervision/document staff to run queries without giving them model/key/engine administration. Legacy role assignments will be migrated to equivalent permission bundles rather than being silently broadened.

### Governance invariants

- `SUPER_ADMIN` is system-reserved: custom permission assignments cannot create it or obtain role/user administration rights.
- Keep at least one active Super Admin; never allow the last one to be deactivated, deleted, or demoted.
- Role deactivation is soft; a role assigned to staff cannot be removed in a way that strands accounts.
- Only Super Admin can manage roles, permissions-to-role grants, and staff role assignments.
- Permission changes are audited. Unknown roles/permissions fail closed.
- Client roles remain separate; `Role.java` remains available for the customer role path even if staff role storage becomes dynamic.

## Research and recordkeeping decisions

- `docs/AGENTIC_CODING_RESEARCH.md` records Agent Skills / security workflow research and the requested new standing rule in `AGENTS.md` §2.
- `docs/ADMIN_ACCESS_CONTROL_UX_RESEARCH.md` records banking/admin access UX research and project-specific adaptations before UI changes.
- External material is advisory and is reviewed rather than copied/executed blindly.

## Implementation sequence

1. Add the required web-research rule to `AGENTS.md` and create this record/research docs.
2. Add a Flyway migration creating roles, fixed permissions, role-permission links, and an access-change audit trail; seed current staff role codes and their legacy grants. Expand the staff role column from the old enum/check-constrained values without deleting existing user rows. Keep customer storage unchanged.
3. Add backend role/permission entities, repositories, DTOs, role service and Super-Admin-only APIs. Validate fixed permission identifiers; generate stable role codes; audit changes; protect Super Admin and role assignments.
4. Update authentication/user management so roles are resolved from the database. The server returns current effective authorities; authorized requests load current permissions from server-side role data. Keep customer principal construction unchanged.
5. Replace hard-coded role checks only on Document/ingestion and RAGLab/related Assistant IA endpoints. Preserve other modules' existing role checks.
6. Add Angular role-management UI, make the existing user form load/assign database-backed roles, and gate routes/navigation/actions using returned permissions. UI checks remain convenience only; API checks are authoritative. Make new layout RTL-safe.
7. Add/adjust backend and frontend tests, docs, run available repository verification, commit and push only to the session branch, then monitor CI.

## Planned file areas

### Add

- `backend/src/main/resources/db/migration/V14__staff_roles_permissions.sql`
- Role/permission/audit models, repositories, fixed permission-code catalog, DTOs, service, and admin API controller under `backend/src/main/java/com/albaraka/backend/`
- Frontend access-control API service and an admin Roles & Permissions page under `frontend/src/app/`
- Backend/frontend authorization tests
- The two research documents above and this implementation record

### Change

- Staff user role model and repository query; auth principal/authority construction; seed admin; `UserController` and auth profile response
- `DocumentController`, `AdminOperationsController`, `AdminRaglabController`, and relevant `AdminAgentController` guards
- Angular auth/permission service, route guards/routes, sidebar, staff-user editor, Documents/Ingestion UI, and RAGLab UI action visibility
- Schema smoke/security tests and this log as work progresses

### Delete

- No files or existing user data are planned for deletion. The migration will drop/replace the old staff-role check constraint as needed to store role codes, and scoped controllers will replace old hard-coded role expressions with permission checks. The Java role enum remains for client roles.

## Verification plan

- Backend: migration/schema smoke test; role service tests; HTTP method-security tests for expected allow/deny cases; Super Admin invariants; legacy permission mapping.
- Frontend: permission service/guard tests, role editor tests, production build, and existing test suite.
- Manual host test: log in as the seeded Super Admin, create a Documents read-only role and a RAGLab View/Run role, assign staff accounts, verify disabled UI actions and direct-API 403s, then verify a RAGLab Manage role can perform full console actions and customer portal access remains unchanged.
- CI must be monitored to completion after pushing.

## Environment discovered before implementation

- Working branch: `arena/87a1028b-bank-assistant`; tree clean before this task.
- Node.js `v22.22.3` and npm `10.9.8` are present.
- Java, Maven, and Docker are absent from the sandbox. Backend verification may therefore need the documented Linux-host verifier; the exact limitation and any available static checks will be recorded here.

## Progress log

- 2026-10-14 — Inspected current Angular/Spring/JPA/Flyway implementation and existing roles; confirmed Documents/RAGLab authorization is hard-coded.
- 2026-10-14 — Researched agentic coding skills, OWASP guidance, Bank of America online-bank access management, and Stripe role/custom-role patterns; findings recorded in research docs.
- 2026-10-14 — User approved the end-to-end implementation plan before implementation began.
- 2026-10-14 — Resolved the RAGLab ingestion overlap: newly composed Documents Process roles do not gain RAGLab feed/index access; RAGLab Manage is required. The seeded `ADMIN_DOCUMENTS` role retains its prior specific feed/index actions through a narrow code-level compatibility guard that requires RAGLab View plus the relevant migrated per-action Documents grants, rather than receiving the broader Manage bundle.

## Implemented design and migration

- `V14__staff_roles_permissions.sql` adds database-backed staff roles, the fixed permission catalogue, role grants, and access-change audit history. The user role column now references the staff-role catalogue; the customer `clients.role` enum and check constraint are untouched.
- Existing effective Documents/RAGLab grants are preserved: `ADMIN_DOCUMENTS` keeps document CRUD/process plus ingestion history and RAGLab View/Run; `ADMIN_SUPERVISION` keeps ingestion history and RAGLab View/Run; `VIEWER` keeps document read, ingestion history, and RAGLab View. These intermediate roles do not receive RAGLab Manage. Super Admin is protected and receives all supported permissions.
- Staff authorities are reloaded from the database when the backend authenticates each JWT request. Manage expands to View and Run; role deactivation disables its assigned staff principals. Refreshing a session for a disabled staff account/role is rejected and revokes that subject’s outstanding refresh tokens. The new disabled-principal check is explicitly staff-only; customer token handling remains unchanged.
- Super Admin alone can access `/api/admin/access-control/**` and `/api/users/**`. Roles can only combine code-defined catalogue keys; customer portal roles are excluded from staff role editing. Role/member writes are audited, role assignment/deactivation uses row locks, and the final active Super Admin cannot be demoted, deactivated, or deleted.
- `/api/documents/**` and standalone ingestion/cache endpoints use per-action Documents permissions; the ingestion-history read is separate from document-list read and process. Updating a document that would queue reindexing also requires process permission.
- `/api/admin/raglab/**` and the relevant `/api/admin/agent/**` control plane use View/Run/Manage. Read-only configuration, feed/status inspection, and masked key status require View; model-backed queries use Run; key/settings/engine/provider changes, feed writes, indexing controls, evaluations, and other high-impact RAGLab mutations require Manage for dynamic roles. `DOCUMENTS_PROCESS` alone never authorizes a RAGLab endpoint. To honor the no-revocation migration requirement without promoting `ADMIN_DOCUMENTS` to Manage, that seeded historical role has a narrow compatibility path for its old feed CRUD/index actions, and only while it retains RAGLab View plus the matching migrated Documents permission. This exception does not apply to newly composed roles. Exact citation lookup remains View-only because it makes no model/embedding call and preserves the former Viewer capability. Embedding sanity check is Run-gated.
- Angular routes, navigation, role-specific actions, user assignment, and the new RTL-safe role editor reflect effective permissions. Frontend checks are convenience only; Spring method security is authoritative. The role editor explains Documents read dependencies and RAGLab Manage's implied View/Run permissions.
- `HOST_TESTING.md` now includes the temporary-role and direct-API acceptance scenario for verifying migration, action separation, high-impact restrictions, and customer access.

## Verification log

- 2026-10-14 — `frontend/npm test -- --watch=false`: **passed**, 21 test files / 118 tests. This includes role-editor document permission dependencies, custom-staff authority landing, the RAGLab feed access boundary, and the existing frontend suite.
- 2026-10-14 — `frontend/npm run build`: **passed**, production bundle generated without Angular warnings.
- 2026-10-14 — `frontend/npm ci`: **passed**; npm reported 26 dependency audit findings (8 moderate, 15 high, 3 critical). No dependency upgrade/audit fix was applied because it is outside this access-control change.
- 2026-10-14 — GitHub Actions [CI run 38553415874](https://github.com/seif-bkh/bank-assistant/actions/runs/38553415874) passed. The backend job ran `mvn -B clean verify`, compiling and executing the new method-security, role-service, staff-principal, refresh-token, final-Super-Admin, and RAGLab feed/index boundary tests; it also confirmed Flyway auto-configuration is packaged in the JAR.
- 2026-10-14 — The RAGLab boundary tests assert Documents Process alone is denied, legacy `ADMIN_DOCUMENTS` retains only its matching migrated feed/index operations, and RAGLab Manage can perform them. The Super Admin unit tests verify lock acquisition/rejection with mocks.
- 2026-10-14 — PostgreSQL migration execution and real concurrent membership interleavings still require host verification: CI test configuration uses H2 `create-drop` with Flyway disabled, and the Super Admin lock tests are mocked.
- 2026-10-14 — `git diff --check`: **passed** (no whitespace errors).
- 2026-10-14 — Removed an unused `RouterLink` import that had triggered Angular `NG8113`; the latest production build is warning-free.

### Required host verification

Run `./verify-local.sh backend` on the Linux host (first run may take several minutes; it starts the verification PostgreSQL/Redis containers and writes backend logs to `verify/out/`). Then stop the verification services with `docker compose -f docker-compose.verify.yml down`. Run the dynamic-role/API and customer smoke scenario in `HOST_TESTING.md` against the test stack; stop it with `docker compose -f docker-compose.test.yml down`. The seed test accounts are local-only demo credentials and must not be reused outside the test environment.
