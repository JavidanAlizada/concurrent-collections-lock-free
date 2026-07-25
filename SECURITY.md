# Security Policy

## Supported versions

Pre-1.0 — no tagged releases yet. Only `main` is maintained.

## Reporting a vulnerability

This is a library of concurrent data structures with no network surface, no
persistence, and no external input parsing, so the realistic risk surface is
narrow (e.g. a concurrency bug that causes memory unsafety or an infinite
loop under adversarial input). If you find one, open a GitHub issue or email
javidanalizada99@gmail.com. Expect an initial response within a few days —
this is a personal project, not a company with an on-call rotation.

## Dependencies

Dependency review runs on every PR via `actions/dependency-review-action` in
`.github/workflows/pr.yml`. Direct runtime dependencies are intentionally
minimal (JUnit and JMH are test/benchmark-scoped only, not shipped).
