# SonarQube — running the analysis, here and on your machine

The repository is ready for SonarQube/SonarCloud, but **no scan can run inside the Arena
sandbox**: it has no JVM, no Docker, and its network allows only GitHub, npm and PyPI, so
neither `binaries.sonarsource.com` (the scanner) nor a Sonar server is reachable. What
exists here is everything a scan needs apart from the server and the token: the analysis
configuration (`sonar-project.properties`), the coverage reports the scan consumes, and a
CI job that turns itself on the moment those two settings exist.

## 1. What is configured

| File | What it does |
|---|---|
| `sonar-project.properties` | sources (`backend/src/main/java`, `frontend/src`), tests, exclusions, coverage paths |
| `backend/pom.xml` (JaCoCo `report`) | writes `backend/target/site/jacoco/jacoco.xml`, the format Sonar reads |
| `frontend/angular.json` (`coverageReporters`) | writes lcov alongside the console table |
| `scripts/lcov-rebase.sh` | rewrites `SF:src/...` to `SF:frontend/src/...` so the root-relative scan matches the files |
| `.github/workflows/ci.yml` (`sonar` job) | runs the tests, then the scan, and **skips with a notice** when no token is configured |

The sandbox itself is not a limitation of the project: on your machine, with Docker, the
same files produce a full report.

## 2. Locally, with Docker (recommended)

Start a server, once:

```bash
docker run -d --name sonarqube -p 9000:9000 \
  -v sonarqube_data:/opt/sonarqube/data \
  -v sonarqube_logs:/opt/sonarqube/logs \
  sonarqube:lts-community
```

Open http://localhost:9000, log in with `admin`/`admin`, change the password when asked,
then **My Account → Security → Generate token** and create a project token. Name the project
`bank-assistant` to match `sonar.projectKey` (or change the key to whatever the server
created).

Produce the coverage, then scan:

```bash
# backend — tests + JaCoCo XML
cd backend && mvn -B clean verify && cd ..

# frontend — tests + lcov, then make the paths root-relative
cd frontend && npm ci && npm test -- --watch=false && cd ..
./scripts/lcov-rebase.sh

# scan the whole repository in one pass
docker run --rm \
  -e SONAR_HOST_URL=http://host.docker.internal:9000 \
  -e SONAR_TOKEN=<your-token> \
  -v "$PWD:/usr/src" \
  sonarsource/sonar-scanner-cli
```

`--network host` instead of `host.docker.internal` on Linux if your Docker version does not
map the host gateway.

If you prefer Maven for the backend half:

```bash
cd backend
mvn -B clean verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<your-token> \
  -Dsonar.projectKey=bank-assistant
```

Maven then scans only the backend; run the scanner container afterwards for the frontend,
or keep one entry point and always use the container.

## 3. In CI (SonarCloud)

1. Import the repository at https://sonarcloud.io (it needs the GitHub app).
2. Note the organisation and project keys it creates. If they differ from
   `bank-assistant`, either update `sonar.projectKey` in `sonar-project.properties` or add
   `sonar.organization=<org>` at the same place.
3. In GitHub: **Settings → Secrets and variables → Actions**, add
   `SONAR_TOKEN` (a project analysis token) and `SONAR_HOST_URL`
   (`https://sonarcloud.io`, or your server's URL).
4. Push. The `sonar` job in `.github/workflows/ci.yml` now runs; without the two secrets it
   prints a notice and does nothing, so a fork stays green.

The job is deliberately **not a merge gate**: the hard gates stay the Maven build, the
frontend suite and the migration job. A quality-gate failure is a signal to read, not a
reason to block a release — that decision belongs to the team, and Sonar's default gate
("new code has no new issues") is often stricter than a bank wants on a legacy branch.

## 4. Reading the first report

Expect these to be the first things Sonar points at, and none of them is a surprise:

- **Complexity in `PublicChatController`** — one class carrying sync chat, SSE chat, quotas,
  persistence and sanitising. A split is a real refactor, not a dashboard fix.
- **Duplication between the JSON and SSE paths** — the same gate and settle logic appears
  twice by construction. The file already says so in comments.
- **`frontend/src/app/pages/**` coverage** — the coverage include is scoped to
  `src/app/services/**`, so pages show as uncovered by design (they are covered by e2e).
- **Hardcoded test credentials** in `DevDataSeeder` — they are behind `DEV_SEED` and
  excluded from coverage, but a security hotspot may still be raised. Mark it reviewed with
  that reason rather than deleting the fixture.
- **`AnswerSanitizer` regexes** — Sonar likes to flag catastrophic-backtracking risks on
  complex patterns. They are unit-tested; a review comment is the right answer.

## 5. What the sandbox *can* verify, and what it cannot

Verified here, without a server:

- `frontend`: `npm test -- --watch=false` → 19 files, 83 tests green, coverage 67.03% of
  statements over the service layer, and `frontend/coverage/frontend/lcov.info` produced
  with 14 entries; `scripts/lcov-rebase.sh` rewrote all 14 to root-relative paths.
- `sonar-project.properties` and the workflow are valid YAML/properties and the job is
  listed with the expected steps.
- Backend: `mvn verify` cannot run here (no JDK); CI runs it on every backend change, and
  the JaCoCo XML format is asserted by the build rather than by inspection.

Not verified here, and worth a look on the first real scan: that Sonar matches every lcov
entry (a mismatch shows as lower coverage than Vitest printed), and that the Java analysis
resolves `backend/target/classes` — it will not if the scan runs before `mvn verify`, which
is why the CI job builds first and the local instructions do too.
