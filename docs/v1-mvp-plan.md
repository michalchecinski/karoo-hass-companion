> Agent-created document: This plan was generated collaboratively with Codex for future reference.

> **Implementation status (2026-07-24):** This document records the original
> MVP scope. The implemented behavior, including later UX and security
> refinements, is documented in [current-implementation.md](current-implementation.md).
> Where the two differ, the current implementation reference is authoritative.

# Karoo Home Assistant Companion — v1 MVP

## Summary

Build a personal, sideloaded Karoo 3 MVP that reliably executes a curated set of explicit Home Assistant actions through either direct Wi-Fi or the paired-phone Companion fallback.

- Leave `docs/app-idea.md` unchanged; this separate agent-created plan is the implementation reference.
- Support scripts, locks, covers, lights, and switches.
- Use Home Assistant OAuth rather than long-lived-token entry.
- Remain a standalone launcher app usable during a recorded ride; add no data field, Bonus Action, or background automation.
- Treat Karoo’s fallback as HTTP-only, limited to 100 KB, with refresh-based state rather than WebSockets. [Karoo SDK request API](https://github.com/hammerheadnav/karoo-ext/blob/master/lib/src/main/kotlin/io/hammerhead/karooext/models/KarooEvent.kt)

## Product and UX

- Onboarding requires Wi-Fi:
  1. Enter one externally reachable, system-trusted HTTPS Home Assistant URL.
  2. Validate and normalize it to its origin.
  3. Authenticate in an embedded Home Assistant OAuth WebView.
  4. Choose `Wi-Fi only` or `Allow Hammerhead Companion fallback`.
  5. Choose PIN mode and configure a 4–6 digit PIN when required.
  6. Add the first Quick Access actions.
- Use `https://michalchecinski.github.io/karoo-hass-companion/` as OAuth `client_id` and `karoohass://auth-callback` as the registered callback. The GitHub Pages HTML must place the redirect declaration in its first 10 KB and visibly explain that the page receives no credentials or Home Assistant traffic. [Home Assistant authentication API](https://developers.home-assistant.io/docs/auth_api/)
- Quick Access is an unbounded, scrollable two-column grid. Each tile represents one immutable operation:
  - Script: Run.
  - Lock: Lock or Unlock.
  - Cover: Open, Close, or Stop when supported.
  - Light/switch: Turn on or Turn off.
- Generate labels from the localized operation and Home Assistant `friendly_name`. Store the HA `mdi:` icon name for future compatibility, but render a small curated set of bundled operation/status vectors in v1.
- Management remains on Karoo but requires Wi-Fi: load all current supported entities, search, choose an operation, configure PIN protection and optional confirmation, remove actions, and reorder them using a dedicated single-column management list with drag handles.
- Reject exact duplicate entity-operation pairs while allowing opposite operations for the same entity.
- Always confirm Unlock and Open. Other actions have a configurable confirmation toggle, off by default.
- Deleted entities remain visibly unavailable and removable; never substitute another entity after an ID change.

## Implementation and Interfaces

- Introduce these core models:
  - `ConnectionPolicy`: `WIFI_ONLY`, `ALLOW_COMPANION_FALLBACK`.
  - `PinMode`: `DISABLED`, `WHOLE_APP`, `SELECTED_ACTIONS`.
  - `ActionKind`: `RUN_SCRIPT`, `LOCK`, `UNLOCK`, `OPEN_COVER`, `CLOSE_COVER`, `STOP_COVER`, `TURN_ON`, `TURN_OFF`.
  - `QuickAccessAction`: local ID, entity ID/domain, action kind, protected flag, confirmation flag, stored HA icon name, and ordering key.
  - `EntitySnapshot`: state, supported features, availability, timestamps, friendly name, and icon.
  - `ActionOutcome`: `SENDING`, `REQUESTED`, `COMPLETED`, `FAILED`, `UNKNOWN`.
- Add a transport boundary used by authentication, state, and action repositories:
  - `DirectWifiTransport` binds HTTP to an active Wi-Fi `Network`; it refuses requests if Wi-Fi is absent.
  - `KarooTransport` wraps official `OnHttpResponse.MakeHttpRequest`; the SDK chooses Wi-Fi first and Companion fallback otherwise.
  - Set `waitForConnection=false` for every user-triggered action so actions are never delivered later.
  - Do not use the community Ktor Karoo engine because it always routes through Karoo System Service and cannot enforce Wi-Fi-only.
- Implement Home Assistant REST operations:
  - Discovery over Wi-Fi via `/api/states`, filtered to the five supported domains.
  - Normal refresh via `/api/states/<entity_id>` for distinct configured entities, with bounded concurrency and progressive tile updates.
  - Execution via `/api/services/<domain>/<service>` with only `entity_id`; expose no arbitrary payload or service-call UI. [Home Assistant REST API](https://developers.home-assistant.io/docs/api/rest/)
- OAuth:
  - Generate and verify a per-attempt `state` nonce.
  - Exchange authorization codes and refresh access tokens through `/auth/token`.
  - Encrypt access/refresh tokens with an Android Keystore AES-GCM key; disable Android backup.
  - On 401, refresh credentials but do not automatically replay an action; ask the user to invoke it again.
  - Sign-out attempts token revocation, then wipes local credentials.
- Persist non-secret configuration and action ordering with DataStore. Remove the unused extension service/metadata while retaining `karoo-ext` for `KarooSystemService`.
- PIN behavior:
  - Whole-app authorization expires after two minutes without interaction or immediately on leaving the foreground.
  - Selected-actions authorization applies to exactly one confirmed action.
  - Store only a salted, slow PIN verifier.
  - After five failures, apply a persistent 30-second lockout; repeated lockouts grow exponentially to 15 minutes and reset after successful entry.
  - “Forgot PIN” warns, best-effort revokes OAuth credentials, and erases account, Quick Access, and settings.
- State and execution rules:
  - State is fresh for one minute. Tapping an older stateful action first refreshes that entity and proceeds only if it is fresh and available.
  - Scripts may execute with stale state; other stateful actions are blocked when refresh fails, state is `unknown`/`unavailable`, or the entity is missing.
  - Allow only one global action request at a time.
  - Never automatically retry a service POST.
  - Scripts and Cover Stop finish as `REQUESTED`.
  - For other stateful actions, poll the target entity for up to 15 seconds and report `COMPLETED` only when the expected final state is observed.
  - Report `UNKNOWN` if Home Assistant may have accepted the action but the response or verification is lost; report `FAILED` only for a definite pre-execution failure or rejection.

## Test Plan

- Unit-test URL normalization, OAuth state verification, token refresh/revocation, encrypted storage, PIN hashing/lockouts/relock timing, entity capability mapping, stale-state gating, expected-state verification, and outcome classification.
- Test both transports with fakes for no Wi-Fi, Companion unavailable, queued/in-progress/complete events, timeout, cancellation, oversized response, 401, malformed JSON, and definite versus ambiguous failures.
- Run integration tests against a fake Home Assistant server for discovery, every supported action mapping, missing entities, unavailable states, action rejection, delayed state changes, scripts with no provable completion, and token expiry.
- Add Compose tests at the existing 256×426 Karoo viewport for onboarding, two-column scrolling, long names, stale/unavailable/protected indicators, confirmation, PIN entry, management, and status surfaces.
- Verify GitHub Pages exposes the redirect declaration within the first 10 KB and that OAuth returns to the app.
- Physical Karoo 3 acceptance:
  - Complete fresh-install OAuth setup over Wi-Fi.
  - Execute representative actions from all five domains.
  - Confirm Wi-Fi-only refuses actions without Wi-Fi and never executes them later.
  - Disable Karoo Wi-Fi and execute through a paired phone with fallback enabled.
  - Interrupt connectivity during execution and confirm an honest `UNKNOWN` result with no automatic replay.
  - Open and operate the standalone app while a ride is recording.
  - Validate all three PIN modes, idle/background relocking, failed-attempt throttling, and reset recovery.
  - Report release APK size and keep curated icon assets below 100 KB.

## Assumptions and Deferred Scope

- Target one current Karoo 3/KOS and one current Home Assistant installation for the personal MVP.
- The Home Assistant URL is externally reachable with a system-trusted certificate; self-signed certificates and custom CA import are excluded.
- Setup and action management require Wi-Fi. Normal actions may use the selected fallback policy.
- English-only UI is acceptable for v1, with all strings kept in Android resources.
- No local/remote URL switching, mDNS discovery, multiple servers, WebSocket state, arbitrary services, buttons, brightness, cover position, custom tile labels, full MDI bundle, custom certificates, public-release onboarding, analytics, project relay, Home Assistant custom component, or ride-screen integration.
- Companion fallback remains a physical-device release gate: if the official Karoo HTTP path does not behave reliably from the foreground app during a recorded ride, v1 is not complete until that limitation is resolved or explicitly respecified.
