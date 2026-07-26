# Contributing

Contributions that keep Karoo Home Assistant Companion focused, glanceable, and deliberate are welcome. Read `docs/app-idea.md` and `docs/v1-mvp-plan.md` before proposing product changes.

## Prerequisites

- JDK 17
- Android SDK
- GitHub credentials for downloading `karoo-ext`, provided through `gpr.user`/`gpr.key` Gradle properties or `GH_USER`/`GH_TOKEN`
- A physical Karoo 3 and Home Assistant installation for behavior acceptance testing

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

1. Create a descriptive branch and keep the change focused.
2. Separate UI, state, Home Assistant transport, and Karoo integration concerns.
3. Add exactly one release label: `version:major`, `version:minor`, `version:patch`, or `version:skip`.
4. Use a concise, rider-facing PR title because it becomes a changelog entry.
5. Run the local checks and test relevant behavior on a physical Karoo.
6. Update documentation and include screenshots for UI changes.

Never commit Home Assistant credentials, private server URLs, logs containing personal data, Android keystores, or signing passwords.

Release maintainers should follow [`docs/releasing.md`](docs/releasing.md).
