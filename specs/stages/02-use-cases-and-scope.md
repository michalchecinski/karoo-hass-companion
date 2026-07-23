# Stage 2: Use cases and scope

Status: **Accepted**

This stage defines the concrete user journeys and feature boundaries for each
delivery slice. It does not yet choose authentication mechanisms, network
policy, API architecture, or exact screen layouts.

## Accepted decisions

### P2-D01: Scripts require no runtime input in the MVP

The script slice supports invoking a configured Home Assistant script without
supplying fields or variables at execution time. The Karoo app will not render
forms for script fields, selectors, or arbitrary service data in the MVP.

Users who need parameters can create a parameterless wrapper script in Home
Assistant with the desired values or logic. Support for script inputs may be
reconsidered after the core control experience is proven.

This restriction applies to the Karoo invocation interface; it does not
restrict what the script itself may do inside Home Assistant.

### P2-D02: Scripts are ordinary direct-action controls

Tapping a script control invokes it as an ordinary action, following the
interaction precedent of Home Assistant's CarPlay and Apple Watch companions.
Scripts do not receive a user-visible sensitive classification or the elevated
confirmation policy reserved for direct sensitive controls.

The app does not inspect or infer what a script does. A script that unlocks a
door, opens a garage, disables an alarm, or invokes another sensitive operation
therefore remains an ordinary script control. Users who want the app's
lock-specific state, confirmation, and security behavior must configure the
lock entity as a direct lock control instead of invoking it through a script.

This is an accepted product boundary and residual risk, not an assertion that
all scripts are harmless. Stage 3 must include it in the threat model without
silently reclassifying scripts as sensitive.

### P2-D03: Suppress duplicate taps while sending

After a script tile is tapped, it temporarily rejects further taps while the
request is unresolved. It becomes actionable again when Home Assistant
acknowledges the request, the request fails, or a short timeout expires.

This is input de-duplication, not a confirmation step and not a wait for the
script itself to finish. Once the request is resolved, Home Assistant's script
mode remains responsible for whether later invocations may overlap.

### P2-D04: Report acknowledgment, not completion

A successful Home Assistant call response proves that the invocation request
was accepted; it does not prove that every action inside the script completed
successfully.

The app therefore shows a brief, neutral acknowledgment rather than claiming
completion or displaying a persistent success state. `Started` is the preferred
MVP label. `Sent` or `Triggered` may be selected during Stage 5 UX testing if
one is materially clearer, but `Completed` must not be used for an invocation
acknowledgment.

### P2-D05: Discover scripts and reuse Home Assistant metadata

During setup, the app retrieves supported script entities from Home Assistant
and lets the user select and order a curated subset. Entity selection is not a
free-text `entity_id` field, and unselected scripts do not appear during normal
operation.

Each selected control is persistently referenced by its Home Assistant
`entity_id`. Its user-facing name and icon come from current Home Assistant
metadata, with an app-provided fallback when either is absent. The MVP does not
provide Karoo-only name or icon overrides; users customize those centrally in
Home Assistant.

### P2-D06: One mixed Quick Access list

Scripts, locks, scenes, buttons, lights, and other selected controls appear in
one user-ordered Quick Access list. Normal operation does not split controls
into domain tabs, screens, or folders. Domain filters may be used during setup
to help find controls.

Although presentation is unified, each control retains its domain-specific
state and explicit operations. The mixed list must not reduce locks, covers, or
other stateful controls to generic toggle behavior.

### P2-D07: Never queue or automatically retry actions

If Home Assistant is unreachable or a request times out, the app does not store
the action for later delivery and does not automatically retry it. Connectivity
recovery never causes an earlier user action to be sent. The user must
deliberately tap again.

This applies even when the app believes the first request was not sent. If
delivery or acknowledgment is ambiguous, the UI reports uncertainty rather
than retrying. Read-only state refreshes may retry independently because they
cannot actuate a device.

### P2-D08: Optional per-lock Open/unlatch capability

Home Assistant distinguishes `unlock` from `open`. Unlocking releases the lock
but may leave a separate latch engaged; opening releases the latch so the door
can be pushed open without turning the handle. Only some lock entities support
the open operation.

Direct lock controls expose `Lock` and `Unlock` by default. If a selected lock
reports support for `Open`, its Karoo app setup screen also shows a per-lock
**Show Open/unlatch button** toggle. It is disabled by default. When disabled,
the `Open` button is absent from normal operation and the app cannot invoke the
operation for that lock.

When enabled, `Open` is a separate, explicitly named operation. It is never
substituted for `Unlock`, inferred from the lock state, or implemented as a
generic toggle. It must receive at least the same security treatment as
`Unlock`; Stage 3 will define that treatment.

If Home Assistant does not report the capability, or it later disappears, the
app must not offer or invoke `Open`. The MVP does not define a `Close` lock
operation because that is not part of Home Assistant's lock domain.

### P2-D09: Every direct lock operation is sensitive

`Lock`, `Unlock`, and the optional `Open` operation are all classified as
sensitive in the MVP. Invoking any of them enters the sensitive-action flow;
none behaves like an ordinary direct-action script.

This keeps the lock control predictable and protects against accidental
lockouts or bolt movement. Stage 3 will define the shared security policy and
may consider stronger treatment for a particular operation only if justified
by the threat model.

### P2-D10: Require live, known lock state before acting

All direct lock operations remain disabled until the app has obtained a current,
known state for that lock from the active Home Assistant connection. Cached
state from an earlier session may be displayed only if unmistakably marked
stale and cannot authorize an action.

The app never offers a sensitive state-changing operation while the lock is
`unknown`, `unavailable`, or represented only by stale cached state. The exact
freshness rule and state transport belong to Stages 4–5.

### P2-D11: Transitional and jammed lock states

While a lock reports `locking`, `unlocking`, or `opening`, all operations are
disabled until a stable state arrives. The UI reflects the actual transitional
state rather than optimistically displaying the requested target state.

When a lock reports `jammed`, the app shows a prominent warning but still lets
the user deliberately retry an explicit supported operation through the
sensitive-action flow. It never treats `jammed` as successful.

### P2-D12: Verify the resulting lock state

After Home Assistant acknowledges a `Lock`, `Unlock`, or `Open` request, the app
continues showing the operation as pending until it observes the corresponding
stable state: `locked`, `unlocked`, or `open`.

Only the observed target state is reported as success. A rejection, `jammed`
state, conflicting stable state, disconnection, or timeout is reported
distinctly, with no automatic retry. Exact timeouts and feedback presentation
belong to Stage 5.

### P2-D13: Exclude caller-supplied lock codes from the MVP

Home Assistant's `lock.unlock` and `lock.open` actions accept an optional
device/integration code. Some locks require the caller to provide one, while
others need no code or have a default configured inside their integration. This
code is separate from the Karoo app PIN.

The MVP does not store, prompt for, or send a caller-supplied lock code. Direct
lock controls are supported only when Home Assistant can perform each enabled
operation without the Karoo supplying one, including integrations that manage
a default code internally.

If the app can determine during setup that a required code is unsupported, it
must explain the limitation before the lock is added. Otherwise a code-related
action rejection is reported without retry. Caller-supplied code support may be
reconsidered only after a future security review.

### P2-D14: Stateless native controls follow script behavior

`button`, `input_button`, and `scene` controls use the same ordinary
direct-action behavior as scripts:

- discover entities from Home Assistant and add them to the mixed Quick Access
  list;
- invoke the explicit native operation (`button.press`, `input_button.press`,
  or `scene.turn_on`) with no runtime parameters or scene transition setting;
- suppress duplicate taps only while the request is unresolved;
- show the neutral `Started` acknowledgment, never inferred completion;
- apply no sensitive classification or confirmation.

As with scripts, the app cannot infer downstream impact. A button may restart a
device, and a scene may affect security-sensitive entities. This is an accepted
residual risk.

### P2-D15: State-aware binary controls

`light`, `switch`, and `input_boolean` controls behave as ordinary state-aware
binary tiles:

- require a live `on` or `off` state before acting;
- display that state and send the opposite explicit operation (`turn_on` or
  `turn_off`) when tapped, never a generic `toggle` call;
- disable repeated taps and show a pending state until the requested `on` or
  `off` state is observed;
- report rejection, timeout, disconnection, or an unexpected state without
  automatic retry;
- provide no brightness, color, transition, or other parameters in the MVP;
- apply no sensitive classification or confirmation.

As with scripts, the app cannot infer whether a switch controls something
sensitive. This is an accepted residual risk.

### P2-D16: Explicit, capability-aware cover operations

The cover slice supports only each entity's advertised `Open`, `Close`, and
`Stop` capabilities, with no position or tilt controls:

- require a live, known cover state and use only explicit native operations;
- offer `Open` from `closed` and `Close` from `open`;
- while `opening` or `closing`, suppress competing movement commands and offer
  `Stop` when the entity supports it;
- verify Open and Close by observing the requested stable state;
- treat Stop as an acknowledged request because Home Assistant has no universal
  `stopped` state, then refresh the actual state;
- provide no target position, tilt, or movement percentage in this slice.

This deliberately differs from the current Home Assistant CarPlay behavior,
which invokes the generic cover toggle action. The stricter behavior makes the
requested direction deterministic, exposes a supported emergency Stop, and
verifies observable outcomes.

### P2-D17: Native cover metadata initializes sensitivity

Setup reads each cover's native Home Assistant `device_class` and supported
feature flags. It shows the reported type to the user and uses supported
features to determine whether Open, Close, and Stop can be offered. The app
does not infer type from an entity name, icon, or hardware brand.

Every cover has a **Sensitive control** toggle initialized from `device_class`:

- **Sensitive by default:** `door`, `garage`, `gate`, `window`, and generic or
  unknown cover classes.
- **Ordinary by default:** `awning`, `blind`, `curtain`, `damper`, `shade`, and
  `shutter`.

The user may change the classification per cover because device class does not
fully describe physical risk. The app does not provide a Karoo-only cover-type
override; incorrect type metadata should be corrected centrally in Home
Assistant.

For a sensitive cover, Open and Close use the sensitive-action flow. Stop
remains an immediate ordinary action because delaying a request to halt
movement can worsen physical safety. Stage 3 must address changes to risk
metadata after setup.

### P2-D18: One Home Assistant server in the MVP

One app installation connects to exactly one Home Assistant server in the MVP.
All selected controls, credentials, connection state, and security settings
belong to that server.

Adding or connecting a different server requires replacing the existing server
configuration through the recovery/setup flow. Multi-server switching and
mixed-server control lists are excluded.

### P2-D19: Missing, renamed, or changed entities fail closed

If a selected `entity_id` no longer exists, or now belongs to an unsupported
domain, the app keeps its tile disabled with a clear **Needs attention** state
until the user removes it or selects a replacement.

The app:

- never silently removes the control;
- never rebinds it using a matching name, icon, device, or newly created entity;
- refreshes friendly-name and icon changes automatically when the same
  `entity_id` remains valid;
- disables all actions for a missing or incompatible selection;
- offers explicit Remove and Replace choices in setup.

An `entity_id` rename in Home Assistant therefore requires review or
reselection unless Stage 4 identifies a supported, unambiguous rename event
that can be migrated safely.

This deliberately prefers a temporarily broken control over silently
controlling the wrong entity.

### P2-D20: Setup and management are self-contained on Karoo

Every task required to use the MVP is possible from the Karoo app itself.
This includes connecting the one Home Assistant server, authenticating,
discovering and selecting controls, ordering the Quick Access list, changing
per-control options, and repairing or removing unavailable controls.

The app may open a system browser or Home Assistant's native authorization page
as part of authentication. However, the MVP does not require:

- a separate project-specific phone or desktop companion app;
- a hosted account or relay service operated by this project;
- a custom Home Assistant integration, App, or HACS installation; or
- manually editing Home Assistant YAML.

An optional phone-assisted or QR-based setup path could be considered later,
but it could not be the only way to configure the app.

This preserves the native Home Assistant boundary and makes the downloaded
Karoo app the complete product. Stage 4 will decide the exact native
authentication flow.

## Open decisions

None.

## Acceptance gate

Stage 2 is accepted when the primary journeys and exclusions for scripts,
locks, and the later control-domain slices are resolved. Stage 3 will then
define the threat model and security posture for those accepted journeys.
