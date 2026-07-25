# Contributing

This is a solo portfolio project, but it's built with the same discipline as
a real internal library, so the same rules apply if that ever changes.

## Before opening a PR

```bash
./gradlew compileJava checkstyleMain checkstyleTest spotbugsMain spotbugsTest
./gradlew test
./gradlew jacocoTestReport
```

All four must pass locally before pushing — CI runs the identical set and
will reject anything that doesn't.

## Code style

- Checkstyle is enforced with zero tolerance for warnings (`maxWarnings = 0`).
  Run `checkstyleMain`/`checkstyleTest` before pushing, not after CI catches it.
- Comments explain *why*, not *what* — if a comment just restates the method
  name, delete it. Save comments for genuinely non-obvious things: a memory-
  ordering choice, a workaround, an invariant a reader could break without
  noticing.
- No `AtomicReference`/`java.util.concurrent` wrapping in the core structures
  — see the README for why. If a JDK class is used anywhere, say why in the
  commit message or an ADR.

## Commits

One logical unit of work per commit — a build change, a class, a test suite,
a doc — not a squash of a whole milestone. Each commit should compile, pass
its own tests, and pass static analysis on its own.

## Design docs before implementation

Any new data structure gets a short design write-up (algorithm, linearization
points, progress guarantee, memory-model implications) before the
implementation lands — see `docs/algorithms/` once Milestone 1's is written,
for the format.
