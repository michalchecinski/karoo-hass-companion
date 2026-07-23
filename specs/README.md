# Specification workflow

## Purpose

These specifications are the source of truth for product behavior. Code,
tests, UX copy, and release notes must trace back to an accepted requirement or
decision.

Specification is interactive. The project discusses one bounded stage at a
time, records the outcome, and waits for explicit product-owner acceptance
before starting the next stage. Later-stage drafts cannot retroactively settle
an earlier open decision.

## Interactive stages

| Stage | Question answered | Gate |
| --- | --- | --- |
| 0. Platform foundation | What does `karoo-ext` provide and constrain? | Technical foundation accepted |
| 1. Problem and motivation | Whose problem are we solving and why? | Product intent accepted |
| 2. Use cases and scope | Which user journeys and features belong in v1? | Scope accepted |
| 3. Security posture | Which attackers and residual risks are acceptable? | Threat model accepted |
| 4. System architecture | How do Karoo and native Home Assistant capabilities interact? | Architecture/ADRs accepted |
| 5. UX flows | How do setup, unlock, confirmation, status, and recovery work? | UX behavior accepted |
| 6. Testable specification | What are the normative requirements and acceptance scenarios? | Specification baseline accepted |
| 7. Delivery plan | In what vertical slices will we implement and verify it? | Backlog accepted |

At each gate, the product owner may accept, amend, defer, or reject the stage.
Only accepted statements become binding inputs to later stages.

## Status vocabulary

- **Proposed**: drafted for discussion and safe to change.
- **Accepted**: approved as the implementation contract.
- **Implemented**: corresponding code exists.
- **Verified**: automated or documented manual acceptance evidence exists.
- **Superseded**: replaced by a newer recorded decision.

## Change process

1. Describe the user problem or risk.
2. Add or amend requirements with stable IDs.
3. Record material trade-offs in an architecture decision record (ADR).
4. Define observable acceptance scenarios.
5. Obtain product-owner acceptance.
6. Implement the smallest accepted slice.
7. Link tests and verification evidence back to requirement IDs.

A code change that alters observable behavior or a security boundary must
change the relevant specification first.

## Requirement language

The terms **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative.

Requirement IDs use these prefixes:

- `PROD`: product behavior or scope
- `ONB`: onboarding and Home Assistant connection
- `ACT`: action configuration and execution
- `NET`: connectivity
- `UX`: Karoo user experience
- `SEC`: security and privacy
- `REC`: recovery and revocation
- `OPS`: diagnostics and maintainability
- `COMP`: platform compatibility

## Current specification set

| Document | Status | Purpose |
| --- | --- | --- |
| [Stage 0: Platform foundation](stages/00-platform-foundation.md) | Accepted | `karoo-ext` facts and foundation decisions |
| [Stage 1: Product direction](stages/01-product-direction.md) | Accepted | Controls-only product and delivery order |
| [Stage 2: Use cases and scope](stages/02-use-cases-and-scope.md) | Accepted | User journeys and feature boundaries |
| [Stage 3: Security posture](stages/03-security-posture.md) | Accepted; connectivity revised 2026-07-23 | Threat model, safeguards, and residual risk |
| [Stage 4: System architecture](stages/04-system-architecture.md) | Accepted on 2026-07-23 | Architecture boundaries and transport decisions |
| [Product definition](product.md) | Seed draft; unreviewed | Input material for Stages 1–2 |
| [Security model](security.md) | Seed draft; unreviewed | Input material for Stage 3 |
| [MVP requirements](mvp.md) | Seed draft; unreviewed | Input material for Stage 6 |
| [ADR-0001](decisions/0001-native-home-assistant-api.md) | Accepted on 2026-07-23 | Native Home Assistant authentication and API boundary |

## Review gate

The specification baseline is ready for implementation planning only when:

- the product owner has resolved every **blocking** open decision;
- the threat model covers the intended front-door use case;
- every MVP feature has at least one acceptance scenario;
- security claims distinguish app safeguards from server-enforced boundaries;
- unsupported Home Assistant internal-state modifications are excluded.
