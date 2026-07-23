# Karoo Home Assistant companion
Karoo Home Assistant companion (`karoo hass companion` or `karoo hass` in short) is an app that allows to make simple actions on your Home Assistant installation using Hammerhead Karoo bike computer. It's like CarPlay companion equivalent for bike computer.

### Project overview
The project is built using:
- Language: Kotlin
- UI Framework: Jetpack Compose (Material3)
- Build System: Gradle (Kotlin DSL)

### Docs

Before implementation always look into `docs` and `specs` folders to identify conventions, decisions made, and other information relevant or related to the change you want to make.

### Hammerhead Karoo integration

When implementing Hammerhead Karoo functionality please follow guidelines and code samples from the following official Karoo sdk repository: https://github.com/hammerheadnav/karoo-ext. Especially different data sources, types and extensions are available here: https://github.com/hammerheadnav/karoo-ext/tree/master/app/src/main/kotlin/io/hammerhead/sampleext/extension.

When searching for examples of Karoo apps, check the list of repos available here: https://github.com/timklge/awesome-karoo
