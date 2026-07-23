# Stage 0: Karoo platform foundation

Status: **Accepted on 2026-07-23**

This stage records what the official Hammerhead platform supplies and proposes
the narrow way this project should use it. It does not decide product scope,
Home Assistant authentication, or lock security; those belong to later stages.

## Sources inspected

- `hammerheadnav/karoo-ext` at commit
  `26e1d1c5c86e4d49922b2e2cc0474e62fc3b6eed`.
- `hammerheadnav/karoo-ext-template` at commit
  `c789ab4a76b682772c73589182991f7254cb3ace`.
- Latest published `karoo-ext` release observed during this stage: `1.1.9`.

The source checkouts are research references at `/Users/michal/dev/karoo-ext`
and `/Users/michal/dev/karoo-ext-template`; they are not part of this project's
repository.

## Established platform facts

### PF-01: `karoo-ext` is a library, not an application framework

The official integration point is a Gradle dependency. Hammerhead separately
publishes `karoo-ext-template` as the recommended empty Android application and
extension-service scaffold.

### PF-02: The application and extension are separate surfaces

The template contains a normal launcher `Activity` and a `KarooExtension`
Android `Service`. The activity can host a full Jetpack Compose application.
The service allows Karoo OS to discover and call extension capabilities.

### PF-03: Extension capabilities are declared statically

`extension_info.xml` declares the extension ID and capabilities such as data
types and bonus actions. Bonus-action IDs are build-time definitions rather
than a dynamic list of user-created Home Assistant buttons.

### PF-04: The extension service is exported

Karoo OS discovers the service through the
`io.hammerhead.karooext.KAROO_EXTENSION` intent action. The official template
marks the service exported. The base `KarooExtension` exposes an AIDL binder and
its final `onBind` implementation does not itself establish an app-specific
authorization boundary for sensitive operations.

Therefore every callback reachable through the extension service must be
treated as callable by an untrusted co-installed Android application unless a
separate verified platform guarantee proves otherwise.

### PF-05: Bonus actions are unsuitable for direct sensitive execution

Karoo controllers can invoke a statically declared `BonusAction`, which reaches
`KarooExtension.onBonusAction(actionId)`. The sample uses one action to launch
its activity. Directly executing a Home Assistant action here would bypass the
proposed app PIN and confirmation UI and would expand the exported service's
security impact.

### PF-06: Karoo's HTTP effect intentionally falls back between transports

`OnHttpResponse.MakeHttpRequest` uses Wi-Fi when connected and otherwise may
use the paired phone over Bluetooth. Its `waitForConnection` option controls
queueing, not the permitted transport. This makes it suitable only when the
user has explicitly allowed either Karoo connection. It cannot enforce a
Wi-Fi-only Home Assistant policy and it does not provide WebSocket or
server-sent-event support. The known Ktor adapter also documents a 100 KB
request and response limit, so the bridge cannot be treated as a transparent
replacement for bulk Home Assistant API operations.

### PF-07: The official UI baseline is suitable for a standalone app

The template uses Kotlin and Jetpack Compose, targets Android API 34 with
minimum API 23, and includes a `256 × 426 dp` Compose preview matching the small
Karoo form factor. The full sample demonstrates lifecycle-aware state, system
events, alerts, and other optional integrations.

### PF-08: Current library compatibility has a Karoo OS floor

The `1.1.9` release states that its capabilities require Karoo OS
`1.634.2440` or later. The project must pin and test an explicit library and KOS
compatibility pair rather than depending on an unbounded `1.x` range.

## Proposed foundation decisions

### FD-01: Start from the official template structure

At implementation time, initialize this repository from the structure and
conventions of `karoo-ext-template`, then replace its identity, resources, and
sample code. Do not vendor or fork the `karoo-ext` library source into the app.

### FD-02: Depend on a pinned `karoo-ext` release

Use the latest release accepted at implementation kickoff—currently `1.1.9`—as
an exact dependency. Library upgrades require an explicit compatibility change
and real-device verification.

### FD-03: Ship a standalone app with a deliberately minimal extension

The Compose activity owns onboarding, PIN entry, configuration, confirmation,
Home Assistant requests, and result display. The extension service exists only
for Karoo discovery and explicitly accepted convenience integrations.

### FD-04: Permit at most one safe bonus action in v1

If included, a static controller bonus action may only open the app's locked
entry screen. It must not select, confirm, queue, or execute a Home Assistant
action. No user-specific entity or script identifier may cross the exported
extension callback.

### FD-05: Support direct Wi-Fi and Karoo-routed Home Assistant transports

Home Assistant communication has two policy-controlled transport paths:

- direct Android networking, explicitly bound to an eligible Wi-Fi network,
  for the **Wi-Fi only** connection mode; and
- Karoo's `OnHttpResponse` capability, optionally exposed through a compatible
  Ktor client engine, for HTTP requests when the user selects **Any available
  Karoo connection**.

The Karoo-routed path may use Wi-Fi or the paired phone over Bluetooth and
therefore must never be selected in Wi-Fi-only mode. It is an HTTP
request/response adapter, not a general socket transport. WebSocket features
remain available only through direct Android networking.

The exact dependency used to adapt `OnHttpResponse` must be pinned,
supply-chain reviewed, and verified against the accepted `karoo-ext` and Karoo
OS versions before implementation.

### FD-06: Exclude unrelated extension capabilities from v1

Do not declare device scanning, data types, custom ride data, map layers, FIT
writing, or background location merely because the sample demonstrates them.
Each additional capability would need a product use case and security review.

### FD-07: Harden the template before feature work

The official template is a functional starting point, not a security profile.
Before feature implementation, the project must change its permissive backup
default, define release signing/minification policy, audit every exported
component, and add an explicit network-security configuration.

## Consequences if accepted

- `karoo-ext-template` supplies the initial Gradle/Compose/manifest structure.
- `karoo-ext` supplies Karoo OS interoperability and the optional
  Karoo-routed HTTP path; the app still owns Home Assistant protocol behavior
  and transport selection.
- The main product is a full-screen Karoo app, not an interactive data field.
- Any controller shortcut opens the locked app and never performs the action.
- Wi-Fi-only networking must be implemented and tested independently of
  Karoo's convenience HTTP API, while the Karoo-routed path must be tested as
  an explicitly selected alternative rather than an automatic policy bypass.
- Karoo 3 is the only required hardware target until a later stage deliberately
  expands support.

## Product-owner disposition

1. **S0-Q1 — Foundation:** Accepted. Use the official template structure and a
   pinned `karoo-ext` dependency.
2. **S0-Q2 — Entry surfaces:** Deferred to Stage 5. Both launcher-only and an
   open-app bonus action satisfy Stage 0 provided the bonus action cannot
   execute a Home Assistant control.
3. **S0-Q3 — Device scope:** Accepted. Karoo 3 is the v1 hardware target.
4. **S0-Q4 — In-ride integration:** Accepted. V1 is a controls-only application,
   not a custom ride-data page, map layer, or data field.

## Acceptance gate

Stage 0 is accepted. The deferred entry-surface choice cannot weaken the rule
that exported Karoo extension callbacks never execute Home Assistant controls.
