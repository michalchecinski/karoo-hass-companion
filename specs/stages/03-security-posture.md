# Stage 3: Security posture

Status: **Accepted on 2026-07-23**

This stage defines the attackers, assets, security guarantees, and accepted
residual risks for the accepted control journeys. It does not yet select the
exact OAuth integration, token-encryption construction, Android APIs, or
network implementation; those belong to Stage 4. Exact confirmation and
recovery screens belong to Stage 5.

## Binding inputs from accepted stages

- The product is a controls-only Karoo app, not a Home Assistant dashboard.
- The MVP cannot depend on the Karoo having a system-level PIN or screen lock.
- Setup and management are self-contained on Karoo.
- The app connects directly to one Home Assistant server using the native Home
  Assistant stack.
- The product requires no custom Home Assistant integration, App, HACS
  component, hosted project account, or project-operated relay.
- Scripts, buttons, input buttons, and scenes are ordinary actions even when
  their implementation has sensitive effects the app cannot inspect.
- Direct lock operations and user-classified sensitive cover operations use a
  sensitive-action flow.
- Missing, stale, ambiguous, or incompatible state fails closed where the
  accepted action contract requires live state or result verification.

## Candidate protected assets

- Authority granted by the stored Home Assistant credential.
- Physical access provided by locks and entrance covers.
- The local PIN verifier and any keys used to protect stored credentials.
- The configured server address, selected entities, and security preferences.
- Household information exposed by entity names, states, and diagnostic logs.

## Accepted decisions

### P3-D01: Tiered security target after physical loss

1. **Required — casual finder resistance:** Someone who finds an unlocked Karoo
   and uses its normal UI cannot access setup, reveal credentials, or execute a
   sensitive action without satisfying the app's local authorization policy.
2. **Required — ordinary extraction resistance:** The app contains no embedded
   reusable secret, excludes secrets from backup and logs, relies on the
   Android application sandbox and supported hardware-backed or OS-backed key
   protection, and makes an extracted PIN verifier expensive to guess
   offline. The exact feasible controls must be verified on Karoo 3 in Stage 4.
3. **Explicitly not guaranteed — compromised platform resistance:** Once an
   attacker can root or fully compromise the Karoo OS, instrument the app
   process after authorization, or defeat the platform keystore, the app cannot
   promise that the Home Assistant credential remains secret.

The app PIN must therefore be described as an application authorization
boundary, not as equivalent to full-device encryption or a secure system
screen lock.

If Stage 4 shows that Karoo lacks a required platform protection, that becomes
a release-blocking feasibility finding or an explicitly reconsidered security
requirement—not a hidden downgrade.

### P3-D02: Dedicated Home Assistant identity is recommended, not enforced

The recommended pairing uses a dedicated Home Assistant user that is:

- active;
- not the owner;
- not an administrator; and
- restricted by Home Assistant to local access only.

The account is created using Home Assistant's native user-management UI. The
Karoo app never requests an administrator password or temporarily pairs as an
administrator to create the account. Setup may guide the user to the
native UI on Karoo, preserving self-contained setup, while recommending that
account administration be performed from an already trusted device when one is
available.

The recommendation does **not** provide entity-level least privilege with Home
Assistant's standard user setup. Home Assistant has an entity permission model
internally, but the standard non-administrator user policy currently grants
access to all entities. Consequently:

- removing administrator rights protects configuration and user management;
- local-only access prevents Home Assistant from accepting this identity from
  a connection it classifies as external;
- a dedicated account provides separate attribution and revocation;
- the Karoo app's selected-control list limits only what the app offers; and
- an attacker who extracts the credential may have ordinary-user access to
  other Home Assistant entities, not only the selected Karoo controls.

The app does not reject an otherwise valid pairing because the account is an
owner, administrator, shared account, or externally enabled. It presents the
recommended posture and, where native APIs expose the facts, a concise
assessment of the paired account. It does not repeatedly nag the user or
disable controls because the recommendation was not followed.

Exact account-property visibility belongs to Stage 4. Granular server-enforced
entity permissions are not claimed unless a future native Home Assistant setup
flow exposes them without a custom integration or unsupported internal-state
editing.

This choice preserves user autonomy, but it creates explicit residual risk:
pairing an owner, administrator, or externally usable identity gives an
extracted credential correspondingly broader authority. Documentation and
setup copy must not hide that difference or describe the account
recommendation as a server-enforced app boundary.

Evidence informing this decision:

- Home Assistant documents user-only accounts for service/integration and
  local-only use:
  <https://www.home-assistant.io/docs/configuration/user-configuration/>
- Home Assistant documents entity permissions and enforcement:
  <https://developers.home-assistant.io/docs/auth_permissions/>
- The current built-in regular-user policy grants the entity category rather
  than a selected entity subset:
  <https://github.com/home-assistant/core/blob/dev/homeassistant/auth/permissions/system_policies.py>

### P3-D03: OAuth first, with a gated long-lived-token fallback

The MVP first attempts to use Home Assistant's native OAuth authorization-code
flow for operational authentication. Under the preferred design:

- the app opens Home Assistant's authorization UI and never asks for or stores
  the user's Home Assistant password;
- the app stores the resulting refresh token under the local protection
  requirements decided in this stage;
- normal API calls use short-lived access tokens;
- logout attempts native refresh-token revocation and clears all local tokens;
- OAuth `state` and redirect handling must prevent a different authorization
  response or app from completing the pairing.

Stage 4 must prove the complete browser-to-app OAuth return on a real Karoo 3.
If that spike shows OAuth cannot be completed reliably on the supported
platform, the MVP may instead accept a manually entered or pasted Home
Assistant **long-lived access token**. This fallback is a deliberate product
decision, not an OAuth variant.

If the fallback is activated:

- it is the only non-OAuth operational credential accepted; passwords, legacy
  API passwords, webhook secrets, and arbitrary imported configuration files
  remain excluded;
- the app explains once that the bearer token may remain valid for years and
  has the authority of its Home Assistant user;
- the same at-rest, PIN, backup-exclusion, display, clipboard, logging, and
  reset protections apply as for an OAuth refresh token;
- a read-only connection test validates it without executing a configured
  control;
- local reset erases it from Karoo;
- lost-device recovery explains how to revoke it from Home Assistant because
  the app cannot rely on OAuth refresh-token revocation; and
- the UI and diagnostics identify which authentication mode is active without
  ever revealing the token.

If OAuth is feasible, the long-lived-token path is not included merely as a
second convenience option. This keeps the normal path safer and prevents the
fallback from avoiding OAuth setup by default.

Evidence informing this decision:

- <https://developers.home-assistant.io/docs/auth_api/>
- Karoo Garage uses a manually supplied long-lived access token:
  <https://github.com/markhaines/karoo-garage>

### P3-D04: The user selects the scope of the local app lock

Settings offer two local-lock modes:

1. **Protect entire app:** After launch or automatic relock, no control list,
   action, or setup is accessible until the PIN is entered. Ordinary controls
   remain one-tap for the rest of the authorized session; sensitive controls
   still require their distinct confirmation flow.
2. **Protect sensitive controls and settings:** The Quick Access list and
   ordinary controls remain available without a PIN. Direct locks, sensitive
   covers, and all configuration remain locally protected.

Viewing or changing the lock mode requires the current PIN. Selecting the
second mode shows a single concise explanation that every selected script,
scene, button, light, switch, and other ordinary control becomes usable by
anyone holding the Karoo. The app does not block the selection or repeatedly
nag afterward.

This consequence is especially important because Stage 2 deliberately does
not inspect scripts: a script that unlocks a door still looks ordinary to the
app.

### P3-D05: New installations protect the entire app by default

A new installation initially selects **Protect entire app**. This gives the PIN
meaningful protection before the user has reviewed the distinction. Users who
prioritize immediate access to ordinary controls can deliberately switch to
**Protect sensitive controls and settings** without being prevented from doing
so.

### P3-D06: The local PIN accepts 4–12 digits

The MVP uses a numeric PIN of 4–12 digits, entered through a large Karoo-sized
keypad.

- Setup recommends at least six digits but does not reject a shorter valid PIN
  or repeatedly warn about it.
- alphanumeric passphrases, patterns, and biometrics are outside the MVP;
- setup requires the PIN twice and never displays it afterward;
- changing the PIN requires the current PIN;
- forgetting it requires a full local reset and Home Assistant re-pairing; and
- the PIN is never stored directly. Stage 4 must select a salted, versioned,
  memory-hard verifier and credential-protection construction appropriate for
  Karoo 3.

A four- or five-digit PIN preserves resistance to casual use through the app
UI, but materially weakens the offline-guessing resistance required by
P3-D01. The product documents this as a user-selected reduction in protection;
the strength of the verifier must not be overstated.

### P3-D07: Failed PIN attempts cause persistent delays, never automatic erase

Consecutive incorrect PIN entries produce increasing cooldowns up to a maximum
decided in Stage 4. The failure counter and current cooldown survive app and
device restarts. A correct PIN resets the counter.

The app never erases credentials or configuration automatically because of
failed PIN attempts. This avoids turning ordinary failed entry into a way to
destroy the backup access path. Persistent cooldowns protect the normal UI but
cannot solve offline guessing of a short PIN; that remains the explicit
trade-off in P3-D06.

### P3-D08: The PIN is a local authorization boundary, not token scope

The security contract separates local PIN authorization from the authority of
the stored Home Assistant credential:

- Android platform storage protection, not the PIN alone, protects the
  credential bytes at rest.
- In **Protect entire app** mode, relocking blocks all authenticated Home
  Assistant requests until the user enters the PIN again.
- In **Protect sensitive controls and settings** mode, the app may read states
  and send ordinary actions while locally locked. It therefore retains a way
  to use the same Home Assistant credential without a PIN for those operations.
- The PIN gate prevents the unmodified app UI from sending sensitive actions,
  but it does not reduce the underlying token's Home Assistant permissions.
- Anyone who extracts the token or bypasses the app's action checks can use the
  token without knowing the app PIN.

Stage 4 may add PIN-derived encryption where it is compatible with the selected
mode, but the product must not claim that the PIN creates server-enforced
entity or action permissions.

### P3-D09: The two lock modes use distinct PIN flows

#### Protect entire app

1. Opening or returning to a locked app requires the PIN.
2. A successful PIN creates a foreground session.
3. Foreground user interaction resets a configurable inactivity timeout. The
   default is 5 minutes; settings offer 1, 5, 15, and 30 minutes, with no
   **Never** option.
4. Leaving the app foreground or turning off the screen locks it immediately;
   there is no background grace period.
5. Process death, device reboot, explicit **Lock now**, logout, or pairing reset
   also locks it immediately.
6. Because the PIN unlocked a general session rather than a particular
   operation, each sensitive action still requires a separate action-specific
   confirmation, but not another PIN.

#### Protect sensitive controls and settings

1. Opening the app and using ordinary controls requires no PIN.
2. Tapping a sensitive action opens a PIN screen that clearly names the exact
   entity and operation, for example **Unlock Front Door**.
3. Correctly submitting the PIN confirms and sends that one named action. There
   is no second confirmation screen.
4. No reusable sensitive-action session is created. The next sensitive action,
   even immediately afterward, requires the PIN again.
5. Leaving the app, losing foreground, or turning off the screen while the PIN
   prompt is open cancels the unsent action.
6. Opening protected settings requires the PIN. The exact settings-session
   lifecycle will be resolved with the setup and recovery flows rather than
   silently reusing an action authorization.

Once an action has actually been sent, relocking does not pretend to cancel it
or cause it to be retried. The app reconciles its result when it next has the
required access.

This avoids a hidden sensitive-action grace window and makes the PIN itself a
meaningful confirmation only when it is bound to one clearly named action.

### P3-D10: Connectivity policy is user-selectable

The app supports exactly two connection modes:

1. **Wi-Fi only:** All Home Assistant traffic—including authentication, token
   maintenance, discovery, state reads, and ordinary and sensitive
   actions—uses direct Android networking over an eligible Wi-Fi connection.
   The Hammerhead Companion phone bridge is never used.
2. **Any available Karoo connection:** The app may use either direct Wi-Fi or
   Karoo's HTTP path through the paired Hammerhead Companion phone. This
   applies to ordinary and sensitive actions alike.

The user must make this choice during setup; it is not silently inferred from
the type of configured controls. The mode remains changeable in PIN-protected
settings. **Wi-Fi only** is presented as the recommended security choice, but
the product does not prevent a user from deliberately choosing the broader
availability mode.

The broader mode means Home Assistant request material, including bearer
credentials and—when required to complete a current foreground request—OAuth
token-maintenance material, passes through additional trusted Hammerhead
software on Karoo and the paired phone. HTTPS still protects the request on
the network, but it does not remove those components from the trust boundary.
Because a Home Assistant credential can authorize more than the controls
displayed by this app, labeling an on-screen action ordinary or sensitive does
not contain the consequence of credential disclosure. Setup explains this
once, at the point of choosing the mode, in neutral language.

In **Any available Karoo connection** mode, direct Wi-Fi is preferred when it
is eligible; otherwise an individual HTTP request may use the Karoo-routed
path. The app does not promise universal reachability: the paired phone,
Hammerhead Companion connection, phone data service, configured Home Assistant
origin, and Home Assistant server must all be usable.

In **Wi-Fi only** mode, the user may additionally enable an optional
approved-SSID list. When enabled, Home Assistant traffic fails closed if the
active SSID is unavailable or not approved.

SSID matching is defense in depth, not proof of physical presence: an SSID can
be copied or spoofed. The app must describe it only as a network preference.
The direct path must be bound to an eligible Android Wi-Fi network rather than
relying on Karoo's request routing. If eligibility cannot be determined in
Wi-Fi-only mode, no Home Assistant request is sent.

The connection mode and the local PIN/confirmation policy are independent.
Choosing broader connectivity does not disable sensitive-action protection;
choosing Wi-Fi only does not make an action non-sensitive.

### P3-D11: HTTPS by default; constrained local HTTP is opt-in

- HTTPS is the default and recommended connection type.
- Certificate and hostname failures always fail closed. The app provides no
  trust-all switch and does not let the user bypass a certificate error.
- A user may deliberately enable plain HTTP only for a directly connected
  Wi-Fi route to a private-address, link-local, or otherwise verified local
  Home Assistant destination.
- Plain HTTP is never used through the Hammerhead phone bridge, for a public
  destination, or when route/destination classification is ambiguous.
- Enabling local HTTP requires one clear acknowledgement that the Home
  Assistant login and bearer credential are not protected from other parties
  able to observe that network. The app does not repeatedly nag afterward.
- Redirects from HTTPS to HTTP are rejected.

This does not make local HTTP secure; it only contains the exception to the
common Home Assistant LAN configuration where TLS has not been configured.
SSID approval does not compensate for missing encryption.

This mirrors the official Home Assistant Companion app's recommended
connection-security posture without offering its unrestricted, less-secure
HTTP mode. It keeps common local installations usable without pretending HTTP
has the same protection as HTTPS.

Evidence informing this decision:

- <https://companion.home-assistant.io/docs/getting_started/connection-security-level/>
- <https://companion.home-assistant.io/docs/troubleshooting/faqs/>

### P3-D12: User-authorized internal and external origins are trusted

One configured Home Assistant server may have:

- one external origin; and
- an optional, separate internal origin.

Each origin is normalized to its exact scheme, host, and port. Home Assistant
does not expose a cryptographically reliable way for a client to prove that
two different origins terminate at the same instance. Matching names,
versions, entity sets, configuration values, or successful use of one bearer
credential would not provide that proof. The app therefore does not claim to
verify it.

Instead, this follows the official Companion app's trust model:

- the user explicitly configures or approves each origin as an address of the
  same Home Assistant server;
- the app may use the stored server credential with either approved origin;
- choosing between the internal and external origins follows the accepted
  network and HTTP restrictions in P3-D10 and P3-D11;
- adding, editing, or removing an origin is a PIN-protected settings action;
- discovery, server responses, DNS results, and redirects cannot silently add
  another credential destination;
- state-changing requests reject redirects;
- read-only redirect behavior is fail-closed unless Stage 4 proves a concrete
  native Home Assistant flow requires it, and any allowed target must already
  be an explicitly approved origin;
- URL parsing and normalization fail closed when ambiguous;
- replacing the one configured Home Assistant server clears the old
  credential and connection-specific security configuration.

This is an explicit user-trust boundary, not same-instance verification. A
mistyped or maliciously supplied approved origin can receive the bearer
credential and may return misleading state. The app keeps origin changes
deliberate but does not prevent a knowledgeable user from configuring the
internal/external arrangement they need.

Evidence informing this decision:

- The official Companion app documents user-configured internal and external
  URLs and selects between them using the configured home network:
  <https://companion.home-assistant.io/docs/troubleshooting/networking/>
- The official Companion app's connection-security level restricts when an
  unencrypted internal URL may be used:
  <https://companion.home-assistant.io/docs/getting_started/connection-security-level/>

### P3-D13: Origin failover never retries a submitted action

The app may use either eligible, user-approved origin for read-only connection
checks, entity discovery, state retrieval, and post-action reconciliation. A
failed read may fall back to the other eligible origin.

For every state-changing action:

- the app selects exactly one eligible origin before submission;
- once submission begins, it never automatically retries the request through
  the same or the other origin, even if the response is lost or the transport
  reports a failure;
- a lost or ambiguous response is shown as an unknown result rather than
  success or definite failure;
- read-only reconciliation may subsequently use either eligible origin to
  determine the resulting state where the entity contract supports it; and
- repeating the action requires a new deliberate user action, including the
  applicable confirmation or PIN flow.

This applies to ordinary as well as sensitive controls. It preserves Stage 2's
no-retry contract and avoids executing a script, button, scene, or physical
operation twice merely because the first response was unavailable.

### P3-D14: Reset is always locally recoverable; revocation depends on context

An authorized user can disconnect or reset the app from protected settings:

- OAuth mode attempts native server-side refresh-token revocation when the
  server is reachable;
- long-lived-token mode explains the applicable Home Assistant revocation
  step;
- local credential material, the PIN verifier, configured controls, server
  origins, network policy, and all other app configuration are erased whether
  or not the server-side operation succeeds; and
- reset never calls a configured Home Assistant control or changes an entity.

The locked PIN screen also provides **Forgot PIN → Reset app** without requiring
the PIN. It requires a deliberate destructive confirmation and then erases all
local app data. It does not unlock the app, reveal configuration, access the
stored credential, or attempt server-side revocation. Protecting this reset
with the forgotten PIN would not preserve availability against a person who
can already uninstall the app or clear its Android data.

After a forgotten-PIN reset, failed revocation, or lost Karoo, the recovery
guidance tells the user to use another authenticated Home Assistant session to
revoke the Karoo credential or disable/delete its dedicated user as
appropriate. The product provides no project-operated remote wipe, recovery
PIN, escrowed secret, or configuration backup. Android backups must exclude
credentials, the PIN verifier, and connection configuration.

Local erasure and server-side revocation are reported separately. The app must
never claim that erasing Karoo revoked a credential when Home Assistant did not
confirm it.

### P3-D15: Controls are foreground-only and are never queued

The MVP has no offline action queue, delayed delivery, scheduled control,
background automation, or background control receiver.

- A state-changing action can be initiated only from the app's visible,
  foreground UI under the current PIN, confirmation, and connectivity policy.
- Losing foreground, turning off the screen, or closing the relevant flow
  cancels an action that has not begun transmission.
- Once transmission begins, the operation is potentially executed. Losing
  foreground cannot cancel it, and the app does not retry it.
- If the app cannot establish the outcome when the user returns, it reports an
  unknown result and performs only the read-only reconciliation allowed by the
  entity contract.
- Regaining connectivity never causes an earlier tap to be delivered.
- Entity discovery and state refresh run only while the app is actively being
  used. The MVP performs no periodic background synchronization.

OAuth token maintenance may occur only as a necessary part of a current
foreground request; it does not create permission for unrelated background
work.

This makes every control operation contemporaneous with visible user intent
and prevents a stale request from operating a lock or another entity after
network conditions change.

### P3-D16: No automatic telemetry; diagnostics are explicit and sanitized

The MVP includes no analytics, advertising SDK, behavioral tracking, or
automatic crash-report upload. It sends no diagnostic data to the project or a
third party in the background.

Production logs never contain:

- a PIN, PIN verifier, bearer token, OAuth authorization code, refresh token,
  authorization header, cookie, or clipboard content;
- an authenticated request or response body;
- entity names, entity IDs, entity state values, or service-call payloads; or
- a complete server hostname, URL, IP address, SSID, or BSSID.

Protected settings may offer a user-initiated sanitized diagnostic export. It
may contain app and Karoo versions, authentication mode, non-secret
connectivity-policy choices, feature flags, timestamps, and categorized error
codes. It does not contain household data, credentials, PIN material, full
origins, network identifiers, entity data, or raw protocol payloads. The user
previews the export before deliberately sharing it; generating it does not
upload it.

Debug builds may provide additional local technical detail needed during
development, but they still never log or export credentials, PIN material, or
authorization headers. Debug behavior must not be enabled in a release build.

### P3-D17: Sensitive screens request standard Android capture protection

- App-owned PIN entry, OAuth/token setup, manual-token entry, and protected
  settings set Android's standard `WindowManager.LayoutParams.FLAG_SECURE`
  while visible.
- The MVP relies on stock Karoo OS honoring that public Android contract and
  does not require a Karoo-specific screenshot, recording, casting, or
  recent-task probe before release.
- This is an app request to the platform, not a guarantee against a modified,
  rooted, instrumented, or non-conforming OS. Those cases remain outside the
  compromised-platform guarantee excluded by P3-D01.
- Capture protection cannot govern the external browser while it displays Home
  Assistant's OAuth UI.
- The ordinary control list may be captured while it is visibly open, allowing
  deliberate screenshots for documentation and support. The MVP makes no
  separate guarantee that an ordinary screen is absent from a Karoo OS
  recent-task preview.
- A manually entered token is always masked after entry. The app provides no
  reveal or copy action and never places the token onto the clipboard itself.
- The MVP posts no notification containing an entity name, state, control
  action, server identity, PIN, or credential. No product notification feature
  is required for the MVP.

This is intentionally a narrower MVP promise than independently proving every
Karoo-specific capture path. It uses the standard Android mitigation on the
screens that contain local authentication or credential configuration and
documents the residual platform dependency.

Post-MVP hardening may add a real-Karoo capture matrix, verified recent-task
redaction, and Karoo OS regression tests across supported releases. Those are
explicitly deferred improvements, not MVP release gates.

## Open decisions

None.

## Acceptance gate

Stage 3 is accepted. The physical and network attackers, credential scope,
local authorization rules, sensitive-action gates, recovery/revocation
behavior, and residual risks are resolved for the MVP. Accepted Stage 4
decisions record the mechanisms intended to satisfy this posture on Karoo 3
and native Home Assistant.
