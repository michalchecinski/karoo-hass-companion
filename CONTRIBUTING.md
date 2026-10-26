# Contributing

Contributions that keep Karoo Home Assistant Companion focused, glanceable, and deliberate are welcome. Read `docs/app-idea.md` and `docs/v1-mvp-plan.md` before proposing product changes.

## Feature ideas and documentation

Propose feature ideas in GitHub issues using the **Feature request** template and the existing `feature` label. Issues are the place to discuss ideas and open questions before agreeing on behavior. Concise roadmap entries may summarize ideas and link to their GitHub issues.

Write detailed documentation in `docs/features` once behavior is agreed or a specification is needed for implementation, and link that specification from the issue. Keep documentation of implemented behavior up to date when the app changes.

## Prerequisites

- JDK 17
- Android SDK
- GitHub credentials for downloading `karoo-ext`, provided through `gpr.user`/`gpr.key` Gradle properties or `GH_USER`/`GH_TOKEN`
- For UI changes, a compatible isolated Karoo emulator
- A physical Karoo 3 and Home Assistant installation only when needed to resolve
  material feasibility or correctness uncertainty; see [the testing strategy](docs/testing-strategy.md)

## Local workflow

```bash
./gradlew ktlintCheck check assembleDebug
./gradlew installDebug
./gradlew ktlintFormat
```

[Task](https://taskfile.dev) aliases are also available:

```bash
task build
task installDebug
task fmt
```

Install the repository's non-mutating pre-commit check with:

```bash
git config --local core.hooksPath .git-hooks
```

## Pull requests

For UI screenshots, follow the repository's
[`karoo-screenshots` skill](.agents/skills/karoo-screenshots/SKILL.md): use a
dedicated emulator and inspect a temporary capture before replacing an image.
Its capture safeguards can be tested on macOS without starting an emulator:

```bash
python3 .agents/skills/karoo-screenshots/scripts/test_capture_app_only.py
```

1. Create a descriptive branch and keep the change focused.
2. Separate UI, state, Home Assistant transport, and Karoo integration concerns.
3. Add exactly one release label: `version:major`, `version:minor`, `version:patch`, or `version:skip`.
4. Use a concise, rider-facing PR title because it becomes a changelog entry.
5. Run the local checks and test relevant behavior on a physical Karoo.
6. Update documentation and include screenshots for UI changes.

Never commit Home Assistant credentials, private server URLs, logs containing personal data, Android keystores, or signing passwords.

Release maintainers should follow [`docs/releasing.md`](docs/releasing.md).
