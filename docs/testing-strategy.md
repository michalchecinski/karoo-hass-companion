# Testing strategy and regression feedback loop

## Goal and current enforcement

Make a broken rider contract produce a reproducible test failure before a change
is accepted. Routine changes should carry enough automated evidence to avoid
requiring a person to inspect their implementation. Tests reduce uncertainty;
neither passing tests nor agreement between agents guarantees correctness.

This document defines the testing policy for agents and contributors now. The
rollout below is a plan, not a claim that the missing tests or CI gates exist.
It does not authorize merging, releases, dependency changes, or changes to live
GitHub rules. Existing issue-to-PR independent review requirements still apply.

As inspected on 2026-10-26 against main commit `38ddbc7`:

- `.github/workflows/_build.yml` runs `ktlintCheck check assembleDebug` for PR
  verification and `ktlintCheck check assembleRelease` for releases.
- JVM tests cover action resolution, onboarding eligibility, connection/discovery
  UI-state helpers, OAuth callback validation, settings codecs, transport selection, and repository
  requests/outcomes, including mock HTTP server cases.
- Instrumentation tests cover selected Quick Access components, PIN keyboard
  behavior, and sound settings. The checked-in CI does not run
  `connectedDebugAndroidTest`.
- [PR #34](https://github.com/michalchecinski/karoo-hass-companion/pull/34) adds
  HTTPS-origin validation, WebView failure handling, and OAuth attempt replacement,
  concurrent consumption, and shortcut interruption checks. Those checks are not
  yet part of the inspected main revision.
- Python tests exercise the issue journal helper, but are not run by checked-in CI.
- `SettingsCodecTest` covers legacy actions and selected saved fields. Direct
  `PinStore` lockout-persistence and Android DataStore recreation coverage remain
  missing. `QuickAccessExecutorTest` exercises executor request sequences, but
  there are no tests driving the full `MainViewModel` action orchestration.
  Testing a UI-state helper or an action resolver does not cover that wiring.
- CI path filters omit `scripts/**` and agent/workflow instructions. Live branch
  protection settings and current remote check results were not inspected.

## Required work for each behavior change

1. Before implementation, write observable acceptance criteria and affected
   existing invariants. Derive expectations from the issue and product contract,
   not by copying the implementation. Resolve conflicting requirements explicitly.
2. Map each changed criterion to a named executable test and its layer. For each
   relevant risk below, identify the test or explain why the risk is inapplicable.
   Keep this mapping in the PR or private run evidence; durable product contracts
   belong in feature documentation. A test may establish several criteria.
3. For a bug, reproduce the defect with a focused failing regression before the
   fix whenever feasible. For a feature, add a failing behavior test first where
   the existing interfaces allow it. An absent API or a compile failure is not
   evidence that a behavioral assertion detects a defect.
4. Implement the behavior and run the focused checks. For changed consequential
   action, authentication, PIN, transport, persistence, or concurrent/lifecycle
   logic, challenge the tests with at least one plausible relevant broken
   implementation, in addition to the normal passing implementation. Use the
   original bug as this challenge when its failing regression covers that risk.
5. Run the repository gate and applicable instrumentation tests against the final
   candidate. Record commands, outcomes, test identities/counts, environment,
   revision/source identity, and any excluded cases. Preserve useful failure
   reports. Subsequent changes invalidate affected evidence.
6. A required test that is absent, disabled, undiscovered, inconclusive, or unable
   to run is a verification gap. Report it; do not substitute a screenshot or a
   successful compilation for executed behavior checks.

Do not require every testing layer for every feature. Use the smallest reliable
layer that proves each contract, plus integration checks for the boundaries
actually changed. Documentation-only, build-metadata-only, and visual-asset-only
changes need appropriate validation rather than artificial application tests.

## Choose tests by the failure they must detect

| Changed behavior | Required evidence when applicable |
| --- | --- |
| Pure rules, parsers, action capabilities or input validation | JVM tests for normal cases, boundaries, invalid/missing data, and forbidden results. Use tables or deterministic generated cases for combinations. |
| API requests, token refresh, outcome classification or routing | Repository/transport integration tests that inspect actual request method, destination, payload, counts, and outcomes. Exercise rejection, malformed/truncated responses, disconnects and timeouts; distinguish failures before send from uncertain results after send. |
| Confirmation, PIN gating, busy state, reconnect or cancellation | Tests through the real action coordinator with controlled collaborators, including ordered events and requests that must never occur. Testing only a derived boolean or fake coordinator is insufficient. |
| Navigation, interaction, lifecycle or screen state | Compose/instrumentation tests that perform the interaction and assert its visible result and downstream effect. Include cancel/back and the relevant background/recreation transition. Screenshots cover appearance separately. |
| Saved actions, settings migration or security storage | Round-trip and compatibility tests using synthetic persisted fixtures; relevant restart, missing/corrupt input, and cleanup tests. Use Android instrumentation for DataStore/Keystore behavior that a JVM fake cannot establish. |
| Android/Karoo integration | Exercise the app-side adapter and Android behavior in instrumentation. Record what the SDK fake models. Research official platform behavior and require a targeted physical experiment if a material uncertainty remains. |
| Build/release/workflow logic | Tests or isolated dry runs of success and rejection paths, artifact/configuration validation, and retained failure diagnostics. Never publish or invoke real home controls to test a gate. |

For asynchronous behavior, test meaningful orderings: response before cancellation,
response after cancellation, repeated input while a request is suspended, and old
work finishing after a new session begins. Use controllable clocks, dispatchers,
deferred responses and bounded synchronization where appropriate. Avoid arbitrary
sleeps and real network accounts. A time limit is a failure diagnostic, not an
assertion of the expected result.

Keep real production logic inside the boundary under test. Replace external
transport, time and platform services with recording/controlled collaborators;
do not replace the behavior whose correctness is being asserted. Prefer explicit
constructor parameters or a small interface where a real testability obstacle
exists. Do not introduce a dependency injection framework or restructure the
whole app just to test one change. Dependency additions/upgrades require the
repository's explicit authorization.

## Critical contracts to preserve

These IDs are a starting catalogue, not a claim of implemented coverage or a
replacement for feature-specific acceptance criteria. Tests must assert both
the expected outcome and relevant forbidden effects.

| ID | Contract and useful counterexample | Existing evidence and next gap |
| --- | --- | --- |
| ACTION-1 | A deliberate authorized invocation sends one service command; rapid taps while busy do not create another. Refresh, reconnect and outcome checking never replay that command. | Repository tests cover no replay after 401; add coordinator event sequences and total POST counts. |
| ACTION-2 | Unlock and Open require confirmation; cancel sends zero service commands. Selected-action PIN failures/cancellation cannot execute a pending command. | `ActionSemanticsTest` covers required-confirmation flags; add confirmation/PIN/coordinator and UI cancellation checks. |
| ACTION-3 | Failed mandatory state refresh, unavailable state and unsupported capabilities prevent state-aware commands. Stateless controls do not gain unnecessary pre-action refreshes. | Resolver/repository cases exist; test the real refresh-to-authorize-to-send sequence. |
| ACTION-4 | Request acceptance is not physical completion. Lost action responses remain uncertain; scripts/buttons/scenes/Stop do not acquire a false Completed result. | Repository and tile checks cover parts; test coordinator polling and terminal outcomes. |
| SESSION-1 | Backgrounding invalidates pending confirmation/authorization and relocks whole-app protection. A late response cannot initiate a canceled command. Already-sent commands are not assumed undone. | `foregroundChanged` and `actionIntentGeneration` need coordinator/lifecycle sequence tests. |
| SESSION-2 | Account reset clears account data; old-session responses cannot restore credentials or repopulate actionable state. | PR #34 adds stale OAuth exchange checks; add reset with suspended state/action/refresh work. |
| PIN-1 | Wrong PIN attempts lock out at the intended threshold; lockout survives store recreation, expires at its boundary, and successful verification resets counters. Disabling protection requires the current PIN. | No direct `PinStore` suite; use synthetic preferences and explicit `verify(now = ...)` times, plus coordinator checks. |
| AUTH-1 | Invalid state/callbacks never exchange codes; each accepted code is consumed once; retry/replacement prevents stale credentials. TLS failures never gain a bypass. | `OAuthCallbackUriTest` covers callbacks on main; PR #34 adds `OAuthAttemptTest` and `OAuthWebViewTest`. Retain and extend for changed paths. |
| NETWORK-1 | Wi-Fi-only requests never enter Companion; discovery uses direct Wi-Fi. Recovery may retry safe reads but not service commands. | `PolicyTransportTest` proves router selection for a request; add coordinator discovery/action routing and fault cases. Actual Karoo routing needs platform evidence. |
| DATA-1 | Restart/upgrade preserves action identity, order, protections and explicit security choices. Legacy installations follow documented onboarding compatibility. | `SettingsCodecTest` covers selected legacy/round-trip cases; add broader identity, order and protection fixtures plus Android DataStore recreation. Corrupt-input recovery policy must be explicit before tests prescribe it. |
| UI-1 | Visible disabled/loading/error states match whether an action can execute; navigation/cancellation and long-name controls work at the Karoo viewport. | Existing Compose tests render selected components; add user-flow checks wired to the real coordinator. |

## Prove the tests can detect a regression

A passing test can execute the faulty code without checking its consequence.
For high-consequence changes, record a small counterexample challenge:

- Temporarily bypass confirmation: the cancellation/authorization test must fail
  because it observes an unexpected service request, not because compilation fails.
- Temporarily replay an action after 401: the test must fail on the POST count.
- Temporarily remove pending-intent invalidation: a controlled late response after
  backgrounding must cause the lifecycle test to fail.
- Temporarily move a PIN/input boundary: the corresponding boundary case must fail.

Apply only the challenge relevant to the changed risk. Work in an isolated
temporary checkout/patch and never publish the broken variant. Preserve the
command, minimal challenge description and failing assertion; restore the correct
implementation and rerun affected tests. A build failure, test-runner crash or
unrelated timeout does not demonstrate that an assertion detected the defect.

Table-driven and seeded generated tests can cover state/capability/event
combinations using the current JUnit stack. Use independent expected results or
an explicit reference model; never compute the expected answer using the same
production resolver being tested. Log a reproducible seed and event sequence on
failure. Random generation supplements named regressions; it does not replace them.

Automated mutation tooling can later scale these challenges after a compatibility
experiment and explicit dependency approval. No such tool is configured now.
Inspect survivors for missing assertions, equivalent behavior or irrelevant code;
do not chase a mutation score by adding implementation-coupled assertions.

## Feedback and repair loop

Contract -> focused failing check -> implementation -> passing check -> relevant
counterexample challenge -> full candidate verification -> independent review.

When a check fails, retain the first failing command, assertion, request/event
trace and source identity. Diagnose whether the defect is in production behavior,
the test, or infrastructure; repair it and rerun affected checks and the final gate.
Keep traces synthetic and free of credentials/private addresses.

Never make a result green by deleting a regression, weakening an assertion,
skipping a required test, hiding the first failure with retries, or updating a
screenshot solely to match an unexplained change. An intentional requirement
change must update the contract and explain why the old expectation changed.
An incorrect test needs evidence for its correction. Flaky tests remain an
unresolved verification gap until their nondeterminism is diagnosed and repaired.
Follow the issue workflow's bounded review/repair rounds; do not loop indefinitely.

If a defect escapes, add the smallest reproducible regression and identify why
the prior suite missed it: untested contract, missing boundary, weak assertion,
incorrect fake, untested integration, or check not executed. Repair that specific
gap in the policy or CI when warranted. Another prompt alone is not the fix.

## Commands and evidence

Run the existing application gate:

```sh
./gradlew ktlintCheck check assembleDebug
```

For affected Android/UI/platform behavior, select an isolated compatible Karoo
emulator, then run instrumentation explicitly:

```sh
./gradlew :app:connectedDebugAndroidTest
```

For changes to the issue helper:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

The issue workflow has emulator preflight instructions. Confirm actual boot,
install and test execution; preflight alone is not test evidence. Inspect the
generated reports under `app/build/reports/tests/`, `app/build/test-results/`
and `app/build/outputs/androidTest-results/` as applicable. Require nonzero
discovered tests for expected suites and distinguish failed, skipped and passed.

Use this evidence shape in a PR/run record; it is documentation, not a new
machine-enforced schema:

| Criterion/invariant | Test class and case | Layer/environment | Command and result | Tested revision/source | Regression or challenge result |
| --- | --- | --- | --- | --- | --- |
| Feature-specific contract or catalogue ID | Actual existing test identity | JVM, mock HTTP, emulator, or physical | Exact command, counts and report | Candidate SHA or recorded matching source identity | Failed on original defect/relevant broken variant, then passed; or justified inapplicability |

## Rollout with completion criteria

1. **Execute existing checks in CI.** Add Python workflow tests and a separate
   emulator instrumentation job. Include relevant script/configuration paths in
   CI triggers. Prove runner acceleration, system-image/ABI compatibility, Karoo
   viewport, boot, installation, nonzero discovery and actual test execution.
   Retain test reports, failure logs and APKs as appropriate. Completion: a known
   failing assertion in either suite produces a failed candidate check and useful
   diagnostics; missing infrastructure cannot be reported as passed.
2. **Protect the real action sequence.** Add narrowly scoped test seams for the
   coordinator's transport/stores/time where needed. Cover ACTION-1 through
   ACTION-4 and SESSION-1/2 through real orchestration, with UI checks for the
   confirmation/PIN connection. Add PIN-1 persistence and DATA-1 upgrade fixtures.
   Completion: catalogue requirements have named passing tests and the relevant
   deliberate counterexamples produce assertion failures. Resolve any exposed
   product ambiguity before setting an expected result.
3. **Enforce candidate evidence.** Require CI results for the final candidate and
   integration with the current base, nonempty expected suites, and independent
   review covering the final diff. Implement validation of evidence and explicit
   blocking states rather than trusting journal text or PR checkboxes. Keep
   trusted gate configuration outside an implementation agent's ability to waive
   its own checks. Live repository-rule changes need separate authorization.
   Completion: dry runs reject stale evidence, omitted suites, altered/bypassed
   checks and unresolved material findings.
4. **Evaluate before unattended approval.** Replay representative past bugs and
   deliberately broken changes in isolation. Add targeted generated event
   sequences; evaluate mutation automation separately. Track missed defects,
   flaky failures and gate duration, and broaden the permitted unattended scope
   only after evidence supports it. All selected critical counterexamples must
   be detected; a percentage of line coverage is not an acceptance substitute.

The initial operational target is a named executed check for every changed
behavioral criterion, no unexplained required skips, and detection of every
selected critical counterexample. There is no supported claim that the whole
existing application already meets that target.

## Limits and references

Mocks establish only their modeled contract. Emulator tests cannot prove physical
door movement, Karoo Companion delivery, hardware trust/storage properties, or
every OS/service version. Preserve the repository policy for targeted experiments
when material uncertainty remains. Product ambiguity requires a decision rather
than tests that silently invent requirements.

- [Android testing fundamentals](https://developer.android.com/training/testing/fundamentals)
  explains local/instrumented scopes and replacing dependencies at test boundaries.
- [Mutation testing concepts](https://pitest.org/quickstart/basic_concepts/)
  explains deliberately altered behavior, surviving mutations and equivalent cases;
  this is background, not a recommendation to install PIT in this Android build.
- [Issue-to-PR workflow](issue-to-pr-workflow.md) defines review and publication.
- [Current implementation](current-implementation.md) defines existing behavior;
  feature documents may also describe planned work and must not be treated as
  proof that those features are already implemented.
