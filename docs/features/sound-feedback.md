# Optional sound feedback

Quick Access can give audible feedback through the Karoo's internal beeper.
Enable **Sound feedback** in Settings, above Connection policy. It is disabled
by default for fresh and existing installations. The preference is saved across
app restarts and cleared when the account is erased. No Home Assistant changes
are required.

Visual outcome feedback remains available with either setting. Sounds describe
the same execution events; they do not change service calls, confirmation,
PIN protection, polling, or automatic retry behavior.

## Cues and certainty

| Event | Pattern | Meaning |
|---|---|---|
| Accepted request | One short 4 kHz beep | Home Assistant returned a valid service-call acceptance response. |
| Verified completion | Two short 5 kHz beeps | The app observed the expected state or a changed toggle state. |
| Definite failure | Silent; visual feedback only | Home Assistant rejected the request. |
| Uncertain outcome | Silent; visual feedback only | The response was lost or invalid, or the app could not verify completion. |

Tones last 200 ms and pauses 100 ms.
An initial **Sending** state is silent. A valid response can produce the accepted
cue, followed by a completion cue only if state verification succeeds.
If verification remains uncertain, the existing visual message explains the
result without another sound. Acceptance never proves that a physical device moved.

Scripts, buttons, scenes, cover Stop, and cover position presets have request-only semantics: they can
produce acceptance feedback but never completion feedback. Stop's best-effort
refresh does not establish physical completion. Presets keep updating reported
position during polling, but reaching the target does not generate a completion
cue. A malformed or missing response
produces visual uncertainty without any sound. A rejected toggle stays
**Failed** rather than being overwritten by a polling-timeout result.

Blocked actions, canceled confirmation, PIN entry, discovery, and ordinary state
refreshes do not generate action cues. Feedback is emitted from execution events,
so recomposition, reopening Home, and restoring a saved preference do not replay
old outcomes. Disabled feedback and outcomes arriving while the app is in the
background remain silent.

## Karoo integration and limits

The app uses the official
[`PlayBeepPattern` effect](https://github.com/hammerheadnav/karoo-ext/blob/1.1.3/lib/src/main/kotlin/io/hammerhead/karooext/models/KarooEffect.kt)
from the existing `karoo-ext:1.1.3` dependency. That SDK release documents
[KOS 1.538.2049 or newer](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.3).
No dependency or minimum OS requirement changes are introduced.

The sound service is bound only while enabled and in the foreground, and released
when disabled, backgrounded, or the ViewModel is cleared. A disconnected service
drops cues; nothing is replayed on reconnection. Audio failures cannot change
the visual outcome or fail an action. There is no Android media-player fallback,
volume override, or attempt to bypass Karoo sound controls.

The official SDK exposes neither playback completion nor documented mute and
pattern-overlap guarantees. Emulator checks validate the preference UI and
graceful operation without Karoo services. They cannot establish audibility.
Before hardware acceptance, verify on a supported physical Karoo 3:

- Both patterns can be distinguished outdoors without being intrusive.
- System sound settings and restrictions are respected.
- Acceptance followed by an immediately verified completion remains intelligible.
- Disabling feedback, backgrounding, and service reconnection do not replay cues.
- Scripts, scenes, buttons, Stop, position presets, rejected requests, and lost responses never
  produce a false completion cue.

## Automated verification

Execution tests drive the real repository with a recording HTTP transport and
assert cue sequences and visual outcomes for stateless acceptance, lock/cover
verification, preset progress and target timeouts, changed and unchanged toggles,
Stop refresh failure, definite
rejection, malformed/missing/lost responses, cancellation, disabled/background
feedback, and broken audio. Settings tests cover old-data migration and persisted
round trips. A Compose test checks that the full Settings row toggles the switch
and exposes its checked state to accessibility.

Implementation checks passed: `ktlintCheck check assembleDebug`, 89 unit tests
in each of the debug and release variants, and six targeted emulator UI tests.
The switch test also passed after the final compact-layout adjustment. The
updated Settings screenshot is in the README gallery. Physical sound checks
listed above remain unverified.
