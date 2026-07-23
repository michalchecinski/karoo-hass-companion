# ADR-0001: Use native Home Assistant authentication and APIs

Status: **Accepted on 2026-07-23**

## Context

The Karoo app needs authenticated access to a deliberately limited set of
user-selected Home Assistant controls. It must not require a project-specific
Home Assistant integration, HACS component, App, hosted account, or
project-operated relay.

Home Assistant offers several native mechanisms:

1. OAuth authorization-code authentication followed by REST or WebSocket API
   calls.
2. Native `mobile_app` registration with an encrypted webhook channel.
3. Automation webhooks identified by secret URLs.
4. Manually generated long-lived access tokens.

The app also needs two Karoo-side connectivity modes: direct Wi-Fi only, or
direct Wi-Fi with Karoo's HTTP-over-Companion path available when Wi-Fi is not
usable.

## Decision

Connect directly to one user-approved Home Assistant server using its native
OAuth, REST, and WebSocket interfaces:

- OAuth authorization code is the preferred authentication flow.
- If and only if a real Karoo 3 spike proves that OAuth cannot be completed
  reliably, the MVP may enable the accepted manually entered Home Assistant
  long-lived-access-token fallback.
- The app never collects a Home Assistant password.
- The app does not register with the built-in `mobile_app` integration and
  does not use a webhook as its control channel.
- The app invokes only the explicit entity operations accepted in Stage 2. It
  does not expose an arbitrary domain/service/data console.
- The selected-control list is an app boundary, not server-enforced token
  scope.

Use native APIs as follows:

- REST handles OAuth token exchange and refresh, bounded state reads, control
  calls, and read-only reconciliation.
- A foreground WebSocket on the direct transport handles supported discovery,
  live state updates, current-user assessment, and other bounded native
  commands where it materially improves behavior.
- The Karoo/Companion transport is HTTP request/response only. Already
  configured controls remain usable through bounded REST operations when that
  is the only available route.

Authentication, API, routing, local authorization, origin, retry, and
credential-storage behavior remains subject to the accepted Stage 3 security
posture and Stage 4 transport architecture.

## Rationale

- OAuth provides a native, independently revocable session without asking the
  user to copy a long-lived secret under normal circumstances.
- REST and WebSocket APIs support the controls-only product without installing
  project code in Home Assistant.
- Direct native calls preserve Home Assistant user attribution and permission
  enforcement.
- The WebSocket path gives efficient live state while direct connectivity is
  available; REST keeps configured controls usable over Karoo's Companion HTTP
  bridge.
- Excluding `mobile_app` registration avoids a server-side device registration,
  webhook secret, and protocol surface that this controls-only app does not
  otherwise need.

## Rejected alternatives

### Native `mobile_app` encrypted webhook

This is a supported built-in Home Assistant feature, but it adds device
registration, a long-lived webhook secret, and an additional encrypted
protocol. Its control operation is not intrinsically scoped to the tiles shown
by the Karoo app. The standard authenticated APIs already provide the required
capabilities.

### Generic automation webhook

A webhook can map a secret URL to one native automation, but the webhook ID
becomes the authentication secret and creates a separate control/configuration
journey. It is a poor fit for a general companion that discovers and invokes
multiple native entity types.

### Long-lived access token as an equal setup option

It is operationally simple but may remain valid for years and encourages
manual secret handling. It is retained only as the explicitly gated fallback
if native OAuth proves infeasible on Karoo, not as a convenience choice next
to working OAuth.

### Custom Home Assistant integration or proxy

A custom component or relay could issue narrower capabilities, add
server-enforced per-control scope, or apply additional rate limits. It is
rejected because zero project-specific server installation and zero
project-operated service are product requirements. The resulting broader
native-user credential authority is an accepted and documented trade-off.

## Consequences

### Positive

- No project-specific installation inside Home Assistant.
- No hosted project account or relay.
- Native session revocation and Home Assistant user attribution under OAuth.
- Live state on direct connectivity and bounded control availability through
  the Companion HTTP path.
- Existing Home Assistant scripts, scenes, entities, and integrations remain
  the behavior source of truth.

### Negative

- A Home Assistant user credential generally has more authority than the
  controls selected in this app.
- OAuth browser return behavior must work on the Karoo platform or the less
  desirable long-lived-token fallback becomes necessary.
- The Companion route expands the trusted software boundary and cannot carry a
  WebSocket.
- Karoo's 100 KB HTTP limit prevents bulk discovery over the Companion path,
  so initial discovery and catalog refresh require direct Wi-Fi in the MVP.
- Two transports and two user-selectable connectivity policies require
  explicit routing and negative policy tests.

## Verification required

Acceptance of this ADR selects the architecture; it does not claim that the
platform spike has already passed. Before the corresponding implementation is
verified:

1. Complete OAuth authorization, redirect validation, refresh, and revocation
   on a real supported Karoo 3.
2. If OAuth fails, document the failure evidence before enabling the accepted
   long-lived-token fallback.
3. Verify REST control calls and WebSocket authentication, discovery, state,
   and current-user assessment against a supported Home Assistant release.
4. Verify direct Android Wi-Fi binding and prove that Wi-Fi-only mode never
   reaches the Karoo-routed client.
5. Verify `ktor-client-karoo` cancellation, timeout, 100 KB behavior, and
   `waitForConnection = false` on a real Karoo/Companion pairing.
6. Verify that a submitted state-changing request never retries or fails over
   to another transport or origin.
7. Verify token revocation, user deletion/disablement behavior, protected
   storage, and sanitized diagnostics.

## Revisit conditions

Reconsider this decision if Home Assistant adds supported scoped application
tokens, first-class per-application capabilities, or a stable native mechanism
that materially reduces credential authority without custom server software.
Also reconsider it if Karoo removes or materially changes Companion HTTP
routing, or if the pinned client engine cannot satisfy the accepted no-queue
and no-retry guarantees.
