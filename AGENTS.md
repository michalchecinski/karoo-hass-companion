# Karoo Home Assistant companion
Karoo Home Assistant companion (`karoo hass companion` or `karoo hass` in short) is an app that allows to make simple actions on your Home Assistant installation using Hammerhead Karoo bike computer. It's like CarPlay companion equivalent for bike computer.

## Project overview
The project is built using:
- Language: Kotlin
- UI Framework: Jetpack Compose (Material3)
- Build System: Gradle (Kotlin DSL)

Do not update any dependencies if not asked or allowed directly.

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

## Hammerhead Karoo integration

When implementing Hammerhead Karoo functionality please follow guidelines and code samples from the following official Karoo sdk repository: https://github.com/hammerheadnav/karoo-ext. Especially different data sources, types and extensions are available here: https://github.com/hammerheadnav/karoo-ext/tree/master/app/src/main/kotlin/io/hammerhead/sampleext/extension.

When searching for examples of Karoo apps, check the list of repos available here: https://github.com/timklge/awesome-karoo
