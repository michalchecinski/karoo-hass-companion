## Summary

<!-- Describe the rider-facing or development outcome. PR titles appear in public release notes. -->

## Screenshots

<!-- For UI changes, add Karoo screenshots or explain why they are not applicable. -->

## Verification

<!-- List automated checks, their tested commit, and environments used. State when physical Karoo/Home Assistant testing was not performed. Link feasibility evidence for platform-dependent behavior. -->

<!-- For behavior changes, map acceptance criteria/critical invariants to named tests using docs/testing-strategy.md. Include relevant failing-before/passing-after or counterexample evidence, actual instrumentation execution, and any gaps. Nonbehavioral changes may explain inapplicability. -->

## Checklist

- [ ] The PR has `version:*` label.
- [ ] The title is suitable for a non-technical public changelog, or `skip-changelog` is applied.
- [ ] `./gradlew ktlintCheck check assembleDebug` passes.
- [ ] Relevant automated behavior checks pass; material feasibility uncertainties are resolved, and any physical testing performed or omitted is stated.
- [ ] Changed behavioral criteria and critical invariants have named test evidence, including applicable regression/counterexample checks, or a justified nonbehavioral exception.
- [ ] UI changes include screenshots where applicable.
- [ ] Documentation and release notes were updated where applicable.
- [ ] No credentials, tokens, private URLs, or signing material are included.
