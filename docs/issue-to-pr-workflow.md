# Local issue-to-PR workflow

The repository skill `karoo-issue-to-pr` takes one explicitly selected GitHub
issue through investigation, planning, implementation, independent review,
verification, and PR creation. The first version runs in Codex on macOS. It
does not poll for issues, merge PRs, or publish releases.

## Start a run

In a Codex chat attached to this repository, supply the actual issue URL:

```text
Use $karoo-issue-to-pr to implement https://github.com/michalchecinski/karoo-hass-companion/issues/NUMBER through a PR.
```

Replace `NUMBER` with an existing issue number. This invocation authorizes the
issue's implementation, evidence-based fixes to review findings, commits,
pushes, and PR creation within the agreed issue scope. Product decisions,
dependency upgrades, merge, and release are outside that authorization.
The skill is at [SKILL.md](../.agents/skills/karoo-issue-to-pr/SKILL.md).

The coordinator reads the issue and discussion, relevant docs, source and tests,
checks existing branches/PRs, and works in an isolated checkout. It preserves
unrelated local edits. Feature, improvement, and bug routes share the delivery
stages but have different investigation requirements. Labels are hints; the
classification must follow the expected and existing behavior.

An existing issue plan is reused only after checking acceptance criteria,
current-code assumptions, scope, and feasibility. Substantial plans receive an
independent review before implementation. Every implementation receives a fresh
independent review. Reviewers inspect the actual issue and code rather than
being asked to endorse the implementer's explanation.

## Feasibility and verification

Before implementation, resolve material uncertainties using current official
Karoo/Home Assistant API documentation, source inspection, and small technical
experiments where necessary. Physical Karoo and Home Assistant testing is
optional unless it is needed to resolve a material feasibility or correctness
uncertainty. If research remains inconclusive, stop with the evidence and the
specific missing experiment or human decision. Do not guess a platform API.

This is the agreed policy for automated issue delivery, also recorded in
`AGENTS.md` and the PR template. `CONTRIBUTING.md` points contributors to the same evidence and testing policy.

Use unit tests, existing mock-server tests, and emulator instrumentation checks
as appropriate. UI work requires the Karoo emulator and screenshots of the
changed screens using `karoo-screenshots`. A screenshot establishes appearance,
not interaction correctness or Karoo service behavior. Mocks establish only
the behavior represented by their fixtures. State explicitly when no physical
Karoo or Home Assistant installation was used.

Apply [the testing strategy](testing-strategy.md) to every behavior change. Map
acceptance criteria and affected critical invariants to named tests, exercise the
real coordinator when component wiring changes, and retain relevant regression
or counterexample evidence. A test that only checks a resolver flag does not
establish confirmation/PIN/request ordering. The strategy distinguishes active
agent requirements from CI and coverage improvements still awaiting implementation.

Run the repository gate for application changes:

```sh
./gradlew ktlintCheck check assembleDebug
```

Run relevant instrumentation tests separately when UI behavior changes. Their
results do not follow automatically from Gradle `check`. Bind verification and
review artifacts to the final code revision; later changes invalidate the
affected evidence. Never check off an unperformed test in the PR template.

## Mac prerequisites and capability check

Use JDK 17, Python 3, Git, authenticated GitHub CLI, and the Android SDK. Private
SDK-package access must be available to Gradle through the repository's normal
credential configuration. Do not store credentials in run artifacts.

For UI work, install the `Karoo` AVD profile, matching host CPU system image, ADB,
Android Emulator, and macOS `sips` used by the screenshot helper. Use a dedicated
`karoo-hass-capture-<task>` AVD and explicit serial as required by the screenshot
skill; do not install on or capture from a shared emulator. The profile display
must be 480×800; the capture helper produces a 480×656 app-only image.

```sh
python3 scripts/issue_workflow.py preflight
python3 scripts/issue_workflow.py preflight --require-emulator
```

Preflight reports JSON and returns a nonzero exit code if required capabilities
are missing. It checks acceleration and AVD availability without booting or
modifying an emulator. It does not prove SDK dependency access, repository write
permission, emulator bootability, successful app installation, or passing tests.
A restricted sandbox can block credential/network checks; confirm access in
the permitted execution environment before diagnosing missing authentication.
In Codex desktop, the separate `codex` CLI is optional.

## Evidence and resume

The coordinator creates a run journal after selecting and checking the base ref:

```sh
python3 scripts/issue_workflow.py init ISSUE_URL --base BASE_REF
python3 scripts/issue_workflow.py show RUN_DIR
python3 scripts/issue_workflow.py record RUN_DIR --stage investigation --status passed --evidence 'investigation.md: reproduction and root cause'
```

Use real values for the uppercase placeholders. Default records live under
`issue-workflow/` in the repository's common Git directory, shared across
worktrees and excluded from normal commits. `--runs-dir PATH` allows an explicit
alternative location when needed. Treat that directory as private working data;
sanitize logs and issue snapshots. Keep snapshots, plans, review findings, test
summaries, and PR identity there. Only durable product documentation belongs in
the PR.

The helper validates issue identity against `origin`, records the base SHA,
refuses duplicate initialization, atomically writes state, and serializes journal
updates. It records agent-reported checkpoints; it does **not** enforce stage
transitions, verify evidence, own an execution lease, or prevent two agents from
resuming the same run. Run only one coordinator per issue in this first version.

To resume, give Codex the issue URL and existing run directory. It must inspect
the saved artifacts, current issue discussion, worktree, remote branch, PR and
CI before continuing. Reassess materially changed requirements. Do not delete
the journal to bypass a blocked outcome or create a second PR.

The outcome is one of: ready PR, draft PR with explicit gaps, needs decision,
inconclusive feasibility, cannot reproduce, environment blocker, already done,
or failed with preserved evidence. Ready means the required checks and review
are complete; optional physical testing alone does not force a draft. Bound
investigation and review loops as specified by the skill and report the stop
reason instead of silently declaring completion.

## Validate and improve the workflow

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

These tests exercise journal behavior and failure reporting, not agent judgment.
Use [the evaluation cases](../.agents/skills/karoo-issue-to-pr/references/evaluation.md)
for behavior checks before unattended use. A dry run must not write to GitHub or
implement the fixture issue. After the first real issue, compare its artifacts
against acceptance criteria and adjust instructions only for observed failures.

No live issue is selected by installing the skill. End-to-end delivery remains
unproven until a real issue has passed through the complete chain.

## External runner migration

Keep the decision process and evidence format; replace only environment setup
and execution plumbing. A Linux runner needs working KVM and `/dev/kvm` access;
a VM host must expose nested virtualization. Confirm acceleration, then actually
boot the AVD, install the APK, execute tests, and capture a screenshot before
accepting that runner. Replace the macOS `sips` screenshot cropper on Linux.

A later unattended runner also needs exclusive run leases, time and spend
limits, schema-validated results, isolated credentials, restart handling, and
idempotent GitHub publication. The current journal is not that scheduler.
