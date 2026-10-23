# Admin access-control UX research

**Researched:** 2026-10-14
**Feature:** Super-Admin-managed staff roles for Document Management and RAGLab
**Purpose:** Satisfy `AGENTS.md` §9's requirement to research modern web-banking UX before changing admin UI.

## References

1. [1](https://merchanthelp.bankofamerica.com/Merchant_Services_Online_Banking_Access_and_User_Management) — Bank of America Merchant Services online banking user/access management. The page describes user creation, administrator delegation, access levels with descriptions, service/location-scoped access, and role-conditioned navigation. It also supports copying an existing user's access.
2. [5](https://docs.stripe.com/get-started/account/teams/roles) — Stripe team roles. It distinguishes an account-wide Administrator from an IAM Administrator who manages users/roles but cannot use operational product features, and reserves Super Administrator assignment to an existing Super Administrator.
3. [1](https://docs.stripe.com/get-started/account/teams/custom-roles) — Stripe custom roles. Permissions are grouped into named jobs, then assembled into a named role; users receive the role. A permission can be reduced from write to read at a narrower level. Changes to a base role are not silently copied into independent custom roles.
4. [1](https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction_attribute-based-access-control.html) — AWS IAM access-control guidance: grant the minimum permissions needed for the job, with each job-function policy explicitly listing its supported resources/actions.
5. [3](https://devguide.owasp.org/en/04-design/02-web-app-checklist/07-access-controls/) — OWASP access-control checklist: deny by default, least privilege, access checks on every request, audit, and automated authorization tests.

## Patterns worth reusing

- **Separate access administration from operational access.** Only the protected Super Admin manages staff, role definitions, and permission grants. A custom RAGLab role must not gain role-management authority just because it can manage RAGLab settings.
- **Explain the consequence of each level.** The UI should describe what a permission enables, not expose only technical permission keys. In this project, “RAGLab Manage” is explicitly full console access, including key/model/configuration changes and operational actions; “View” is read-only.
- **Group permissions by product area and action.** Use a compact Documents group with Read/Create/Update/Delete plus Process/Reindex, and a RAGLab group with View/Run/Manage. Keep the fixed technical permission identifiers separate from their human-readable descriptions.
- **Make privilege changes reviewable.** Show role name, description, assigned staff, selected permissions, and the effect of Manage before saving. Protect `SUPER_ADMIN`; make deletion/deactivation safe and preserve an audit history.
- **Navigation follows authorization.** Hide unavailable modules/actions for clarity, but keep backend checks authoritative. A denied direct API call must remain denied even if a browser route or button is manipulated.
- **Avoid permission sprawl.** Keep the user-facing permission set aligned to meaningful actions. The fixed `RAGLAB_RUN` capability preserves today's “can run a model query but cannot change settings” tier; `RAGLAB_CONSOLE_MANAGE` remains the broad all-actions capability.

## Project-specific decisions

- The admin UI will follow the existing French back-office conventions, use semantic form labels and permission explanations, and use logical CSS properties/RTL-friendly structure so the new page can render in Arabic direction without layout breakage.
- A two-part administration experience will be used: a role/permission editor for creating and changing dynamic role types, and the existing staff-user manager will assign one of those roles. This keeps role design separate from account editing.
- Permissions are not free-form database strings: only the code-supported, seeded catalogue is selectable. This prevents a role from displaying a permission that no endpoint actually enforces.
- The separate Documents/Ingestion area uses Documents permissions. The RAGLab console's own feed is a RAGLab operation; full RAGLab Manage covers it.
- Existing role bundles will be seeded from today's grants, not widened silently. A compatibility `RAGLAB_RUN` capability separates retrieval/query use from sensitive settings management.
- Read-only configuration and masked key-presence status remain available under RAGLab View to preserve the viewer role; actual key writes/deletes and all configuration mutations require Manage. Secret values are never returned to the UI.

## Explicitly not copied

- Bank of America's account/location assignment matrix is not added: the current application has no branch- or account-scoped staff data model.
- Stripe's multi-role union is not adopted for staff accounts in this slice. Each staff user keeps one role assignment, avoiding accidental union of high-risk capabilities.
- No generic “full access” checkbox is applied to ordinary dynamic roles. Only Super Admin retains role-management authority; full RAGLab Manage is clearly labelled and auditable.
