# Stage 4: System architecture

Status: **Accepted on 2026-07-23**

Stage 4 defines how the Karoo application and native Home Assistant
capabilities interact, establishes dependency rules, and accepts the relevant
architecture decisions.

## Binding architectural input

### P4-I01: KarooFirefly-inspired responsibility layers

Use the layered structure documented by
[KarooFirefly](https://github.com/derstrassi/karoofirefly#layers) as an
architectural reference. Reuse its separation of platform integration,
device/service communication, domain behavior, persistence, extension entry
point, and UI—not its bike-light-specific package names or implementation.

The accepted responsibility mapping is:

1. **Karoo integration** — adapters for supported `karoo-ext` capabilities.
2. **Home Assistant** — authentication and native REST/WebSocket API adapters.
3. **Controls** — platform-independent control models, capabilities, and
   invocation use cases.
4. **Security** — credential protection, local app access, and enforcement of
   accepted security and network policy.
5. **Data** — connection settings, selected controls, ordering, and other
   persisted preferences.
6. **Extension entry point** — `KarooExtension` lifecycle and dependency
   composition.
7. **UI** — Jetpack Compose setup, control, confirmation, and recovery flows.

The mapping deliberately replaces KarooFirefly's `ble/`, `light/`, and
`engine/` responsibilities with concerns belonging to this product.

## Accepted decisions

### P4-D01: One application module with responsibility packages

The application follows KarooFirefly's structural approach for both the MVP
and later product development:

- one Android `app` Gradle module;
- top-level Kotlin packages representing the Karoo integration, Home
  Assistant, controls, security, data, extension entry point, and UI
  responsibilities;
- tests organized alongside or mirroring those responsibility boundaries; and
- no Gradle module per architectural layer.

These are architectural boundaries even though Gradle does not enforce them.
New code is placed by responsibility rather than collected in generic
`common`, `utils`, or `managers` packages. Interfaces and dependency rules will
provide the seams needed for unit testing and future extraction.

The domain models and behavior used to represent controls must remain usable in
plain Kotlin tests without Karoo, Android UI, Home Assistant transport, or
persistence types. Exact package names and the permitted import directions are
resolved separately.

This is the intended long-term architecture, not temporary MVP scaffolding.
Later features extend the responsibility packages or add focused subpackages
inside the same application module. A move to separate Gradle modules is not on
the product roadmap and would require a new, explicit architecture decision
supported by concrete evidence that the current structure no longer works.

### P4-D02: Dependencies point toward platform-independent behavior

The single module uses a lightweight ports-and-adapters dependency rule:

- `controls` owns platform-independent control models, use cases, and the
  interfaces those use cases require;
- `security` owns local-authorization and connection-policy behavior and
  implements or supplies the security interfaces required by control use
  cases;
- `homeassistant`, `data`, and `karoo` are outer implementations of interfaces
  defined by the inner behavior they support;
- `ui` calls use cases and purpose-specific security session APIs rather than
  concrete network, persistence, or keystore implementations;
- the extension entry point can request that the application open or lock but
  cannot invoke a Home Assistant control;
- concrete implementations meet only in the application composition root; and
- Android, `karoo-ext`, Home Assistant DTO, serialization, database, and
  keystore types do not leak into control-domain models.

An interface is introduced for a real boundary or test seam, not for every
class. Package placement alone does not justify an abstraction. This keeps the
dependency direction durable without turning the single-module application
into a ceremonial clean-architecture framework.

Representative dependency flow:

```text
UI ----------------> Controls/use cases
UI ----------------> Security session API

Home Assistant ----> Controls-owned gateway interfaces
Data --------------> Controls/Security-owned repository interfaces
Security ----------> Controls-owned authorization interfaces and models
Karoo/Android -----> Core-owned platform interfaces

Extension ---------> App launch/lock boundary only
Composition root --> Constructs and connects concrete implementations
```

### P4-D03: Manual constructor injection with one small application container

The application does not use Hilt, Koin, Dagger, or another dependency
injection framework, and it has no `di` package.

- Dependencies are explicit constructor parameters.
- A small `AppContainer` is the composition root for concrete application
  dependencies.
- `KarooHassApplication` owns the container and dependencies whose lifetime is
  the application process.
- ViewModels receive their dependencies through explicit factories.
- Tests construct use cases directly with fakes or in-memory implementations.
- Product code does not reach into the Application object as a general service
  locator and does not use a global mutable dependency singleton.
- The base package contains only the Application class and composition-root
  types; behavior remains in its responsibility package.

The long-term responsibility packages beneath the eventual base namespace are:

```text
controls/
security/
homeassistant/
karoo/
data/
extension/
ui/
```

Focused subpackages are introduced when a responsibility genuinely needs them,
for example `homeassistant/auth` or `ui/settings`. Generic `common`, `utils`,
`services`, and `managers` packages are not part of the architecture.

This follows KarooFirefly's direct, single-module construction style while
retaining constructor seams for the stronger security and transport testing
required by this application. Adding a dependency-injection framework later
requires a separate, explicit architecture decision.

### P4-D04: Two policy-selected Home Assistant transport adapters

The architecture exposes one Home Assistant gateway to control use cases while
keeping two transport implementations behind it:

1. **Direct transport** uses the normal Android network stack. It can bind HTTP
   and WebSocket connections to an eligible Wi-Fi network and is the only
   transport available in **Wi-Fi only** mode.
2. **Karoo-routed HTTP transport** adapts
   `OnHttpResponse.MakeHttpRequest`, potentially through a pinned
   `ktor-client-karoo` dependency. It can operate through Wi-Fi or the paired
   Hammerhead Companion phone, but supports request/response HTTP only.

The security-owned connection policy chooses the eligible transport before a
Home Assistant operation begins. Control use cases and UI code do not choose a
network client directly. A state-changing request is submitted through exactly
one selected transport and is never retried on the other transport. The
Karoo-routed adapter always submits with `waitForConnection = false`; lack of a
current route is reported immediately and never creates a Karoo-side queue.

In **Any available Karoo connection** mode:

- eligible direct Wi-Fi is preferred;
- the Karoo-routed adapter is the HTTP fallback when direct Wi-Fi is
  unavailable;
- authentication and foreground token maintenance must be able to use the
  selected HTTP transport so the mode does not stop working merely because an
  OAuth access token expires; and
- the same action capabilities remain available through either path. Direct
  lock and sensitive cover actions still require their accepted local
  authorization flow, but transport policy does not prohibit them.

WebSocket discovery and live state are enhancements of the direct path, not a
requirement for sending an already configured control. When only the
Karoo-routed path is available, runtime pre-action state, action submission,
and reconciliation use bounded, entity-specific Home Assistant HTTP requests.
The UI must distinguish a fresh snapshot from an actively updated state and
must continue to honor each control type's fail-closed state requirements.

The Karoo HTTP effect and known Ktor adapter limit requests and responses to
100 KB. The bridge path therefore never fetches an unbounded all-entity state,
registry, or service catalog. For the MVP, initial entity discovery and
refreshing the available-control catalog require direct Wi-Fi; the controls
already selected and stored locally remain usable through the Companion path.
This is a setup limitation, not a reduction in the configured runtime control
set. If an action may have reached Home Assistant but its response exceeds the
limit or is otherwise lost, the outcome is **unknown** and the action is never
retried automatically.

The two transports share Home Assistant request/response models above their
wire adapters where useful, but do not share routing behavior implicitly. The
Karoo-routed implementation cannot be reached in Wi-Fi-only mode, including by
authentication, reads, diagnostics, or fallback error handling. Tests must
prove both positive routing and the absence of this policy bypass.

### P4-D05: Use `ktor-client-karoo` behind the Karoo transport boundary

The Karoo-routed HTTP adapter uses a pinned `ktor-client-karoo` dependency
rather than implementing a second Ktor engine directly on
`OnHttpResponse.MakeHttpRequest`.

- Only `homeassistant` transport implementation code may depend on or expose
  `ktor-client-karoo` types.
- Controls, security policy, UI, and Home Assistant protocol behavior depend
  on the app-owned transport interface, so the library remains replaceable.
- The dependency version is exact rather than a range. Its Ktor and
  `karoo-ext` compatibility must be checked against the versions selected for
  the application.
- The existing GitHub Packages credentials required by the official
  `karoo-ext` dependency are also used to resolve the additional public
  package repository; the project does not introduce a second credential
  workflow.
- The implementation spike must prove that requests are submitted with
  `waitForConnection = false`, coroutine cancellation unregisters the Karoo
  consumer, timeouts have one terminal result, and oversized or lost responses
  become an ambiguous/unknown outcome without retry.
- If the library cannot meet those behaviors without unsafe modification, the
  app replaces only this outer adapter with a direct `karoo-ext`
  implementation. That fallback does not reopen the two-mode product or
  transport-policy decisions.

This accepts a small third-party implementation dependency without making it
an application-wide architectural dependency.

### P4-D06: Use Ktor with separate clients for the two transports

Ktor is the Home Assistant HTTP abstraction for both supported transport
paths. The application constructs separate clients whose engines and security
configuration cannot change implicitly:

1. The **direct client** uses Ktor's OkHttp engine for REST and the Ktor
   WebSockets plugin for a directly networked Home Assistant connection. Its
   underlying OkHttp client is configured to use the Android `Network` selected
   by the Wi-Fi policy.
2. The **Karoo-routed client** uses the pinned `ktor-client-karoo` engine and
   supports HTTP request/response operations only.

Home Assistant endpoint definitions, serialization, response mapping, and
sanitized error categories are shared above the engine-specific adapters where
their semantics are genuinely identical. The clients do not share an engine,
connection pool, cookie store, implicit redirect target, or automatic fallback
behavior.

The security-owned connection policy selects one eligible client before an
operation. Callers cannot ask a general-purpose client to discover the route
after submission begins. WebSockets always use the direct client and are
closed when direct connectivity or the accepted foreground/security lifecycle
no longer permits them.

Ktor and engine types remain inside `homeassistant` implementation code. The
control domain sees app-owned request results and connection-state models.
Both clients disable automatic request retries and credential-bearing body or
header logging. Their redirect and authentication behavior must implement the
accepted origin and no-retry rules rather than relying on permissive library
defaults.

The app pins one Ktor version compatible with the accepted
`ktor-client-karoo`, `karoo-ext`, Android API, and Kotlin versions. A
real-device implementation spike must prove direct Wi-Fi binding, HTTPS,
WebSocket lifecycle, Karoo HTTP cancellation, and the absence of cross-client
fallback before the transport layer is considered verified.

### P4-D07: Security owns authorization policy; Home Assistant owns protocol sessions

Connection authorization and Home Assistant session mechanics have distinct
owners:

- `security` owns foreground/lock authorization, PIN session state, sensitive
  action authorization, the selected connectivity mode, approved-SSID policy,
  and evaluation of the eligible transport and approved origin set for a
  particular operation;
- `homeassistant` owns OAuth protocol behavior, in-memory access-token state,
  foreground token refresh, authenticated REST execution, WebSocket
  authentication and lifecycle, response mapping, and allowed read-only
  reconciliation;
- `data` persists connection preferences, approved origins, selected controls,
  and opaque protected credential material through purpose-specific
  repositories, but makes no authorization, origin, routing, refresh, or
  connection-lifecycle decision; and
- `ui` observes app-owned session and operation states and invokes use cases,
  but cannot directly refresh a token, open a socket, select a transport, or
  retrieve credential material.

Before an operation begins, security returns an immutable, operation-specific
authorization/connection plan. It identifies the permitted transport and
origin candidate or candidates under the accepted rules. Home Assistant code
may execute only within that plan: it may perform the accepted read-only
failover when multiple candidates are supplied, but cannot add an origin,
switch a submitted action to another transport, or weaken a Wi-Fi restriction.

`homeassistant/auth` may request protected OAuth or long-lived-token material
through a narrow credential-vault interface when the current authorized
foreground operation requires it. It never reads a general settings store.
The vault exposes no export or display operation. An access token derived from
OAuth is held only in the Home Assistant session and is cleared according to
the accepted lock, reset, process, and lifecycle rules.

Foreground loss, screen-off, app relock, credential reset, and loss of an
eligible direct network produce explicit invalidation events. The Home
Assistant session responds by cancelling unsent work and closing connections
that are no longer authorized. Work already submitted retains the accepted
unknown-result/no-retry semantics; lifecycle invalidation does not pretend to
recall it.

This keeps protocol state close to the protocol implementation without letting
the network layer decide whether it is allowed to operate.

#### Native current-account visibility

During authenticated setup over the direct WebSocket path, the app uses Home
Assistant's `auth/current_user` command for the concise account assessment
required by Stage 3. The supported response exposes `is_owner` and `is_admin`,
so the app may accurately report those properties without requesting
administrator authority.

The same response does not expose `local_only`. Home Assistant's configuration
auth API includes that property but requires administrator access. This app
does not call the admin-only endpoint or elevate temporarily merely to verify a
recommendation; local-only status remains user-attested. Failure to obtain the
optional assessment does not reject an otherwise valid pairing.

Evidence:

- [`auth/current_user` implementation](https://github.com/home-assistant/core/blob/cbe9a7e9a2291b71139f9aa2f647c27be9076ce9/homeassistant/components/auth/__init__.py#L481-L518)
- [Admin-only auth configuration API](https://github.com/home-assistant/core/blob/cbe9a7e9a2291b71139f9aa2f647c27be9076ce9/homeassistant/components/config/auth.py#L66-L105)

### P4-D08: Platform and library types remain in explicit outer implementations

The single-module package boundaries are enforced by import rules:

- `controls` and the platform-independent parts of `security` contain no
  Android, AndroidX lifecycle, Compose, Ktor, Home Assistant wire DTO,
  persistence-framework, keystore, or `karoo-ext` types;
- Compose and Android UI types remain in `ui`;
- Android credential, network-observation, lifecycle, and persistence types
  remain in focused outer implementations under their owning responsibility,
  such as `security/android` or `data/android`;
- `karoo-ext` types remain in `karoo`, `extension`, and the narrowly scoped
  `homeassistant/transport/karoo` implementation required to construct and use
  the `ktor-client-karoo` engine;
- Ktor and OkHttp types remain in `homeassistant` transport implementations,
  including the direct and Karoo-routed clients;
- Home Assistant REST/WebSocket DTOs remain in `homeassistant` and are mapped
  to app-owned control, session, and result models before crossing an
  inner-facing interface; and
- persistence records remain in `data` and are mapped to app-owned models
  rather than becoming control-domain entities.

ViewModels are part of the outer UI layer and may use AndroidX lifecycle and
coroutine APIs. They expose immutable app-owned UI state and events rather than
Ktor responses, Android network objects, Home Assistant DTOs, database rows,
or Karoo events.

The application composition root is the deliberate exception that may import
all outer implementations in order to construct them. It contains wiring and
lifetime ownership only; it does not become a home for routing, authorization,
mapping, or control behavior.

Where an inner policy needs platform information, an app-owned value or narrow
port crosses the boundary—for example, a normalized connectivity snapshot
rather than Android's `NetworkCapabilities`. Interfaces are not introduced
merely to disguise a platform type inside the same outer adapter.

These rules are verified with package/import architecture tests where
practical, in addition to ordinary code review.

### P4-D09: Use `com.karoohass` as the permanent application identity

The Android application ID and Kotlin base namespace are:

```text
com.karoohass
```

Responsibility packages are placed beneath that namespace, for example
`com.karoohass.controls` and `com.karoohass.homeassistant`.

The identifier is deliberately product-facing rather than tied to the original
developer's personal domain. The product owner accepts that the corresponding
`karoohass.com` domain is not owned and that Android does not enforce
reverse-domain ownership. The application ID still must be treated as
permanent once builds are distributed: changing it would create a different
Android application rather than an in-place update.

Release signing identity, not domain ownership, controls whether a future APK
can update an installed build with this application ID.

## Stage 4 review gate

Accepted by the product owner on 2026-07-23. All previously deferred Stage 4
architecture decisions are resolved. No implementation structure is accepted
merely by appearing in an example; the binding structure and dependency rules
are P4-D01 through P4-D09.
