# Run evidence and recovery

The coordinator owns gate decisions and verification. `scripts/issue_workflow.py`
is a local prerequisite checker and journal, not an unattended executor or a
security boundary. Run it from the repository root. Its schema/output is the
source of truth for helper fields; store larger evidence as referenced files.

```sh
python3 scripts/issue_workflow.py preflight
python3 scripts/issue_workflow.py preflight --require-emulator
python3 scripts/issue_workflow.py init https://github.com/OWNER/REPO/issues/NUMBER --base origin/main
python3 scripts/issue_workflow.py show RUN_DIR
python3 scripts/issue_workflow.py record RUN_DIR --stage investigation --status passed --evidence 'Reproduction and source references: investigation.md'
```

`init` requires the actual issue URL and an existing base ref; it records local
repository/issue/base identity and rejects an existing run. It does not contact
GitHub or promise the recorded issue is current. Use `--runs-dir PATH` to select
run storage when necessary. Do not put secrets or private home data in the journal.

Keep the following evidence in run storage, outside the published app change:

| Evidence | Contents |
| --- | --- |
| Identity | Issue URL/revision, repository, base ref/SHA, worktree, branch, run identity |
| Intake | Sanitized issue/comments snapshot, existing PR search, classification rationale |
| Investigation | Current/expected behavior, reproduction or specification, source citations, feasibility conclusion |
| Plan | Acceptance criteria, implementation/check mapping, independent plan review when substantial |
| Verification | Exact commands/results, logs/screenshots, source SHA/tree, environment and limitations |
| Review | Independent reviewer findings, dispositions, round counts, reviewed diff identity |
| Publication | PR URL/head SHA, label, required CI state, final outcome and remaining decisions |

Journal stages are intake, investigation, plan, implementation,
verification, review, or publication. Record a gate decision when its evidence is
ready and a blocker when discovered; do not mark stages complete merely because a
file exists. Preserve review counts and unresolved questions across interruptions.

## Resume and duplicate prevention

Before any external mutation, read the journal and reconcile it with actual Git
and GitHub state. Locate the existing branch/worktree and PR by exact repository,
issue, branch, and stored URL. A failed publication call may have succeeded
remotely: query before retrying creation. Never create a second PR to recover an
unknown result. Verify the stored base SHA and actual head/diff, uncommitted work,
issue changes, and last successful check/review SHA. Reopen only invalidated gates.

If another active writer owns the issue/run, stop with an infrastructure blocker;
do not rely on a journal file alone as a distributed lock. The first version is
single-coordinator local execution. A future scheduler must provide mutual
exclusion, credential isolation, invocation and retry limits, and durable storage.
Do not claim these are implemented by the helper.

Each review has a bounded three-round budget. For an identical failed tool/check
operation, allow the initial attempt plus at most two evidence-driven retries;
change the approach only when new evidence justifies it. Persistent unavailable
credentials, network, emulator, or tool capability should produce a blocker.
GitHub checks may be polled with backoff, but an execution time limit ends with a
truthful pending/draft outcome. Preserve state for a later explicit resumption.

## Terminal outcomes

- `ready_for_review`: PR published, acceptance criteria verified, material findings
  resolved, required checks passing for the final head.
- `draft_with_limits`: useful PR published, with explicit unmet checks or pending
  verification; do not represent it as ready.
- `needs_decision`: precise unresolved product judgment or material feasibility
  uncertainty, research completed, recommendation and next experiment recorded.
- `cannot_reproduce`: attempted reproductions and missing evidence recorded; no
  speculative fix published as proven.
- `blocked_environment`: specific unavailable infrastructure/access and recovery
  step recorded; preserve useful work and any existing PR as draft.
- `already_addressed`: exact existing implementation/PR/duplicate and supporting
  acceptance evidence recorded. Do not close or relabel the issue automatically.

These are semantic outcomes for the coordinator's report. For helper events map
`ready_for_review` to `ready_pr`, `draft_with_limits` to `draft_pr`, and
`already_addressed` to `already_done`; the other outcomes use matching statuses.
Use `inconclusive` for an unresolved investigation checkpoint. The helper does not
validate that an outcome is deserved.
An external runner may later require a structured final result based on this
contract. No merge or release belongs to this workflow.
