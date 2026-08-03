# Current implementation

This document describes the behavior currently implemented in the Android app.
It complements the original [v1 MVP plan](v1-mvp-plan.md), which remains a
useful record of the intended scope but does not capture all later UX changes.

## Home Assistant connection

- The app accepts a system-trusted, externally reachable HTTPS Home Assistant
  origin and normalizes it before authentication.
- Authentication uses Home Assistant OAuth in an embedded WebView. It uses the
  GitHub Pages client ID `https://michalchecinski.github.io/karoo-hass-companion/`
  and HTTPS callback
  `https://michalchecinski.github.io/karoo-hass-companion/auth-callback`.
- The callback is intercepted by the app and the authorization code is
  exchanged over direct Wi-Fi. Tokens are encrypted with Android Keystore
  AES-GCM storage.
- Fresh installations continue from OAuth through guided connection-policy and
  PIN-mode choices. Neither choice is preselected, and both must be saved
  before the entity chooser becomes available. Setup finishes after the first
  Quick Access action is added and resumes at the saved step if interrupted.
- Existing installations created before guided onboarding are treated as
  complete. Erasing the account starts the fresh guided flow again.
- The connection policy is configurable as **Wi-Fi only** or **Allow Companion
  fallback**. The choice explains that fallback adds the paired phone and
  Hammerhead Companion app to the connection path. Initial OAuth setup, entity
  discovery, and management use direct Wi-Fi; normal Quick Access state and
  action requests use the selected policy.
- Direct Wi-Fi responses allow Home Assistant's large state list (up to 2 MB).
  The Karoo Companion fallback remains bounded to 100 KB.
- Signing out or using account reset attempts OAuth revocation, removes tokens,
  actions, settings, PIN data, and cached entity states.

## Quick Access

- Quick Access is a scrollable two-column tile grid.
- Every tile displays the Home Assistant friendly name, using the stored name
  until a fresh entity response is available. Long names can use up to three
  lines before ellipsizing.
- Tiles use small bundled icons selected from the Home Assistant icon/domain:
  light, script, lock, cover, switch, or a generic entity fallback. The same
  icon treatment is used in the entity chooser and configured-action list.
- Scripts show only their name and icon; they do not show a state or an extra
  “Run script” label.
- Lights and switches are represented as one **Toggle** action rather than
  separate turn-on and turn-off actions.
- Locks and covers are added as one state-aware control per entity rather than
  as separate operations. A locked lock offers Unlock; an unlocked or open
  lock offers Lock. A closed cover offers Open, an open cover offers Close,
  and a moving cover offers Stop when the entity supports it. Scripts support
  Run. Unlock and Open are always confirmation-protected; other resolved
  operations can optionally request confirmation.
- Lock and cover tiles show a readable current state plus the operation a tap
  will request. Transitional or jammed locks, unsupported cover directions,
  and unavailable entities explain why no action can currently be sent.
- Tiles can be PIN-protected in selected-actions PIN mode. Tiles are disabled
  while an entity refresh or action request is in progress.
- Lock and cover controls refresh their state immediately before resolving an
  operation, then poll Home Assistant after execution to update the visible
  state. Cover Stop performs a best-effort state refresh without claiming
  physical completion. Toggle actions wait for a changed state. Scripts
  deliberately do not display state.
- Configured actions store the Home Assistant friendly name and icon and are
  refreshed when discovery finds a newer entity definition.

## Managing actions

- The Settings gear in the upper-right opens general settings.
- **Manage Quick Access** opens the entity chooser. The chooser does not
  replace the general settings screen.
- The chooser fetches supported Home Assistant entities over direct Wi-Fi,
  supports text search and domain filters, and uses custom rows rather than
  Material `ListItem` to avoid a Karoo Compose measurement crash. Refresh is
  disabled without Wi-Fi and the requirement is explained inline. A discovery
  failure is shown on its own and is not presented as a successful empty
  result.
- Selecting an entity opens an action picker. Locks, covers, lights, switches,
  and scripts each have one Add action; users do not choose separate lock or
  cover operations. Existing configured actions can be removed or reordered
  from the management screen.

## PIN protection

- Available modes are **Disabled**, **Whole app**, and **Selected actions**.
- Onboarding and Settings explain that disabling PIN protection leaves Home
  Assistant controls without additional local authorization on the Karoo.
- New PINs are 4–6 digits, use a masked field and numeric password keyboard,
  and are stored only as salted PBKDF2 verifiers. PIN verification includes
  persistent lockouts after repeated incorrect attempts.
- PIN setup, disabling, and unlock verification run outside the UI thread.
  Each operation disables its controls and shows an in-progress spinner.
- The keyboard Done/confirm action submits a valid unlock PIN.
- An incorrect unlock attempt immediately clears the masked input. A later
  successful unlock clears the prior error message.
- Disabling protection requires verification of the current PIN, clears the
  verifier only after success, and confirms the result inline in Settings.
- Whole-app PIN protection prevents entity-state traffic and Quick Access
  rendering until the PIN succeeds. Entity discovery starts immediately after
  a successful whole-app unlock; tiles show **Loading…** during that refresh.
- A whole-app session remains unlocked while moving between in-app screens and
  locks again when the app leaves the foreground or starts in a protected
  configuration.
- **Forgot PIN / erase this account** is a full-width destructive button. It
  always presents a cancelable confirmation dialog before erasing data.

## Navigation and visual conventions

- The Home Assistant title bar is intentionally omitted from the Quick Access
  screen. The settings gear remains in the upper-right and is kept clear of
  tile content.
- Non-home, non-PIN screens use the same lower-left back control as
  `karoo-camera-control`: its 54 dp `back.png` image with a 10 dp bottom
  inset and direct tap target.
- Back navigates to the immediate parent: entity chooser or OAuth screen to
  Settings, and Settings to Home. PIN entry has no back control.
- The setup heading uses the compact `titleMedium` style for the Karoo display.

## Validation performed during implementation

- The debug unit-test task and debug APK assembly task are run after each
  implementation change:

  ```sh
  ./gradlew :app:testDebugUnitTest :app:assembleDebug
  ```

- The current debug APK is produced at
  `app/build/outputs/apk/debug/app-debug.apk`.

## Current limitations

- The app intentionally remains a curated control surface, not a Home
  Assistant dashboard or arbitrary service-call client.
- Entity management and discovery require direct Wi-Fi because the full state
  response can exceed the Companion transport's 100 KB limit.
- Home Assistant state is retrieved through REST refresh/polling; the app does
  not maintain a WebSocket subscription.
- Bundled icons are a curated domain-oriented set, not the full Material
  Design Icons catalog.
