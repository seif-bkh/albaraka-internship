# Agentic coding research — dynamic staff access control

**Researched:** 2026-10-14
**Task:** Dynamic staff roles and permissions for Documents and RAGLab
**Status:** Research completed; guidance applied to this task and recorded as a repository rule in [`AGENTS.md`](../AGENTS.md), §2.

## Sources and takeaways

1. [1](https://github.com/agentskills/agentskills) — Agent Skills open format. A skill is a portable directory with `SKILL.md` instructions and optional references/scripts; skills are discovered by metadata and loaded when useful. I used this as a discovery format, not as a reason to import external code into the project.
2. [2](https://github.com/agentskills/agentskills/blob/main/docs/specification.mdx) — Specification: keep the skill front matter descriptive, and separate short trigger metadata from task instructions and supporting material. This supports keeping `AGENTS.md` rules short, while putting task findings in focused Markdown docs.
3. [1](https://github.com/openai/skills/blob/main/skills/.system/skill-creator/SKILL.md) — OpenAI Skill Creator: keep guidance concise and set the degree of freedom according to risk (flexible for judgment, precise for fragile operations). Applied by treating the access model as fixed, testing security boundaries, and not prescribing irrelevant scaffolding.
4. [2](https://github.com/addyosmani/agent-skills/blob/main/skills/spec-driven-development/SKILL.md) — Spec-Driven Development skill: establish objective, boundaries, dependencies, and verification before implementation. Applied through the user-approved plan and this implementation record.
5. [3](https://github.com/addyosmani/agent-skills/blob/main/skills/security-and-hardening/SKILL.md) — Security and Hardening skill: identify trust boundaries/assets, think through privilege escalation and repudiation, and turn abuse cases into tests. Applied by testing direct API calls, role escalation, final-super-admin protection, and audit records.
6. [3](https://devguide.owasp.org/en/04-design/02-web-app-checklist/07-access-controls/) — OWASP Developer Guide access-control checklist: use deny-by-default, least privilege, check all requests server-side, audit authorization changes, and test authorization rules. These are implementation acceptance criteria here, not optional front-end conventions.

## Difficult backend issues researched

1. [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) — `@EnableMethodSecurity` activates `@PreAuthorize` on Spring-managed classes/methods via Spring AOP; the security starter alone does not turn it on. The existing application configuration already enables it, so the implementation keeps endpoint checks there and adds tests against Spring proxies rather than testing annotations by direct, unproxied construction.
2. [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) — `@Lock` can attach a JPA lock mode to repository query methods. The role/user repositories use `PESSIMISTIC_WRITE` queries inside the same service/controller transaction as role assignment and membership changes.
3. [PostgreSQL — Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html) — row locks block conflicting writers/lockers until transaction end. A count-only “last Super Admin” check can race, so every mutation that could reduce active Super Admin membership first locks the stable `SUPER_ADMIN` role row; the count and write then share the transaction.

These references informed the locking and method-security implementation, but do not substitute for runtime verification. GitHub Actions CI ran and passed the backend build/tests; the Super Admin tests verify lock acquisition and rejection using mocks. A real concurrent PostgreSQL interleaving and Flyway migration still need host verification because CI tests use H2 `create-drop` with Flyway disabled.

## How this research affected the work

- The permission catalogue is application-defined and fixed; only role composition and staff-role assignment are data-driven. A free-form permission key cannot safely authorize an endpoint that has not explicitly adopted that capability.
- The system has a permanently protected `SUPER_ADMIN` boundary. Custom roles receive only the supported Documents/RAGLab capability authorities; they cannot manufacture `ROLE_SUPER_ADMIN` or access-management privileges.
- The frontend may hide inaccessible navigation/actions, but all authorization is enforced by Spring on every protected request.
- Database role and permission changes are audited, with safe failure when a role/permission is unknown.
- No third-party skills, code, or dependencies were installed. External skills were read as advisory workflow guidance only; project instructions and the user-approved requirements remain authoritative.

## New standing rule

On 2026-10-14, the maintainer asked that agentic coding use internet research for relevant coding-agent skills, inspiration, and hard problems, and that this be captured in `AGENTS.md`. The rule was added under §2: for substantial/multi-file/security/schema work, research relevant agentic skills and primary technical sources, use web research when the repository is insufficient, review external material rather than trusting it, and record sources and applied decisions in task-specific Markdown documentation. UI work still has its separate web-banking research requirement in §9.
