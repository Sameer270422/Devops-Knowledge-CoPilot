# Stage 6 — Security Hardening

## What was built

### Rate limiting beyond login

The security plan committed to "rate limiting on the API and specifically the chat
endpoint," but only login had it (Stage 4's `LoginRateLimiter`). Added a generic,
reusable `common/RateLimiter` — a fixed-window counter keyed by whatever string the
caller supplies — and wired it into the three endpoints that either cost real money per
call or could be used to spam the system:

- **Chat `ask()`** — 20 requests/minute per user id. Every call is a real, billed LLM
  request.
- **Document upload** — 10 uploads/hour per user id. Every upload kicks off async
  extraction + chunking + embedding (also billed API calls).
- **Register** — 5 accounts/hour per client IP (there's no user id yet at this point, so
  IP is the only available key) — caps mass account creation/spam signups.

All three map to HTTP 429 via a new `TooManyRequestsException` + `GlobalExceptionHandler`
entry. Same tradeoff as `LoginRateLimiter`: in-memory, per-instance, fine for the
single-instance Phase 1 target.

### Explicit security response headers

Spring Security's defaults already cover `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, and HSTS on HTTPS requests — `SecurityConfig` now adds an
explicit Content-Security-Policy and Referrer-Policy on top of those defaults.

The more important half of this is in `frontend/nginx.conf`, not the backend: CSP is
enforced by the browser against whatever HTML it actually renders, and that's the
nginx-served frontend, not the backend's JSON API. Added CSP, `X-Content-Type-Options`,
`X-Frame-Options`, `Referrer-Policy`, and `Strict-Transport-Security` (inert until Stage 7
serves this over real HTTPS, but harmless to set now) there. One deliberate compromise:
`style-src` includes `'unsafe-inline'` because several pages use React's
`style={{...}}` inline prop (`DocumentsPage.tsx`, `ChatPage.tsx`) — a strict `style-src`
would silently break those rather than error, and migrating them to CSS classes first was
out of scope for this pass.

### File upload content-sniffing

Extension allowlisting alone doesn't stop someone renaming an executable to
`notes.txt` — a real gap flagged (but not closed) in Stage 4.
`DocumentUploadValidator` now also sniffs the first 512 bytes of every upload:

- `.pdf` must actually start with the `%PDF` magic bytes.
- `.txt`/`.md`/`.log`/`.csv` are rejected if a NUL byte appears in that header — no
  genuine text file contains one, but nearly every binary format (executables, archives,
  images) does. Same heuristic `git`/`grep -I` use for binary detection.

This doesn't replace real malware scanning (still an open gap — see below), but it closes
the cheapest version of the attack. Covered by a new `DocumentUploadValidatorTest`
(genuine text, genuine PDF, fake-extension PDF, NUL-byte "binary" renamed to `.log`,
unsupported extension, empty file, oversized file).

### Non-root containers

Neither Dockerfile ran as anything but root before. Fixed both:

- **Backend** — creates a dedicated `copilot` user/group, chowns `/app` and
  `/data/documents` (so the docker-compose named volume inherits correct ownership on
  first creation instead of showing up root-owned), `USER copilot`.
- **Frontend** — switched from the plain `nginx:alpine` image (whose master process runs
  as root to bind port 80) to `nginxinc/nginx-unprivileged:alpine`, which runs entirely
  non-root on port 8080. `docker-compose.yml`'s frontend port mapping is now `"5173:8080"`
  (the host-side URL you open, `http://localhost:5173`, is unchanged — only the
  container-internal port moved).

### Dependency and secret scanning

- **`.github/dependabot.yml`** — weekly updates for the backend (Gradle), frontend
  (npm), e2e suite (npm), both Dockerfiles' base images, and the GitHub Actions used in
  CI itself.
- **Trivy** (`aquasecurity/trivy-action`) — builds the backend and frontend images in CI
  and fails the build on any HIGH/CRITICAL vulnerability with a known fix. This is the
  "Trivy image scanning in CI" the security plan committed to; it runs as a gate now even
  though the actual ECR push doesn't exist until Stage 7.
- **gitleaks** (`gitleaks/gitleaks-action`) — scans every push/PR for accidentally
  committed secrets (API keys, credentials).

### Non-root, but also non-stale: a GitHub Actions version audit

While wiring in Trivy and gitleaks, checked current action versions instead of guessing —
worth doing carefully, since GitHub is removing the Node 20 Actions runtime from hosted
runners entirely on **2026-09-16** (about six weeks from when this stage was written).
Workflows still pinned to Node 20-era majors (`actions/checkout@v4`,
`actions/setup-java@v4`, `actions/setup-node@v4`, `gradle/actions/setup-gradle@v4`,
`gitleaks/gitleaks-action@v2`) would simply stop running after that date, silently
breaking CI on a project that was otherwise "done." Bumped every action reference in both
`ci.yml` and `e2e.yml` to a current, Node 24-compatible major
(`checkout@v6`, `setup-java@v5`, `setup-node@v6`, `setup-gradle@v5`,
`gitleaks-action@v3`), and corrected the Trivy action tag itself — Aqua Security migrated
all their tags to a `v`-prefix after a supply-chain incident, so the plausible-looking
`aquasecurity/trivy-action@0.28.0` guessed initially isn't a real tag; the actual current
release is `@v0.36.0`. The new `github-actions` entry in `dependabot.yml` keeps these from
going stale again on their own.

### Closed: the missing Gradle wrapper (found while testing this stage)

Not originally scoped for Stage 6, but surfaced directly while verifying this stage's own
work: running `gradle build` locally used whatever Gradle was installed system-wide
(9.3.1), which broke immediately (`getDirMode()` signature mismatch) because Spring Boot
3.3.2's Gradle plugin doesn't support Gradle 9.x. This is exactly the failure mode the
"no committed wrapper" gap flagged back in Stage 4 predicted. Generated and committed the
wrapper (`backend/gradlew`, `gradlew.bat`, `gradle/wrapper/*`) pinned to Gradle 8.9 — same
version CI already used — and updated both `ci.yml` and `backend/Dockerfile` to build via
`./gradlew` instead of a floating system/image-provided Gradle, so the version is defined
in exactly one place from now on.

## Verification

Backend changes (`RateLimiter`, `TooManyRequestsException`, `GlobalExceptionHandler`,
the three controllers, `DocumentUploadValidator`, `SecurityConfig`) — checked via
brace-balance on every file touched, and by tracing each controller's constructor
injection against the actual `RateLimiter` bean shape. Added `RateLimiterTest` (limit
enforcement, independent keys, window reset) and `DocumentUploadValidatorTest` (7 cases)
— written but not executed locally, same sandbox constraint as every prior backend stage.

This stage is also where real local test execution happened for the first time on your
machine (see the Gradle wrapper story above): once the wrapper was in place, `./gradlew
build` ran 37 tests, of which 35 passed — including the fix to a real chunking bug caught
only by that real run (documented in Stage 5, discovered while chasing this stage's
build-tooling fix). The remaining 2 failures were both `CopilotApplicationTests` and
`VectorStoreIntegrationTest` hitting the same local Docker Desktop-specific quirk (a
bundled enterprise/AI-agent gateway proxy stubbing out `docker-java`'s API calls) that
doesn't reproduce in GitHub Actions, where the same tests already pass — noted as a local
environment quirk, not a project defect.

`docker-compose.yml`, `dependabot.yml`, `ci.yml`, and `e2e.yml` — YAML-parsed clean.
`nginx.conf` — visually verified (no local nginx binary available to run `nginx -t`
against it); the CSP directive was checked against every external resource `index.html`
actually loads (Google Fonts) so it doesn't silently break the app's own styling.

## Known gaps

- **Malware/virus scanning** — still not implemented. Content-sniffing (above) catches
  crude renamed-file attacks but is not a substitute for a real scanner (e.g. a ClamAV
  sidecar). This needs external infrastructure this environment can't stand up; treat it
  as a hard requirement before accepting untrusted uploads from strangers in a real
  public deployment.
- **Rate limits are per-instance** — noted above, expected for Phase 1's single-instance
  target; move to Redis if/when the app runs multi-replica.
- **CSP `style-src` allows `'unsafe-inline'`** — a deliberate, documented trade-off against
  the current use of React inline `style` props, not an oversight.
- **AWS-level hardening (IAM, network isolation, WAF, encryption at rest/in transit,
  Secrets Manager)** is Stage 7's job, not this one — this stage covers only what's
  controllable at the application/container/CI level before any cloud infrastructure
  exists.
