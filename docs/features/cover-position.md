# Cover position

## Goal

Let riders see a cover's reported position and move supported covers to deliberately configured target positions.

## Scope

- Add position reporting to existing state-aware cover controls.
- Add position presets as separate Quick Access controls for supported cover entities.
- Preserve the existing open, close, and stop behavior.
- Do not provide relative `+` or `-` position controls.
- Preset targets are whole percentages from 1 through 99.
- A cover can have multiple presets at different target positions.
- Cover tilt position is outside scope.
- Free-form sliders and custom preset labels are outside scope.

## User experience

- An existing state-aware cover tile includes the latest position reported by Home Assistant when one is available.
- Each configured position preset appears as its own Quick Access tile.
- Tapping a preset requests its stored absolute target position.
- The cover action picker offers an option to add a position preset when the entity supports position commands.
- Adding a preset requires the user to enter its target percentage.
- The app rejects values outside 1 through 99 and an exact duplicate of an existing preset for that cover.
- Configured presets use the existing removal and reordering controls. Changing a preset requires removing it and adding the new target.
- A preset is identified on Quick Access and in management by the cover's friendly name and target percentage.
- While a preset request is in progress, its tile uses the existing action-status presentation.
- A preset tile shows the cover's friendly name, current state and reported position, and the preset target.

## Functional behavior

- Position support uses Home Assistant's `current_position` attribute and `cover.set_cover_position` action.
- Home Assistant positions use `0` for fully closed and `100` for fully open.
- The app accepts a reported position only when it is a whole percentage from 0 through 100; a missing or invalid value is treated as unavailable position data.
- A preset action sends the configured entity ID and target `position` percentage.
- The cover must advertise support for setting its position before a preset can be added or invoked.
- The app refreshes the entity immediately before resolving a preset action.
- The preset action is not sent when refresh fails, the entity is unavailable, or the refreshed current position is missing.
- The app permits a preset request while a cover is already moving; the refreshed position still determines whether mandatory confirmation applies.
- A successful Home Assistant response produces **Requested**.
- After an accepted request, the app polls using the existing post-action refresh behavior and updates the displayed state and position from each successful response.
- Reaching the target position does not change **Requested** to **Completed**. Polling is for display refresh, not physical completion verification.
- Failure to observe the target position does not by itself produce **Outcome uncertain**.
- A definite rejection before execution produces **Failed**. A lost or ambiguous action response produces **Outcome uncertain**.
- The app never automatically retries or replays a preset request, including after refreshing an expired access token.
- Normal position requests follow the user's selected connection policy.
- Only one global action request may be active at a time.
- The stored target position is part of the configured action identity. The app prevents an exact duplicate entity-and-position pair while allowing other presets for the same cover.
- Discovery refreshes the stored friendly name and icon without changing the configured entity ID or target position.

Home Assistant references:

- [Cover integration](https://www.home-assistant.io/integrations/cover/)
- [Cover entity](https://developers.home-assistant.io/docs/core/entity/cover/)
- [Frontend cover position control](https://github.com/home-assistant/frontend/blob/dev/src/state-control/cover/ha-state-control-cover-position.ts)
- [Frontend favorite cover positions](https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/components/covers/ha-more-info-cover-favorite-positions.ts)

## Security behavior

- The target and refreshed current position determine whether the preset moves the cover toward open or closed.
- A target greater than the current position always requires confirmation, matching the existing protection for **Open**.
- A target lower than the current position uses the preset's optional confirmation setting.
- Position presets follow the existing whole-app and selected-action PIN modes.
- Confirmation occurs before PIN authorization, matching existing Quick Access actions.
- Canceling confirmation or failing PIN authorization sends no request.
- Requests use the existing OAuth credentials, encrypted token storage, connection policy, and account-reset behavior.

## Acceptance criteria

- Discovery parses `current_position` values from 0 through 100 and ignores missing, non-numeric, or out-of-range values.
- Existing state-aware cover tiles display a valid reported position and retain their current behavior when no position is reported.
- Only a cover advertising Home Assistant position support offers **Add position preset**.
- The add flow accepts whole-number targets from 1 through 99 and rejects 0, 100, fractions, non-numeric input, and values outside the range.
- Multiple different presets can be added for one cover, while an exact duplicate entity-and-position pair is rejected.
- Preset tiles and management rows distinguish controls for the same cover by target percentage.
- Presets can be removed and reordered using the existing management controls.
- Invoking a preset refreshes the cover before presenting confirmation, requesting PIN authorization, or sending the action.
- Refresh failure, unavailable state, or missing current position blocks the action and explains that it is unavailable.
- A target greater than the refreshed current position always requires confirmation.
- A target lower than or equal to the refreshed current position requires confirmation only when the preset's optional confirmation setting is enabled.
- Canceling confirmation sends no request.
- Selected-action and whole-app PIN modes protect presets using the existing authorization flow.
- Failed or canceled PIN authorization sends no request.
- An accepted preset calls `cover.set_cover_position` with exactly the entity ID and stored target percentage.
- A preset can replace the target while a cover reports that it is opening or closing.
- Requests use Wi-Fi-only or Companion fallback transport according to the saved connection policy.
- A successful action response displays **Requested** and never claims **Completed** based on position polling.
- Successful polling responses update the displayed state and position, including gradual movement and a final exact target.
- Failure to observe the target during polling leaves the accepted action as **Requested**.
- A definite HTTP rejection displays **Failed**; an ambiguous transport failure displays **Outcome uncertain**.
- A 401 may refresh credentials but never replays the preset action automatically.
- Existing open, close, and stop resolution, confirmation, verification, and outcome behavior remains unchanged.
- Unit tests cover parsing, capability detection, input validation, duplicate identity, direction-aware confirmation, action payloads, outcomes, and no-replay behavior.
- Compose tests at the Karoo viewport cover position display, adding multiple presets, invalid input, long names, confirmation, PIN entry, unavailable data, management, and request feedback.
- Tests with a fake Home Assistant server cover gradual movement, exact and inexact final positions, already-reached targets, moving covers, definite rejection, expired credentials, malformed responses, and interrupted connectivity.
