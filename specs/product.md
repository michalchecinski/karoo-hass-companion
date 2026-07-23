# Product definition

Status: **Seed draft; unreviewed**. This document is discussion input for
Stages 1 and 2, not an accepted product specification.

## Problem statement

A cyclist may arrive home with an unavailable phone and still need to invoke a
small number of Home Assistant actions from a Hammerhead Karoo 3. Installing a
full Home Assistant client on an unlocked bike computer creates unnecessary
exposure, while installing and maintaining a project-specific component inside
Home Assistant creates onboarding and support friction.

The product should provide a deliberately narrow interface between the Karoo
and Home Assistant, using only supported Home Assistant capabilities on the
server.

The motivating use case is requesting that a front-door lock be unlocked. This
is also the highest-risk use case and must not be generalized from lower-risk
actions such as climate or lighting control.

## Intended user

The initial user is a technically competent Home Assistant owner who:

- owns a Hammerhead Karoo 3;
- can create a regular Home Assistant user and a Home Assistant script;
- accepts that this is a secondary convenience mechanism, not guaranteed
  emergency access;
- understands how to revoke a Home Assistant session if the Karoo is lost.

Supporting non-technical household members or managed enterprise deployments
is outside the MVP.

## Goals

- **PROD-G01**: Invoke a small, explicit set of Home Assistant actions from a
  Karoo-appropriate interface.
- **PROD-G02**: Require no HACS component, Home Assistant App, custom
  integration, webhook automation, or modification of Home Assistant internal
  storage.
- **PROD-G03**: Use supported Home Assistant authentication and APIs.
- **PROD-G04**: Limit damage from accidental use, device loss, credential
  disclosure, and misleading success feedback.
- **PROD-G05**: Make the credential independently revocable from Home
  Assistant.
- **PROD-G06**: Remain usable on a small display, outdoors, and with gloves.
- **PROD-G07**: Fail closed when network, identity, or action state is
  uncertain.

## Non-goals

- Reimplementing the Home Assistant dashboard or Companion App.
- Browsing arbitrary entities, services, automations, or history during normal
  use.
- Controlling Home Assistant over phone Bluetooth tethering, cellular data, or
  a public Home Assistant URL in the MVP.
- Creating Home Assistant users, scripts, permission groups, or automations on
  the user's behalf.
- Editing `.storage`, `configuration.yaml`, or other Home Assistant server
  files.
- Providing an unattended background automation or location tracker.
- Guaranteeing access to the home during power, Wi-Fi, Home Assistant, smart
  lock, or Karoo failure.
- Treating an app PIN on an otherwise unlocked Karoo as equivalent to a
  hardware-backed device unlock.

## Proposed product shape

The MVP is a Karoo application with two modes:

1. **Setup mode** authenticates to a local Home Assistant instance, configures
   an app PIN, and selects a bounded list of existing `script` entities.
2. **Ride mode** displays large action buttons. An action is available only
   after the app is unlocked and the required network policy is satisfied.

Supporting only Home Assistant scripts is an intentional proposed constraint.
Scripts allow the Home Assistant owner to use native actions, conditions,
notifications, and sequencing while keeping the Karoo app's protocol fixed to
`script.turn_on`. Direct domain/service/entity configuration is deferred.

For safety-sensitive actions, Home Assistant scripts should use explicit
actions such as `lock.unlock`, stable `entity_id` targets, and sequential
execution. They should not use toggles whose result depends on current state.

## Principal use cases

### UC-01: Arrival action with unavailable phone

The rider reaches home, the Karoo joins the approved home Wi-Fi, the rider
unlocks the app with its PIN, confirms the configured arrival action, and sees
whether Home Assistant accepted the request.

### UC-02: Lower-risk convenience action

The rider invokes a script such as pre-heating, cooling, exterior lighting, or
an arrival scene under the same authentication and network controls.

### UC-03: Lost-device response

The owner uses another device to revoke the Karoo's Home Assistant session or
delete the dedicated user. Subsequent requests from the Karoo fail.

### UC-04: Unsafe or ambiguous environment

When the Karoo is not on an approved Wi-Fi connection, is using a tethered
route, has not been unlocked, or cannot establish authenticated communication,
the app does not submit an action.

## Challenged assumptions

### "Home Wi-Fi means the owner is at the door"

It does not. Coverage may extend beyond the apartment, network names can be
imitated, and a stolen Karoo may already possess the Wi-Fi credential. Wi-Fi
restriction reduces remote exposure but is not user authentication.

### "An app PIN makes an unlocked Karoo secure"

It mainly deters casual use. Its value depends on how it protects the stored
credential, retry handling, Android keystore behavior, Karoo debug access, and
whether an attacker can extract or replace application data.

### "A dedicated regular Home Assistant user is least privilege"

It removes administrative privileges but is not automatically limited to one
script. Client-side action allowlisting is valuable but is not a Home
Assistant server-side authorization boundary.

### "This is a reliable backup for a dead phone"

The path still depends on the Karoo battery, Wi-Fi, Home Assistant, the lock
integration, and household power. A physical key, keypad, or independent lock
credential remains a more dependable emergency-access mechanism.

### "Native means secure"

Using supported native APIs reduces maintenance and avoids custom server code,
but it does not create scoped credentials that Home Assistant does not provide.
The design must describe its residual authority honestly.

## Success criteria

- A new user can pair a supported Karoo with no server-side software install.
- No long-lived access token is manually copied into the app.
- A configured action cannot be submitted through a disallowed network path.
- A single lost-device operation invalidates future Home Assistant access.
- The UI never labels a service request as a confirmed physical outcome unless
  it has independently observed the configured result state.
- The shipped application exposes no general service-call or entity-control
  interface.

## Product risks

- The native Home Assistant permission model may leave the credential broader
  than the UI.
- Secure local HTTPS is uncommon enough to create onboarding pressure toward
  unsafe cleartext HTTP.
- Karoo networking APIs may not reliably distinguish Wi-Fi from phone
  tethering without platform-specific work.
- Karoo OS updates may affect sideloading, background behavior, or extension
  integration.
- The door-lock use case may create expectations of emergency reliability the
  product cannot meet.

## Blocking product decisions

1. **PD-01 — Lock scope:** Is front-door unlock an explicit MVP feature, or an
   advanced post-MVP capability after lower-risk actions validate the platform?
2. **PD-02 — Script-only MVP:** Accept or reject limiting buttons to existing
   Home Assistant `script` entities.
3. **PD-03 — Transport security:** Must lock-capable configurations require
   local HTTPS, or may users explicitly accept local cleartext HTTP?
4. **PD-04 — Verification:** Is "Home Assistant accepted the request" adequate
   for MVP, or must sensitive actions configure an entity/state used to confirm
   the physical result?
5. **PD-05 — PIN policy:** Choose minimum PIN/passphrase strength and the
   destructive retry policy appropriate for an unlocked device.

## Non-blocking later decisions

- Maximum number and layout of action buttons.
- Support for multiple Home Assistant instances.
- Configuration export/import without exporting credentials.
- Karoo ride-extension surface versus a standalone app-only surface.
- Distribution channel and update mechanism.
