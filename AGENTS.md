# Karoo Home Assistant companion
Karoo Home Assistant companion (`karoo hass companion` or `karoo hass` in short) is an app that allows to make simple actions on your Home Assistant installation using Hammerhead Karoo bike computer. It's like CarPlay companion equivalent for bike computer.

## Project overview
The project is built using:
- Language: Kotlin
- UI Framework: Jetpack Compose (Material3)
- Build System: Gradle (Kotlin DSL)

Do not update any dependencies if not asked or allowed directly.

## Local working notes

Keep generated planning and review notes, including `expert-prompt.md` and
`expert-team.md`, outside the repository in a task-specific directory under
`/tmp`. This applies even when a skill suggests saving those notes in the
workspace. Do not stage or commit them unless the user explicitly requests
them as deliverables. Product and feature documentation belongs in `docs`.

## Docs

Before specification, planning, or implementation, read `docs` folders to identify conventions, product boundaries, and other information relevant to the change.

After adding new feature add Markdown document explaining it in the `docs` folders.
When changing behaviour, UI or other part of the app, update the appropriate document in the `docs` folder.

## Git & GitHub

### branches

Do not use an `agent/` prefix for branch names.

### PR
When creating PR use `.github/PULL_REQUEST_TEMPLATE.md` and check all items on checklist.
When there was a UI change in the PR include screenshots of changed screens only (use `karoo-screenshots` skill).

### Human judgment
If a review comment or any other issue genuinely requires human judgment, ask the user for a decision. Do not resolve the comment or make that decision yourself.

## Testing requirements

Read [the testing strategy](docs/testing-strategy.md) before planning or changing
runtime behavior. For every feature or behavior change, map observable acceptance
criteria and affected safety/security invariants to named executable tests. Cover
relevant negative, boundary, cancellation, persistence and concurrency cases at
the smallest reliable layer; test real orchestration when component wiring changes.
Do not require every test type or add tests that merely mirror implementation.

For bugs, seek a failing-before/passing-after regression. For consequential action,
authentication, PIN, transport, persistence or lifecycle changes, demonstrate that
tests detect the original defect or a relevant deliberately broken variant, then
restore the correct implementation and rerun affected checks. Compilation failures
do not count as behavioral detection. Record exact checks, final source identity,
environment and gaps. Missing/skipped required tests are not a pass; never weaken
assertions or hide failures to obtain green results. Follow the strategy's feedback
loop when a defect escapes. Documentation-only and other nonbehavioral changes
need appropriate validation rather than artificial application tests.

## Issue implementation workflow

Use the repository's `karoo-issue-to-pr` skill for an issue-to-PR implementation request. See `docs/issue-to-pr-workflow.md` for the local workflow.

Physical Karoo and Home Assistant testing is optional unless needed to resolve a material feasibility or correctness uncertainty. Research platform-dependent behavior before implementation; pause for a targeted experiment or human decision if that research remains inconclusive. Automated tests and emulator checks must cover the behavior that can be verified locally. Explicitly report environments tested and remaining device limitations; never claim hardware validation from emulator results.

## Hammerhead Karoo integration references

When implementing Hammerhead Karoo functionality please follow guidelines and code samples from the following official Karoo sdk repository: https://github.com/hammerheadnav/karoo-ext. Especially different data sources, types and extensions are available here: https://github.com/hammerheadnav/karoo-ext/tree/master/app/src/main/kotlin/io/hammerhead/sampleext/extension.

When searching for examples of Karoo apps, check the list of repos available here: https://github.com/timklge/awesome-karoo
