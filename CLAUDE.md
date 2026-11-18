# Agent instructions

Follow [CONTRIBUTING.md](CONTRIBUTING.md); it is the authoritative workflow for humans and agents alike. Before work, read [docs/STATUS.md](docs/STATUS.md) (current state) and [docs/DECISIONS.md](docs/DECISIONS.md) (decisions that bind behavior). Explicit maintainer instructions take precedence; a material change in behavior is recorded as a new ADR.

Additional rules for automated agents:

- Preserve other people's uncommitted work: never revert, stash, reset or overwrite changes you did not make. Other agents may share the checkout and the machine.
- Commit, push, merge to `main`, sign, upload or dispatch a workflow only when the maintainer asks. Check `git var GIT_AUTHOR_IDENT` before committing. Never force-push or rewrite published history.
- Device testing: target every adb command at an explicit device, never reset adb or change other apps, services or accessibility selections, remove only test packages you installed, and keep captures and results in `.cache/` (gitignored).
- Prefer repository-local tools and caches; do not change machine-wide versions, profiles or global configuration.
- Do not delegate to other agents unless the maintainer asks; if asked, split file ownership.
- Report exactly what was verified (build, device, app version) and what was not. A passing build or a mock is not device evidence.
- In long sessions, checkpoint the objective, decisions, completed checks and next step before context compaction, then continue without redoing finished work.
