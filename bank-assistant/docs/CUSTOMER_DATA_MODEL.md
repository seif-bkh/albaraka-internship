# Customer data model — two populations, one assistant

This document describes the customer side of the schema (migration `V15`), why it looks
like a core-banking extract rather than a demo table, and how much of it may ever reach
RAGLab. It also lists the development accounts that are seeded on request.

## 1. Two kinds of account, one direction of authority

The product has exactly two populations:

| | Staff | Customer |
|---|---|---|
| Table | `users` | `clients` |
| Role | a row of `admin_roles` (foreign key) | the fixed marker `UTILISATEUR` |
| Created by | a `SUPER_ADMIN` | self-registration, or the bank |
| Lands on | `/admin/**` | `/portal` |

Roles exist only among staff, and this is enforced in the database, not only in the
service layer. `admin_roles` carries the check `is_staff OR code = 'UTILISATEUR'`, so no
new role can be created as a non-staff role; `clients` carries
`CHECK (role = 'UTILISATEUR')`, so a direct `UPDATE` cannot hand a customer a staff role
either. The role catalogue itself is managed by the general manager (the `SUPER_ADMIN`
account) from **Utilisateurs & rôles** — creating a role, choosing its permissions,
assigning it to an employee, and disabling it once nobody holds it. A role that still has
holders cannot be deactivated, and the last `SUPER_ADMIN` cannot be removed: both rules are
already implemented in `AdminRoleService` and audited in `admin_access_audit`.

## 2. Why the customer schema looks like this

The bank never told us its core-banking layout, so this is a deliberate approximation of
what a Tunisian Islamic retail bank typically holds: a customer record with KYC state and
branch, accounts with an IBAN, a RIB and an available balance, cards with a category and
limits, movements with both a booking date and a value date, standing beneficiaries, and
financing contracts booked as Islamic products rather than loans. The vocabulary is the
one a data team would recognise — `IBAN`, `RIB`, `value date`, `available balance`, `KYC`,
`murabaha` — so a real extract can be mapped onto these tables without a translation
layer, and so a future integration replaces the seed rather than the model.

Everything here is **read-only from this application**: the portal and the assistant
display it, nothing books a movement or an instalment. In production these tables are fed
by the core banking system; in development they are fed by the seeder.

## 3. The tables

`clients` (extended by V15) keeps identity and relationship: `cin`, `date_naissance`,
`situation_professionnelle`, `employeur`, `revenu_mensuel`, `agence_id`, `statut_kyc`
(`EN_COURS | VALIDE | A_REVALIDER | REJETE`), `niveau_risque` (`FAIBLE | MOYEN | ELEVE`),
`relation_depuis`, `segment` (`PARTICULIER | PROFESSIONNEL | ENTREPRISE`),
`langue_preferee`.

`agences` holds the branch network as reference data — code, name, city, address, phone —
seeded with four branches (Tunis Centre, Ariana, Sousse, Sfax).

`comptes` gains `statut` (`ACTIF | BLOQUE | CLOTURE`), `rib`, `solde_disponible`,
`decouvert_autorise` and `date_cloture`. Account types are now
`COMPTE_COURANT`, `COMPTE_EPARGNE`, `DEPOT_INVESTISSEMENT`, `COMPTE_DEVISES`,
`COMPTE_PROFESSIONNEL`; the three legacy values were renamed in place by the migration.

`cartes` gains `categorie` (`DEBIT | CREDIT | PREPAID | PREMIUM`), `statut`
(`ACTIVE | BLOQUEE | OPPOSITION | EXPIREE`), `plafond_retrait` and `date_emission`, and its
`reseau` is constrained to `CIB | VISA | MASTERCARD | AMEX`.

`transactions` gains `date_valeur`, `reference`, `canal` (`AGENCE | DAB | CARTE | VIREMENT |
PRELEVEMENT | EN_LIGNE`), `contrepartie` and `statut`
(`EN_ATTENTE | COMPTABILISEE | REJETEE`). The sign of `montant` still carries the
direction: positive is a credit.

`beneficiaires` holds saved counterparties: name, RIB, bank, country, type
(`VIREMENT | PRELEVEMENT | INTERNE`), active flag.

`financements` holds the Islamic financing contracts: `reference`, `produit`
(`MOURABAHA | IJARA | MOUCHARAKA | MOUDARABA | SALAM | ISTISNA`), `objet`,
`montant_finance`, `apport`, `marge_taux` (a profit margin, not an interest rate),
`duree_mois`, `echeance_mensuelle`, `capital_restant`, `date_debut`, `date_fin`,
`prochaine_echeance`, `statut` (`DEMANDE | EN_COURS | EN_RETARD | CLOTURE | REJETE`) and
the booking branch.

## 4. What the assistant is allowed to know

RAGLab's `/answer` accepts a question and retrieval knobs — no session, no customer
payload. So when a signed-in customer asks something, the edge rewrites their question into
a short context block followed by the question itself (`CustomerContextService`). The block
is built to be useless if it leaks:

- **Never**: name, email, phone, address, CIN, IBAN, RIB, card number, exact balance,
  exact income, exact instalment.
- **Only**: segment, preferred language, branch and city, KYC state, risk grade, number of
  accounts and their types, **banded** total available balance, number of cards and how
  many are blocked, for each live financing the product, **banded** monthly instalment,
  remaining instalments and the month of the next one, and the count of beneficiaries.

Bands are coarse on purpose (eight steps from "moins de 500 TND" to "plus de 100 000 TND"),
and the block instructs the model not to recite itself. The prompt is assembled only for a
caller carrying `ROLE_UTILISATEUR`; anonymous visitors and staff get the bare question,
exactly as before. The exact figures stay in the portal, read from this database and shown
to the customer who owns them.

Switch the behaviour off with `CUSTOMER_CONTEXT_ENABLED=false`. It is worth knowing that
RAGLab has a `private_or_live_request` refusal for exactly this kind of question: if it
refuses even with the bounded context, the answer is the honest refusal, not a guess, and
the flag is the lever.

## 5. Development accounts

None of this runs unless `DEV_SEED=true` (property `app.dev-seed.enabled`), which
`docker-compose.test.yml` and `docker-compose.e2e.yml` turn on because they are test stacks,
and which no other stack sets. A known password in a live database is a breach, so the
default is off and the seeders log what they created.

**Staff** — password `dev12345`, except the historical `admin@albaraka.tn` (`postgres`).

| Account | Role | What it is for |
|---|---|---|
| `admin@albaraka.tn` | `SUPER_ADMIN` | the general manager: roles, users, everything |
| `documents@albaraka.tn` | `ADMIN_DOCUMENTS` | document and RAGLab operations |
| `supervision@albaraka.tn` | `ADMIN_SUPERVISION` | read + run on the RAGLab console |
| `lecteur@albaraka.tn` | `VIEWER` | read-only |
| `charge.clients@albaraka.tn` | `CHARGE_CLIENTS` (custom) | documents read + console view |
| `auditeur@albaraka.tn` | `AUDITEUR` (custom) | documents read + ingestion history |

The two custom roles are created the way the general manager would create them, and are
flagged staff — the assignment endpoint refuses anything else.

**Customers** — password `client123` for all four.

| Account | Profile | Banking situation |
|---|---|---|
| `client@albaraka.tn` | particulier, fr, Tunis Centre | current + savings, two cards, movements, a Mourabaha, one beneficiary |
| `sana.trabelsi@albaraka.tn` | particulier, ar, Ariana | car Mourabaha (48 months, 12 elapsed), CIB card, two beneficiaries |
| `mohamed.bouazizi@albaraka.tn` | professionnel, fr, Sousse | business account, investment deposit, Ijara on equipment, credit card |
| `ines.khemiri@albaraka.tn` | particulier, en, Sfax, KYC in progress | student account, savings, **blocked card** |

They exist to answer four different questions: what the console looks like at each
permission level, whether a customer can be refused a staff page, whether the assistant
answers in the language of the question rather than of the record, and whether a financial
situation (running instalment, blocked card, KYC pending) changes the answer it gives.

## 6. Trying it

```bash
cp .env.docker.example .env      # RAGLAB_URL, RAGLAB_TOKEN, JWT_SECRET
DEV_SEED=true docker compose -f docker-compose.test.yml up --build
```

To inspect the seeded data directly:

```bash
docker compose -f docker-compose.test.yml exec postgres psql -U postgres banque_assistant \
  -c "select c.email, a.code as agence, c.statut_kyc, c.segment from clients c left join agences a on a.id=c.agence_id;" \
  -c "select reference, produit, statut, echeance_mensuelle from financements;"
```

Restarting the stack is safe: every block is guarded, so nothing is duplicated and a
password changed during testing is not reset.

## 7. What is deliberately absent

No guardians or co-borrowers, no joint accounts, no statements archive, no KYC documents
scan, no scoring history, no offers. They belong to the next slice, not to this one, and
adding them now would widen the prompt surface before the masking rules have been tried in
practice.
