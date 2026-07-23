# Stage 1: Product direction

Status: **Accepted on 2026-07-23**

This stage defines what the product is. It intentionally does not yet settle
the detailed feature list, security mechanisms, protocol, or screen flows.

## Accepted direction

### P1-D01: General Home Assistant companion

The product is a general Home Assistant control companion for Karoo, not a
single-purpose door-lock utility. Door locking and unlocking remain a primary
motivating use case and the highest-risk early control.

### P1-D02: Controls only; no dashboard

The product resembles the curated Quick Access interaction of Home Assistant's
official CarPlay experience, not the full Home Assistant dashboard.

It provides no:

- Areas browser;
- all-entities or all-domains browser during normal use;
- sensor/status-card dashboard;
- history, logbook, energy, media, camera, or map view;
- automatic reproduction of a user's Lovelace dashboard.

The user configures a bounded set of favorite controls. Normal operation shows
only those controls.

### P1-D03: State belongs to a control

No separate read-only status-card feature is planned. A stateful control may—and
for safety-sensitive controls must—show the state necessary to operate it
safely. For example, a front-door control may show `Locked` before offering
`Unlock`.

This is operational feedback, not a dashboard. The application should not
monitor or display unrelated entities merely because it can read them.

Stateless controls such as scripts show request progress and outcome rather than
inventing a physical state.

### P1-D04: Direct lock controls belong in the MVP

The first public MVP includes direct, state-aware Home Assistant lock controls.
It does not rely exclusively on a user-created lock script. Detailed lock
operations and security gates are deferred to Stages 2 and 3.

### P1-D05: Delivery order

The accepted implementation order is:

1. **Secure foundation and scripts** — connect to Home Assistant, protect the
   local session, select scripts, and invoke them deliberately.
2. **Direct locks** — add state-aware lock controls and the sensitive-action
   policy. Completion of this slice defines the first public MVP.
3. **Stateless native controls** — add `button`, `input_button`, and `scene`.
4. **Ordinary binary controls** — add `light`, `switch`, and `input_boolean`.
5. **Covers** — add explicit open, close, and stop behavior with sensitive
   classification for doors, garages, and gates.

Climate control remains expressible through a Home Assistant script until a
later stage demonstrates that a dedicated control provides enough value to
justify its additional UI and state model.

### P1-D06: Large CarPlay-style controls

Normal operation uses large, high-contrast control tiles sized for the Karoo 3
display and imprecise or gloved touch. A control combines a recognizable icon,
short user-facing name, and—when stateful—the minimum state needed to choose the
correct operation.

The product must not shrink controls into a dense dashboard, settings-style
entity list, or tiny icon grid merely to show more favorites simultaneously.
Scrolling through a small curated collection is preferable to reducing touch
targets.

For a sensitive control, tapping the large tile selects the control and opens
its confirmation flow. It does not directly execute the operation. Exact touch
target dimensions, layout, control count per screen, hardware-button behavior,
and outdoor/glove validation belong to Stage 5.

### P1-D07: Guided setup without audience labels

The first release may assume that setup includes creating or selecting an
appropriately restricted Home Assistant account, choosing supported entities,
and reviewing security options. The product must explain these tasks with
clear, guided instructions instead of describing itself as being only for
"enthusiasts," "advanced users," or another technical audience.

Setup must not conceal security choices to appear effortless, but product copy
must also avoid making ordinary Home Assistant users feel excluded. Improving
onboarding later must not require weakening the underlying security model.

## Consequences

- The first internal build can be useful as a script launcher before the public
  MVP is complete.
- Putting locks second deliberately front-loads the hardest security and state
  semantics. It slows the public MVP but tests the motivating use case early.
- The control model must be extensible by Home Assistant domain without
  exposing a general service-call console.
- The small-screen information hierarchy prioritizes large controls over the
  number of controls visible at once.
- Stateful controls must use explicit operations. Locks must use supported
  lock, unlock, or open operations as separately reviewed capabilities; they
  must never use generic toggle semantics.
- Entity references use stable Home Assistant `entity_id` values selected from
  supported API data rather than Android-side free text or Home Assistant
  `device_id` values.

## Accepted product statement

> A focused Home Assistant control companion for Hammerhead Karoo. Choose a
> small set of scripts and device controls, then operate them through a simple,
> ride-friendly interface without installing custom software in Home
> Assistant.

## Acceptance gate

Stage 1 is accepted. Stage 2 will define concrete use cases and the scope of
each delivery slice without changing the accepted product direction.
