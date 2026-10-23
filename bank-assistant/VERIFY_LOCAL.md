# Local verify (Docker) — run on your host, not CI

Day-to-day build/test iteration happens on **your machine** with a single
command. GitHub CI is intentionally limited to main/master merges and
pull-requests against main, so a throwaway branch push never burns CI minutes.

Everything you need is committed in this repo:

| File | Purpose |
|---|---|
| `verify-local.sh` | the one command you run |
| `docker-compose.verify.yml` | the Docker environment (postgres + one-shot verifiers) |
| `verify/out/*.log`, `verify/out/STATUS.txt` | the logs this script writes and pushes |

## Run it

```bash
# Full check (backend Maven build + Angular build), then push logs
./verify-local.sh

# A single area, e.g. just the Java backend
./verify-local.sh backend
./verify-local.sh frontend

# Run checks but do NOT push the logs (inspect first)
./verify-local.sh --no-push
```

Each run:
1. starts `postgres` (kept **running** between runs),
2. runs the selected checks in throwaway containers,
3. writes the full output to `verify/out/{backend,frontend}.log` and a
   summary to `verify/out/STATUS.txt`,
4. **commits and pushes those files to the current branch** (same command), and
5. leaves the compose environment up.

When you are done:

```bash
docker compose -f docker-compose.verify.yml down   # stop postgres (data removed)
```

## Important

- **Run on a feature/arena branch, not `master`.** The script pushes its logs to
  the current branch; pushing logs to `master` would trigger the (intended) main-branch
  CI run. Iterate on your working branch and only merge to `master` when green.
- **First run is slow.** The Maven image resolves dependencies and npm installs the
  frontend. Later runs reuse Docker layer / named-volume caches and are fast.
- **Re-runs are incremental — nothing you've downloaded is wiped.** Backend
  compiles without `mvn clean` (Maven recompiles only changed sources; deps stay
  in the `m2repo` cache). Frontend uses `npm install`, not `npm ci`, so an
  existing `node_modules` is reused and only new/changed packages are installed.
  The authoritative clean build (`mvn clean verify` / `npm ci`) still runs in CI
  on main/master merges, so local fast iteration is safe.
- If a change ever looks stale (e.g. you deleted a class or a frontend package
  won't drop), do one clean rebuild to rule it out — or just merge to main, where
  CI does the clean build for you.
- The verify `postgres` is a **separate** container from the app stack's
  (`docker-compose.yml`). It publishes no host ports and only talks to the
  one-shot verifier containers by service name, so it won't collide with the app.
- Logs under `verify/out/*.log` and `STATUS.txt` are intentionally tracked in git
  so build results are readable from the repo; other files there are ignored.
