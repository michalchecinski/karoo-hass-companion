# Ride-end notification

## Behavior

Karoo HASS registers a Karoo extension service that observes the SDK's ride
state even while the app UI is closed. After an observed recording or paused
ride changes to `RideState.Idle`, it posts a Karoo `SystemNotification`:

- Header: **Karoo HASS**
- Message: **Ride finished. Open your Home Assistant controls.**
- Action: **Open Karoo HASS**

The action opens Quick Access using the normal onboarding, connectivity, and
PIN checks. Posting or opening the notification does not execute any Home
Assistant action. **Settings → Ride-end notification** turns future ride-end
notifications on or off. It defaults to on for new and existing installations,
and the saved choice survives app and extension restarts. The extension checks
the saved setting when a ride ends, so changing it requires no restart.
Turning it off does not remove a notification already posted in Control Center;
that entry can be dismissed there.

Manual pauses, auto-pauses, stopping at traffic lights, and resuming never
trigger the notification. Initial idle and repeated idle updates are ignored.
A stable notification ID updates the existing entry instead of accumulating
one entry per ride.

## Platform limits

The supported destination is Control Center's notifications panel. A brief
top-of-screen banner is not guaranteed and must be checked on a physical Karoo.
This uses `SystemNotification`, not Firefly's `InRideAlert`.

The SDK defines idle as recording not started or already finished. It does not
distinguish saving from discarding a ride in this event, so ending either way
can produce the notification. This is an end-of-recording reminder, not proof
that a ride was saved or uploaded.

Karoo must discover and run the extension. A service restart or lost SDK
connection clears the observed ride to avoid stale notifications; a ride that
finishes while disconnected can be missed. Reconnecting during recording or
pause arms detection again.

## Device test

1. Install the APK and confirm Karoo HASS is enabled in Karoo's extensions.
2. With no ride running, open and close the app: no ride-end notification.
3. Start a ride and leave Karoo HASS closed.
4. Manually pause, resume, and test auto-pause: no ride-end notification.
5. Finish the ride: check for an immediate banner, then check the notification
   in Control Center. Note when it appears relative to the save/summary flow.
6. Tap **Open Karoo HASS**: Quick Access opens. Repeat with whole-app PIN
   enabled and confirm the PIN is required before controls become available.
7. Finish another ride: verify the notification updates and still opens the app.
8. Turn **Ride-end notification** off in Settings, close the app, and finish
   another ride: no new notification. Restart the app and verify the switch is
   still off, then turn it on and finish a new ride to restore notifications.

Physical-device notification presentation and end-of-ride timing remain to
be validated.

The Settings switch layout and persistence of the off choice after an app
restart were verified on an isolated 480×800 emulator. Settings codec tests
cover the default for existing installations and persistence of both choices.
