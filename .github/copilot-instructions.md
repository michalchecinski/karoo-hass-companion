# Code review instructions

Review pull requests for concrete defects, regressions, security problems, and
missing tests. Comment only when the changed code has a clear, actionable
problem. Do not leave praise, style-only comments, speculative concerns, or
requests to change dependencies unless the pull request explicitly changes
them.

## Product boundaries

- This is a focused Home Assistant Quick Access companion for a Hammerhead
  Karoo, not a general Home Assistant dashboard or arbitrary service-call
  client. Flag changes that expand those boundaries without an explicit
  product decision.
- Preserve support for scripts, locks, covers, lights, and switches. Do not
  silently substitute a missing or changed Home Assistant entity with another
  entity.
- Changes to current behaviour must agree with `docs/current-implementation.md`.
  Treat it as authoritative over the older MVP plan.

## Security and action safety

- Treat access tokens, refresh tokens, OAuth authorization codes, PINs, PIN
  verifiers, keystore material, and signing secrets as sensitive. Flag code
  that logs, exposes, persists insecurely, backs up, or transmits them to an
  unintended destination.
- OAuth flows must retain state-nonce validation and must not weaken the HTTPS,
  system-trusted-certificate, or encrypted Android Keystore storage policy.
- Home Assistant actions must result from an explicit user action. Flag delayed
  delivery, automatic action replay after token refresh or a network failure,
  and automatic retries of service POST requests.
- Unlock and Open actions must remain confirmation-protected. Do not treat a
  request as physical completion unless the implementation can verify the
  expected state; scripts and cover Stop are intentionally only requested.

## Connectivity and Home Assistant behaviour

- Keep Wi-Fi-only traffic bound to direct Wi-Fi. Companion fallback is allowed
  only when the user selected it; entity discovery and management require
  direct Wi-Fi because the full states response can exceed the companion size
  limit.
- Review error handling carefully: unavailable, unknown, missing, transitional,
  and unsupported entities must not trigger an unsafe default action or be
  shown as successfully completed.
- Preserve the distinction between definite failure and an uncertain outcome
  after a request may have reached Home Assistant.

## Android and Karoo UI

- Flag main-thread blocking for network, cryptographic, token, or persistent
  storage work.
- The UI targets Karoo's small portrait display and must remain glanceable,
  touch-friendly, and accessible without relying on colour alone. Preserve the
  established navigation behaviour and avoid `Material ListItem` in the entity
  chooser because it causes a Karoo Compose measurement crash.
- Keep user-visible strings in Android string resources; do not introduce
  hard-coded UI text in Kotlin composables.

## Tests and review comments

- Require focused unit tests when changed logic affects authentication, secure
  storage, PIN lockouts, URL normalization, entity/action mapping, transport
  routing, stale-state gating, or outcome classification. Require tests for a
  bug fix when practical.
- Do not demand tests for documentation-only, build-metadata-only, or purely
  visual asset changes unless they alter runtime behaviour.
- For each finding, explain the triggering scenario and user impact. Reference
  the smallest relevant changed line range and propose a fix when it is clear.

## Evidence threshold

- Report a finding only when it is directly supported by the changed code and
  relevant repository context. If you cannot identify a concrete execution path
  that produces the problem, do not comment.
- Do not infer defects from redacted, masked, generated, or truncated values in
  the review UI. Review the source and tests as written.
- Do not comment merely because a different implementation might be preferable;
  require a correctness, safety, security, user-impact, or maintainability
  failure.

## Finding quality

- Do not restate an existing test, implementation, or documented invariant as a
  concern unless the change demonstrably violates it.
- When uncertain whether a concern is real, omit it rather than presenting it
  as a defect.
