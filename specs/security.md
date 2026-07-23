# Security model

Status: **Seed draft; unreviewed**. This document is discussion input for Stage
3, not an accepted threat model.

## Security objective

Compromise or misuse of the Karoo application should not silently become
administrative access to Home Assistant or an unrestricted remote method for
unlocking the home. Where the native Home Assistant stack cannot enforce the
desired scope, the residual authority must be documented rather than hidden by
the user interface.

## Protected assets

1. Physical access controlled by the front-door lock.
2. Authority to control other Home Assistant entities.
3. Home Assistant OAuth refresh and access tokens.
4. App PIN/passphrase and its verification material.
5. Home Assistant address, selected action metadata, and network allowlist.
6. Security-relevant logs that could reveal credentials or household details.

The home Wi-Fi credential is managed by the Karoo operating system and remains
relevant to the threat model even though this app does not store it.

## Adversaries

### A1: Casual finder

Possesses an unlocked lost or stolen Karoo and can operate its normal UI but
does not modify the operating system.

### A2: Technically capable physical attacker

Possesses the device, can connect it to a computer, inspect installed APKs,
attempt app-data extraction, and repeatedly interact with the application.

### A3: Malicious co-installed application

Runs under another Android application identity and attempts to invoke exported
components, observe data, or abuse inter-process communication.

### A4: Remote network attacker

Can reach an externally exposed Home Assistant endpoint or control an
untrusted network but does not initially possess the Karoo credential.

### A5: Misconfiguration or implementation error

Includes pairing an administrator account, selecting an external URL, allowing
phone tethering, logging secrets, exposing Android components, or reporting a
false success state.

## Explicitly out of scope

The application cannot promise to withstand:

- a rooted or fully compromised Karoo operating system;
- a compromised Home Assistant host or administrator account;
- a compromised smart-lock integration or lock vendor account;
- an attacker who controls the home LAN and the Karoo;
- physical attacks against the lock itself;
- denial of service against power, Wi-Fi, Home Assistant, or the lock.

These exclusions do not permit avoidable insecure defaults.

## Trust boundaries

1. **User to app:** The app PIN authorizes local use of stored credentials.
2. **App storage to app process:** Secrets cross this boundary only after
   successful local authorization.
3. **Karoo to network:** The app must decide whether the active route satisfies
   policy before any credential leaves the device.
4. **Network to Home Assistant:** TLS and Home Assistant authentication protect
   the API request.
5. **Home Assistant identity to entity action:** Home Assistant permissions are
   the server boundary; the script allowlist is an additional client boundary.
6. **Service acceptance to physical result:** A successful API response is not
   proof that a physical lock changed state.

## Required controls

### Identity and authorization

- **SEC-I01**: The app MUST use Home Assistant's OAuth authorization-code flow.
- **SEC-I02**: The app MUST NOT accept manually pasted long-lived access
  tokens, Home Assistant passwords, generic webhook IDs, or mobile-app webhook
  secrets as its operational credential.
- **SEC-I03**: The app MUST require a dedicated, active, non-administrator Home
  Assistant user for production pairing.
- **SEC-I04**: The setup guidance MUST require that user to be configured for
  local access only.
- **SEC-I05**: If the API exposes enough information, the app MUST verify
  `non-administrator` and `local access only`; otherwise setup MUST clearly
  identify which property remains user-attested.
- **SEC-I06**: Normal operation MUST use only short-lived access tokens. The
  refresh token MUST be independently revocable in Home Assistant.
- **SEC-I07**: The app MUST invoke only locally allowlisted `script` entity IDs
  in the MVP and MUST NOT expose a general domain/service call facility.
- **SEC-I08**: Documentation MUST state that the Home Assistant credential may
  retain broader regular-user authority than the selected scripts.

### Secret storage and local authorization

- **SEC-S01**: Refresh tokens MUST be encrypted at rest using Android Keystore
  protection and an app-secret design that incorporates the user's
  PIN/passphrase.
- **SEC-S02**: Access tokens MUST be held in memory only and cleared on app
  lock, process backgrounding beyond the configured grace period, logout, or
  pairing reset.
- **SEC-S03**: The app MUST NOT display, export, copy, log, back up, or include
  authentication secrets in crash reports.
- **SEC-S04**: PIN verification MUST use a memory-hard password derivation
  function with per-installation random salt and versioned parameters.
- **SEC-S05**: Failed attempts MUST incur increasing delays. The accepted
  specification must decide whether a retry threshold erases the local
  credential.
- **SEC-S06**: Changing or disabling the PIN MUST require the existing PIN or a
  full credential reset and OAuth re-pairing.
- **SEC-S07**: The app MUST automatically relock after inactivity and whenever
  the process is no longer trusted to retain foreground authorization.

### Network policy

- **SEC-N01**: The MVP MUST communicate only through an active Wi-Fi network.
- **SEC-N02**: The app MUST NOT intentionally route Home Assistant requests
  through Karoo phone/Bluetooth tethering.
- **SEC-N03**: The Home Assistant base URL MUST resolve to an approved local
  address or local hostname. Nabu Casa cloudhooks, remote UI URLs, public IP
  addresses, and generic externally reachable URLs are outside the MVP.
- **SEC-N04**: SSID/BSSID allowlisting MAY add defense in depth but MUST NOT be
  described as proof of physical presence or identity.
- **SEC-N05**: A sensitive action MUST fail closed if transport type, route,
  destination, or TLS state cannot be determined.
- **SEC-N06**: The final specification MUST explicitly decide whether local
  cleartext HTTP is prohibited or permitted behind a high-friction warning.
- **SEC-N07**: Redirects MUST NOT carry an Authorization header to a different
  origin and SHOULD be rejected for action requests.
- **SEC-N08**: TLS certificate errors MUST fail closed; the app MUST NOT provide
  a trust-all certificate option.

### Android application boundary

- **SEC-A01**: Activities, services, receivers, and providers MUST be
  non-exported unless a documented Karoo SDK contract requires otherwise.
- **SEC-A02**: Any required exported component MUST authenticate its caller and
  MUST NOT expose credential-bearing or action-execution operations.
- **SEC-A03**: Production builds MUST disable debuggability and disallow
  cleartext traffic except for an explicitly accepted local-HTTP design.
- **SEC-A04**: Application backup MUST exclude all credentials and sensitive
  configuration.
- **SEC-A05**: External intents, deep links, and imported configuration MUST NOT
  be able to trigger an action without the same PIN, network, and confirmation
  gates as direct UI use.

### Action safety and feedback

- **SEC-X01**: An action MUST require a deliberate confirmation distinct from
  selecting its button.
- **SEC-X02**: The app MUST debounce repeated input and MUST NOT automatically
  retry a state-changing request whose completion is ambiguous.
- **SEC-X03**: The app MUST distinguish `not sent`, `sending`, `accepted by Home
  Assistant`, `verified result`, and `failed/unknown` states.
- **SEC-X04**: Only an independently observed state satisfying the configured
  verification rule may be shown as a verified physical result.
- **SEC-X05**: Sensitive actions MUST use explicit semantics. The app and setup
  documentation MUST discourage `toggle` behavior for locks, covers, doors, and
  alarms.
- **SEC-X06**: Logs MUST contain timestamps and coarse result categories but no
  secrets, full request bodies, or unnecessary household data.

### Recovery

- **SEC-R01**: The app MUST provide a local logout/reset that erases all stored
  tokens and action configuration.
- **SEC-R02**: Documentation MUST provide a lost-device revocation procedure
  that does not require possession of the Karoo.
- **SEC-R03**: A revoked refresh token, deleted user, or disabled user MUST
  cause the app to clear local authenticated state and require re-pairing.

## Residual risks

Even with these controls:

- a stolen device can already know the home Wi-Fi credential;
- a low-entropy PIN can be attacked if app/keystore protections are bypassed;
- a regular Home Assistant user's token can be broader than the app's script
  list;
- local-only operation does not prove the rider is the legitimate owner;
- Home Assistant accepting `script.turn_on` does not prove the lock moved;
- a software-only system remains less reliable than an independent physical
  access method.

The release documentation must present these as design limits, not edge cases.

## Security verification plan

Before a lock-capable release, verification must include:

- static inspection of the merged Android manifest for exported components;
- tests that secrets never appear in logs, backups, saved state, or intents;
- route tests for Wi-Fi, disconnected, Bluetooth tethered, captive, and
  ambiguous network states;
- redirect and certificate-failure tests;
- OAuth revocation and user-deletion tests;
- repeated-tap, timeout, process-death, and ambiguous-response tests;
- adversarial configuration tests using admin users, external URLs, and unsafe
  scripts;
- a documented physical-device assessment on the supported Karoo OS version.
