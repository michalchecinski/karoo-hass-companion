# WebSocket-based state updates

## Goal

Keep configured Quick Access entity state current while the rider is actively viewing the app, without maintaining a connection throughout the ride.

## Scope

- Use the Home Assistant WebSocket API for live state updates over direct Wi-Fi.
- Keep WebSockets unavailable through Hammerhead Companion fallback, whose supported extension interface provides bounded HTTP requests rather than persistent sockets.
- Maintain a WebSocket only while the app is in the foreground, Quick Access is visible, and whole-app PIN protection is unlocked.
- Disconnect when Quick Access is no longer visible, the app leaves the foreground, the display turns off, Wi-Fi is lost, the account is reset, or whole-app protection locks.
- Keep REST discovery, action requests, pre-action safety refreshes, and post-action verification behavior unchanged.
- Do not add background monitoring, notifications, automation, delayed actions, or ride-screen integration.

## User experience

- Configured tiles update when Home Assistant reports a relevant state or attribute change while the WebSocket is active.
- Live updates do not change how a rider invokes, confirms, or authorizes an action.
- WebSocket unavailability never disables normal Quick Access actions; they continue to follow the selected connection policy and existing refresh rules.
- The app does not display WebSocket connection state, reconnection messages, or a live-update indicator.
- Connection loss degrades silently to the existing REST-based behavior.

## Functional behavior

- Connect to the authenticated Home Assistant WebSocket API at `/api/websocket` using the configured HTTPS origin's secure WebSocket equivalent.
- Authenticate with the existing OAuth access token using the documented Home Assistant WebSocket authentication sequence.
- If Home Assistant rejects authentication, make one silent access-token refresh through the existing OAuth flow and immediately reconnect with the refreshed token.
- If token refresh fails or the refreshed token is also rejected, stop WebSocket connection and authentication retries for the current session. Allow the existing REST behavior to continue, and allow a new WebSocket attempt only after credentials change or a new unlocked Quick Access session starts.
- Subscribe only to state changes for the distinct entity IDs currently configured in Quick Access.
- After authentication and subscription succeed, perform one REST refresh of the distinct configured entity IDs to recover state changes missed while the WebSocket was disconnected.
- Start the subscription before the recovery refresh so changes occurring during the refresh are not missed.
- Merge REST and WebSocket snapshots by Home Assistant's `last_updated` value so an older REST response cannot overwrite a newer WebSocket update.
- A failed recovery refresh does not close a healthy WebSocket; subsequent live events continue to update their entities.
- Ignore events for entity IDs that are not currently configured.
- Apply accepted state updates through the same entity parsing and UI-state path used by REST responses.
- Adding or removing a Quick Access entity updates the active subscription without requiring an app restart.
- WebSocket state never replaces the immediate REST refresh required before resolving security-sensitive or state-dependent actions.
- Implement the WebSocket connection with OkHttp in a dedicated Home Assistant WebSocket client.
- Bind the WebSocket client's socket creation and DNS resolution to the active Android Wi-Fi `Network`.
- Create a new network-bound WebSocket client when the active Wi-Fi network changes; do not reuse sockets or DNS results from the previous network.
- After an unsuccessful connection or an unexpected disconnect, retry after approximately 1, 2, 4, 8, 16, and then 30 seconds, remaining capped at 30 seconds for subsequent attempts.
- Reset the reconnection delay after a successful connection.
- Retry only while all WebSocket lifecycle conditions remain satisfied. Stop pending retries when Quick Access is hidden, the app leaves the foreground, the display turns off, Wi-Fi is lost, the account is reset, or whole-app protection locks.
- When a new active Wi-Fi network becomes available while the lifecycle conditions are satisfied, attempt a fresh connection immediately rather than waiting for the previous retry delay.
- Treat any valid incoming WebSocket message as proof that the connection is alive and restart its idle timer.
- After 60 seconds without an incoming message, send a Home Assistant WebSocket `ping` command.
- Require the matching `pong` response within 10 seconds. If it does not arrive, close the WebSocket and enter the normal exponential reconnection sequence.
- Do not send heartbeat traffic while other incoming messages are already proving that the connection is alive.
- Use OkHttp only for WebSocket communication. Keep `DirectWifiTransport` on the existing network-bound `HttpURLConnection` implementation and keep `KarooTransport` unchanged.
- Keep the exact OkHttp version and any build-tool upgrades outside this feature specification; select a mutually compatible, maintained version during implementation planning.

References:

- [Home Assistant WebSocket API](https://developers.home-assistant.io/docs/api/websocket/)
- [Karoo SDK HTTP request interface](https://github.com/hammerheadnav/karoo-ext/blob/master/lib/src/main/kotlin/io/hammerhead/karooext/models/KarooEvent.kt)

## Security behavior

- Derive only a `wss://` endpoint from the validated Home Assistant HTTPS origin. Never downgrade to `ws://` or follow a redirect to an insecure origin.
- Use Android's normal certificate-chain and hostname verification. Do not add trust-all handling or a separate certificate exception for WebSockets.
- Send the access token only in Home Assistant's WebSocket authentication message after the secure connection is established. Do not place tokens in the URL or logs.
- Do not persist WebSocket messages, authentication payloads, or heartbeat payloads.
- Accept state updates only for entity IDs currently configured in Quick Access, even if the server sends other events.
- Treat incoming WebSocket data as state input only. Never translate an incoming event into a service call or other Home Assistant action.
- Reject malformed messages without changing displayed state. Close the connection and use normal reconnection behavior after a protocol-level failure that makes the stream unsafe to continue.
- Limit each accepted incoming message to the existing 2 MB direct-response bound. Oversized messages close the connection and fall back to existing REST behavior.
- Cancel the socket, heartbeat, subscriptions, pending recovery refresh, and scheduled retries when any WebSocket lifecycle condition stops being satisfied.

## Acceptance criteria

- With Quick Access visible, the app in the foreground, the display on, whole-app protection unlocked, valid credentials, and direct Wi-Fi available, a test Home Assistant server observes one authenticated `wss://.../api/websocket` connection.
- DNS lookup and socket creation use the active Android Wi-Fi `Network`; a non-Wi-Fi default network is not used for the WebSocket.
- No WebSocket connection is created while Quick Access is hidden, the app is backgrounded, the display is off, whole-app protection is locked, no configured entity exists, or direct Wi-Fi is unavailable.
- The client subscribes once for the distinct configured Quick Access entity IDs and does not request state events for unconfigured entity IDs.
- After subscription succeeds, the client performs one REST recovery refresh for each distinct configured entity ID without starting periodic REST polling.
- A WebSocket event received during a recovery refresh remains displayed when the older REST response completes afterward.
- A valid state-change event updates the matching tile's state and relevant attributes without manual refresh.
- Events for unconfigured entities and malformed events do not change displayed state or invoke an action.
- An event reporting removal or loss of the configured entity makes that entity unavailable using the existing unavailable-state presentation.
- Adding or removing a Quick Access entity replaces the active subscription with the new distinct entity set without restarting the app, and performs one recovery refresh for the resulting set.
- An unexpected disconnect schedules attempts after approximately 1, 2, 4, 8, 16, and 30 seconds, remains capped at 30 seconds, and resets the sequence after a successful connection.
- Hiding Quick Access, backgrounding or locking the app, turning off the display, losing Wi-Fi, or resetting the account cancels the socket and any scheduled retry before another attempt occurs.
- Replacing the active Wi-Fi network cancels the old network-bound client and starts one immediate connection using the new network.
- Receiving any valid message postpones the heartbeat. Sixty seconds of inbound inactivity sends one Home Assistant `ping`; a matching `pong` within 10 seconds keeps the connection open.
- Failure to receive the matching `pong` within 10 seconds closes the socket and starts the exponential reconnection sequence.
- One `auth_invalid` response causes at most one OAuth token-refresh attempt and one immediate reconnect with the refreshed access token.
- Failed refresh or rejection of the refreshed token stops WebSocket retries for the current session while existing REST operations remain available.
- WebSocket connection, authentication, subscription, heartbeat, and recovery failures do not disable or alter existing REST discovery, refresh, action, or verification behavior.
- Service calls continue to use the existing HTTP transports; no Home Assistant action is sent through the WebSocket and no service POST gains automatic retry behavior.
- When Companion fallback is selected, REST requests retain their current Karoo SDK behavior while WebSocket updates remain direct-Wi-Fi-only.
- A TLS certificate or hostname validation failure prevents the WebSocket connection without an insecure retry, and access tokens do not appear in captured logs or request URLs.
- An incoming message larger than 2 MB is not applied to UI state, closes the WebSocket, and leaves REST behavior available.
- On a physical Karoo, state changes made in Home Assistant update visible configured tiles without manual refresh; leaving Quick Access or turning off the display closes the connection and stops heartbeat traffic.
- The implementation's unit tests, integration tests against a controllable WebSocket server, and release build pass, and the release APK size difference from the pre-feature build is recorded.
