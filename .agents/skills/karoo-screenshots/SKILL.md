---
name: karoo-screenshots
description: Capture and maintain clean app-only screenshots for Karoo Home Assistant Companion. Use when a UI change needs visual verification, new README screenshots, release images, or a pull-request visual summary; includes emulator capture at Karoo resolution, system-UI removal, README gallery updates, and PR-body image links.
---

# Karoo Screenshots

Capture the affected screens after UI work. Keep production README assets in `screenshots/emulator-karoo-480x800/` and never publish Android status or navigation bars.

## Workflow

1. Read `docs/` and inspect the changed composables. Identify the smallest representative set of user-visible states, including loading, empty, error, and populated states when the change affects them.
2. Preserve unrelated worktree changes. Stage only intentional screenshot, README, and PR-description files.
3. Build with `./gradlew :app:assembleDebug`. Use a dedicated 480×800 emulator named `karoo-hass-capture-<unique-task-suffix>`, and keep its explicit ADB serial for every command. Do not navigate, install on, or capture from a shared emulator. If `Karoo` is already in use, create a fresh temporary AVD from its `config.ini` under a task-specific `ANDROID_AVD_HOME`; change `AvdId` and `avd.ini.displayname`, and create the matching `.ini` with its temporary path. Start it on an unused explicit port with `-no-snapshot`, install the APK there, and shut down only that instance when finished. Do not copy account data or use `-read-only` against an AVD already open in writable mode; the emulator rejects that combination.
4. Use isolated emulator app data. Do not use a real Home Assistant account or token. Where a connected settings state is needed, use `https://demo.example.com` only as a nonfunctional placeholder.
5. Navigate to each state with ADB. Run `scripts/capture-app-only.sh` with the dedicated serial, a temporary candidate path outside the repository, and the expected AVD name. It checks emulator identity and the foreground app before and after capture, validates the raw dimensions, removes the 48 px status area and 96 px navigation area, and validates the 480×656 result before publishing its output. A failed check leaves an existing output untouched.
6. Inspect each temporary candidate visually before copying it over a tracked screenshot. Check the intended screen and state, and that no Android system bar, keyboard, dialog outside the intended state, placeholder typo, or partial transition appears. If capture fails or another app appears, keep the tracked image and diagnose the dedicated target; do not continue with stale output. After inspection, copy only the approved candidate into `screenshots/emulator-karoo-480x800/`.
7. Update the README gallery only when the changed screen is already represented there or the change is a user-visible feature worth showcasing. Keep the existing Karoo-project convention: sequential images between `## Screenshots` and `## Installation`, without a table, captions, or emulator qualification.
8. For a PR that changes visible UI, add a `## Screenshots` section to its description. Link only the screenshots that demonstrate the change, using repository-relative Markdown paths when supported by the PR client; otherwise mention their paths explicitly.

## Capture command

Run from the repository root after starting the dedicated emulator. Supply its
serial, a temporary candidate path, and its exact AVD name:

```sh
.agents/skills/karoo-screenshots/scripts/capture-app-only.sh emulator-5560 /tmp/karoo-hass-capture-issue-24/settings-connected-app-only.png karoo-hass-capture-issue-24
```

The script requires macOS `sips` and expects `com.karoohass` in the foreground.
Use `adb -s <serial> emu avd name` and `adb -s <serial> shell wm size` to verify
the dedicated profile before navigating. Replace the example serial and suffix
with those allocated to the current task. Run its regression checks with
`python3 .agents/skills/karoo-screenshots/scripts/test_capture_app_only.py`.

## Asset rules

- Use stable, state-based lowercase names such as `quick-access.png`, `onboarding.png`, and `settings-connected.png`.
- Replace an existing image rather than accumulating obsolete variants.
- Prefer populated, realistic Quick Access controls as the lead README image. Do not use an empty state as a product hero when a populated state is available.
- Do not modify authentication, saved credentials, or real Home Assistant data merely to create screenshots.
- Before committing, run `git diff --check` and confirm each PNG is tracked intentionally.
