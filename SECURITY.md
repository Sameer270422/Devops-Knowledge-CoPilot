# Security Policy

DevOps Knowledge Copilot is a self-hostable RAG application — if you're running it
yourself, you're responsible for your own deployment's security (network isolation,
secrets management, TLS termination, etc. — see [docs/06-security-hardening.md](./docs/06-security-hardening.md)
and [docs/01-requirements-and-planning.md](./docs/01-requirements-and-planning.md) for
what's already handled at the application layer and what's still a deployment-time
responsibility). This policy covers vulnerabilities in the application's own code.

## Reporting a Vulnerability

Please **do not open a public GitHub issue** for security vulnerabilities — that
broadcasts the problem before a fix exists.

Instead, report it privately using either:

- **GitHub Private Vulnerability Reporting**: open the repository's Security tab →
  "Report a vulnerability", or
- **Email**: samirshaik1127@gmail.com — include a description of the issue, steps to
  reproduce, and the potential impact if known.

You should get an acknowledgment within a few days. This is a solo-maintained project, so
please be patient on turnaround for a fix — but every report will get a response.

## What's in scope

- Authentication/authorization bypass (JWT handling, refresh token rotation, session
  handling)
- Cross-user data leakage (a user retrieving another user's documents/chat history)
- Injection vulnerabilities (SQL, prompt injection that escapes the retrieved-context
  sandbox, XSS)
- Anything that defeats the upload validation or rate limiting described in
  [docs/06-security-hardening.md](./docs/06-security-hardening.md)
- Secrets or credentials exposed in the repository, images, or API responses

## What's out of scope (known, already-documented gaps)

These are tracked openly rather than hidden — see the "Known gaps" sections of
[docs/04-implementation.md](./docs/04-implementation.md) and
[docs/06-security-hardening.md](./docs/06-security-hardening.md):

- No malware/virus scanning on uploaded files (extension allowlist + content-sniffing
  only; a real scanner like ClamAV is a deployment-time addition, not built in)
- Rate limiting and login lockout are in-memory and per-instance (not shared across
  replicas) — expected in a single-instance Phase 1 deployment, not a vulnerability report
- Vulnerabilities in third-party dependencies are handled via Dependabot + Trivy CI
  scanning, not ad-hoc reports (though flagging one you've noticed is still welcome)

## Supported Versions

This project doesn't yet have tagged releases — security fixes land on `main`. Once
versioned releases exist, this section will list which are still receiving fixes.
