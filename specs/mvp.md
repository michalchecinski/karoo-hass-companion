# MVP requirements

Status: **Seed draft; unreviewed**. This document is discussion input for Stage
6, not an accepted implementation contract.

This document defines the first implementable slice. Security requirements in
[security.md](security.md) are normative and apply even when not repeated here.

## Preconditions

The user has:

- a Hammerhead Karoo 3 on a supported Karoo OS version;
- a reachable local Home Assistant instance;
- a dedicated regular Home Assistant user configured for local-only access;
- one or more existing Home Assistant scripts intended for Karoo use;
- an independent way to enter the home if the software path fails.

## Onboarding

- **ONB-001**: The app MUST discover Home Assistant using the supported local
  discovery mechanism or accept a manually entered local base URL.
- **ONB-002**: The app MUST authenticate through the system browser or another
  supported browser surface using Home Assistant OAuth; it MUST NOT collect the
  Home Assistant password itself.
- **ONB-003**: OAuth state and redirect validation MUST prevent authorization
  response substitution.
- **ONB-004**: Pairing MUST fail for an administrator account when this can be
  determined through supported Home Assistant APIs.
- **ONB-005**: Before storing a credential, setup MUST explain the dedicated
  regular/local-only user requirement and obtain explicit confirmation for any
  property the app cannot verify.
- **ONB-006**: The user MUST create and confirm an app PIN/passphrase before the
  refresh token is committed to persistent storage.
- **ONB-007**: Setup MUST test an authenticated, read-only request before it can
  complete.
- **ONB-008**: Setup MUST NOT create or modify Home Assistant server
  configuration.

## Action configuration

- **ACT-001**: The MVP MUST support only existing Home Assistant entities in
  the `script` domain.
- **ACT-002**: The app MUST obtain selectable scripts through an authenticated
  Home Assistant API rather than free-text entity entry.
- **ACT-003**: Each configured action MUST contain a local immutable identifier,
  user-visible label, selected `script` entity ID, sensitivity classification,
  ordering, and optional verification rule.
- **ACT-004**: The MVP MUST support at least one and at most six configured
  actions.
- **ACT-005**: All configured actions MUST default to sensitive.
- **ACT-006**: Editing, adding, deleting, or reordering actions MUST require the
  app to be unlocked.
- **ACT-007**: Imported configuration MUST NOT contain credentials and MUST NOT
  silently add an executable action.

## Action execution

- **ACT-101**: Selecting an action MUST open a confirmation view; selection
  alone MUST NOT send a request.
- **ACT-102**: Confirmation MUST identify the exact action and require a
  deliberate second gesture suitable for a small touch display.
- **ACT-103**: Immediately before sending, the app MUST re-evaluate app-lock,
  network, destination, and configuration policy.
- **ACT-104**: The only state-changing MVP request MUST invoke
  `script.turn_on` for the configured script entity.
- **ACT-105**: The app MUST permit at most one in-flight action request.
- **ACT-106**: The app MUST NOT automatically retry an action after timeout,
  connection loss, process death, or an otherwise ambiguous result.
- **ACT-107**: A successful HTTP/service response MUST be displayed as
  "Accepted by Home Assistant" or equivalent, not as a confirmed physical
  result.
- **ACT-108**: If a verification rule is configured, the app MAY read the
  selected entity until the expected state is observed or a bounded timeout is
  reached. Only then may it display a verified result.
- **ACT-109**: The result view MUST preserve uncertainty and offer a safe way to
  dismiss it; it MUST NOT offer a blind one-tap retry.

## Connectivity

- **NET-001**: Action execution MUST require an active Wi-Fi transport.
- **NET-002**: Action execution MUST be blocked over Bluetooth/phone tethering,
  cellular, VPN-only, unknown, or disconnected transport in the MVP.
- **NET-003**: The user MUST be able to select one or more approved Wi-Fi
  identifiers when the platform exposes them with acceptable permissions.
- **NET-004**: Network-name checks MUST be treated as defense in depth; the
  local Home Assistant destination and authenticated API remain mandatory.
- **NET-005**: The app MUST show why execution is blocked without revealing
  credentials or sensitive endpoint details.
- **NET-006**: The app MUST not use Karoo networking facilities that silently
  fall back to the paired phone for an action request.

## Karoo user experience

- **UX-001**: The primary view MUST show no more than six high-contrast,
  glove-usable action targets without requiring precise gestures.
- **UX-002**: Locked, network-blocked, ready, confirming, sending, accepted,
  verified, and failed/unknown states MUST be visually distinguishable without
  color as the only signal.
- **UX-003**: Sensitive action controls MUST not be available from a lock-screen
  notification, widget, unprotected deep link, or other bypass surface.
- **UX-004**: Returning from background beyond the accepted grace period MUST
  show the app locked.
- **UX-005**: The UI MUST remain comprehensible in portrait Karoo dimensions
  and under large system font settings.
- **UX-006**: The application MUST warn during setup that it is not guaranteed
  emergency access and recommend an independent fallback.

## Recovery and diagnostics

- **REC-001**: The settings view MUST offer logout/reset after local
  authentication.
- **REC-002**: Logout/reset MUST erase tokens, derived secrets, cached states,
  action configuration, and sensitive diagnostic data.
- **REC-003**: Authentication failure caused by revocation or user removal MUST
  transition to a non-operational re-pairing state.
- **REC-004**: The documentation MUST identify the exact Home Assistant UI
  procedure for revoking the Karoo credential.
- **OPS-001**: The app MUST maintain a bounded local audit history containing
  time, action label or opaque ID, pre-send policy outcome, and coarse result.
- **OPS-002**: Diagnostic export MUST be opt-in, redact endpoint and household
  identifiers by default, and never contain authentication material.
- **OPS-003**: Failures MUST use stable internal categories suitable for tests
  and support documentation.

## Compatibility research requirements

- **COMP-001**: Before implementation planning is accepted, the project MUST
  document the supported Karoo OS/API range and installation method.
- **COMP-002**: A research spike MUST determine whether the product is a Karoo
  extension, standalone Android activity, or both.
- **COMP-003**: A research spike MUST demonstrate that Home Assistant requests
  can be bound to Wi-Fi without fallback to phone tethering.
- **COMP-004**: A research spike MUST demonstrate OAuth redirect handling on a
  real Karoo 3.
- **COMP-005**: A research spike MUST determine available Android Keystore
  characteristics and backup/debug behavior on a real Karoo 3.

## Acceptance scenarios

### AS-01: Successful pairing

**Given** a supported Karoo on the home Wi-Fi and a dedicated regular user,
**when** the user completes OAuth and configures the app PIN,
**then** the app stores no Home Assistant password or long-lived access token,
proves an authenticated API request works, and proceeds to script selection.

### AS-02: Administrator pairing is rejected

**Given** an administrator authorizes the app,
**when** the app retrieves supported identity information,
**then** pairing stops before the refresh token becomes operational and explains
how to create a dedicated regular user.

### AS-03: Script execution on approved Wi-Fi

**Given** the app is unlocked, the approved Wi-Fi route is active, and a script
is configured,
**when** the user selects and deliberately confirms it,
**then** exactly one `script.turn_on` request is sent and the UI reports the
service acceptance separately from any physical result.

### AS-04: Phone tethering is blocked

**Given** Home Assistant would be reachable through the paired phone but no
approved Wi-Fi route is active,
**when** the user attempts an action,
**then** no credential-bearing request is sent and the UI reports that Wi-Fi is
required.

### AS-05: Ambiguous response is not retried

**Given** an action request is sent and the connection is lost before a
definitive response,
**when** the result screen appears,
**then** it reports an unknown outcome and does not automatically or implicitly
retry.

### AS-06: Revoked credential

**Given** the owner revokes the Karoo session in Home Assistant,
**when** the app next refreshes or invokes an authenticated API,
**then** it clears operational authentication state, sends no action, and asks
for re-pairing.

### AS-07: Locked or backgrounded application

**Given** the inactivity grace period elapsed or the app was backgrounded,
**when** an action is reached through any supported entry point,
**then** PIN/passphrase authorization is required before confirmation and
execution.

### AS-08: False-success prevention

**Given** Home Assistant accepts the script call but the lock does not change
state,
**when** no configured verification rule reaches its expected state,
**then** the app never displays "Door unlocked" or another verified-success
claim.

### AS-09: Repeated input

**Given** an action is being confirmed or sent,
**when** the user taps repeatedly,
**then** at most one request exists and no queued duplicate executes later.

## Exit criteria for MVP specification

The MVP specification may be accepted only after:

- `PD-01` through `PD-05` are resolved;
- `COMP-001` through `COMP-005` have planned owners and evidence format;
- each required security control has a planned verification method;
- the Home Assistant API choice in ADR-0001 is accepted;
- implementation work is divided into traceable vertical slices.
