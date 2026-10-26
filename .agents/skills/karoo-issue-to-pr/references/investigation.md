# Investigation routes

## Bug

Record expected behavior and its source, observed behavior, affected versions,
environment, minimal reproduction, and sanitized evidence. Inspect the current
base and linked history before choosing a fix. Separate a reproducible failure
from a plausible hypothesis.

Use an isolated test or emulator scenario to reproduce the defect. Prefer a
regression test that demonstrably fails on the unfixed base for the reported
reason and passes after the fix. Record both commands and results; a compile or
fixture failure is not successful reproduction. If adding the test to the base
requires adaptation, explain the adaptation. Do not rewrite the user's checkout
to run this comparison; use the isolated worktree or an additional temporary one.

For timing, lifecycle, or visual defects without a useful deterministic test,
record repeatable steps and before/after logs or screenshots plus the closest
meaningful automated coverage. Explain why that evidence is sufficient. If no
credible reproduction or causal evidence exists after investigation, conclude
`cannot_reproduce` with attempted scenarios and missing evidence. Do not implement
a speculative fix just to advance the chain.

Investigate the actual cause and adjacent callers so the fix addresses the
trigger without changing unrelated behavior. Confirm that the alleged bug is not
an intentional product boundary or unsupported environment. If it is, explain the
evidence; route any requested behavior change through improvement/feature planning.

## Improvement

Inspect the current feature's implementation, docs, callers, persisted data, and
tests. Describe the before/after behavior with a concrete rider interaction.
Identify what must be preserved: configured actions/order, PIN and confirmation
choices, connectivity policy, state semantics, and migration compatibility where
relevant. Define acceptance checks for both changed and preserved behavior.

Do not treat every inconsistency as permission to redesign the feature. Ask for a
decision only when the requested change conflicts materially with the documented
contract or leaves multiple materially different product outcomes.

## Feature

Trace the requested rider problem to the focused Quick Access product boundaries
in `docs/app-idea.md`. Establish supported entity/API capabilities, minimal UI
states, authentication/transport implications, failure and uncertain outcomes,
storage needs, and acceptance criteria. Avoid arbitrary service-call interfaces
or a general HA dashboard unless the user explicitly changes product scope.

Research actual SDK/API support before designing an integration. Use official
Karoo SDK samples and HA API documentation, and inspect existing project patterns.
The `awesome-karoo` list can locate examples; verify behavior against official
sources and code rather than copying an example as proof of support. Clone any
external source repository under `/Users/michal/dev/<repo-name>` per project rules.

For all routes, distinguish a supported approach from unverified physical
behavior. A mock may prove the app sends the expected request and handles replies;
it does not prove that a lock moved, a script completed, or Companion transport
behaves identically on hardware.
