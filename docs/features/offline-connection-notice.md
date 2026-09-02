# Offline connection notice

## Goal

Make it clear when Quick Access cannot reach the configured Home Assistant
installation after the app opens, and tell the rider how to recover according
to the chosen connection policy.

## Behavior

- When authenticated Quick Access becomes visible in the foreground, the app
  performs one safe authenticated `GET /api/` request through the saved
  connection policy.
- The check does not run during onboarding, action management, or while
  whole-app PIN protection is locked. It runs after a successful whole-app PIN
  unlock.
- Quick Access actions stay disabled while that check is in progress and while
  Home Assistant is unreachable. No Home Assistant service call is retried or
  sent as part of the check.
- A failed check displays a persistent card above the configured controls. The
  card has a **Try again** button and disappears after a successful recheck.
- Wi-Fi connectivity callbacks cause another check. Companion connectivity has
  no independent Karoo SDK status, so the rider can use **Try again** after
  restoring phone or Companion connectivity.

## Policy-specific guidance

- **Wi-Fi only**, with Wi-Fi absent: **No Wi-Fi connection** — connect the
  Karoo to Wi-Fi, then try again.
- **Wi-Fi only**, with Wi-Fi present but Home Assistant unreachable: check the
  Karoo's Wi-Fi connection and Home Assistant, then try again.
- **Allow Companion fallback**: connect to Wi-Fi, or check that the phone is
  paired, Hammerhead Companion is running, and the phone has internet access.

## Scope

This notice applies only to normal Quick Access requests. Entity discovery and
management continue to require direct Wi-Fi, and their existing inline Wi-Fi
guidance remains unchanged.
