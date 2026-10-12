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

## Regression

The unlock screen currently creates a masked numeric-password field without
requesting its focus or showing the keyboard. An instrumentation test launches
the real activity with an isolated whole-app PIN configuration and checks focus
and Android IME visibility without tapping the field.

Test data uses only `https://demo.example.com`, no Home Assistant credentials,
and a local test PIN. No live Home Assistant installation is required.
