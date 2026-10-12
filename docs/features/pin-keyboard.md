# PIN keyboard

## Platform feasibility

Automatic input focus and keyboard display are feasible in the app's foreground
Android activity on Karoo. The [official Karoo SDK sample](https://github.com/hammerheadnav/karoo-ext/blob/master/app/src/main/AndroidManifest.xml)
uses a normal Android activity, rather than a remote data-field view. The
[Karoo Notepad app](https://github.com/timklge/karoo-notepad/blob/main/app/src/main/kotlin/de/timklge/karoonotepad/screens/MainScreen.kt)
also uses a standard Compose text field for keyboard input.

[Android's keyboard guidance](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)
requires both window focus and an active text editor before requesting the
software keyboard. A text-field focus request alone does not guarantee a visible
keyboard. No Karoo SDK effect or additional permission is needed for this
foreground input.

The emulator's Android keyboard can verify this API behavior. It cannot certify
the keyboard bundled with Karoo firmware; physical Karoo validation remains a
separate acceptance check.

## Behavior

Opening the unlock screen automatically focuses the masked PIN field and opens
the numeric password keyboard. It waits for a focused window, then a composition
frame to attach the enabled field, requests editor focus, and waits another
frame before requesting the keyboard through Compose's keyboard controller.

The request runs when the window gains focus or PIN verification finishes with
the unlock screen still present. Ordinary text edits and other recompositions
do not repeat it, so dismissing the keyboard remains possible. It does not use
a forced keyboard flag, a fixed delay, or a polling loop. Existing PIN length,
masking, lockouts, and Done-to-unlock behavior remain in place.

The activity explicitly requests Android's `adjustResize` keyboard behavior.
The PIN layout respects the IME inset and uses compact vertical spacing so the
field and Unlock button remain visible with the keyboard at Karoo resolution.
It can scroll when pending-action text or a larger keyboard needs more space.

## Regression verification

Before the fix, an instrumentation test launching the real activity failed
because the PIN field was not focused. After the fix, the same test passed both
the focus assertion and Android IME visibility check without tapping the field
on the 480×800 Karoo AVD (Android 16 / API 36).

The instrumentation coverage also checks activity recreation, return from the
background, selected-action PIN entry, incorrect-PIN clearing and retry with
Done, keyboard dismissal across an unrelated recomposition, and visibility of
the field and Unlock button while the IME is open. The selected-action test
invokes the real ViewModel route directly because the isolated account has no
live connection; it does not send a Home Assistant action.

A normal cold launch outside the test runner was also checked: Android reported
`mInputShown=true`, numeric-password `inputType=0x12`, and Done IME action
`imeOptions=0x2000006`. Tapping 1, 2, 3, 4 and Done on the displayed Android
keyboard unlocked the fixture without first tapping the field. Visual inspection
found Android initially chose `adjustPan`, which covered Unlock; the explicit
`adjustResize` setting fixes that, and the test checks the button's bounds against
the actual visible Android window rather than relying only on Compose visibility.

Run the checks with:

```sh
./gradlew ktlintCheck check assembleDebug :app:connectedDebugAndroidTest
```

Enable the emulator's **Show virtual keyboard** option when its hardware
keyboard is enabled (`adb shell settings put secure show_ime_with_hard_keyboard 1`).
The screenshot includes the keyboard deliberately, since its automatic display
is the behavior being verified. Android status and navigation bars are cropped
out using the repository's screenshot helper.

Test data uses only `https://demo.example.com`, no Home Assistant credentials,
and a local test PIN. No live Home Assistant installation is required.
