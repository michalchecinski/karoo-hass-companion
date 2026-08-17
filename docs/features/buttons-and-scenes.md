# Buttons and scenes

## Goal

Allow riders to add Home Assistant button and scene entities to Quick Access as deliberate one-tap controls.

## Scope

- Support Home Assistant `button` and `scene` entities in Quick Access.
- Include both entity domains in the existing Quick Access management flow.
- Keep each configured control tied to one Home Assistant entity ID.
- Use only the standard action for each domain; arbitrary Home Assistant actions and action data remain outside the product boundary.
- Creating, editing, or deleting buttons and scenes is outside scope.
- Home Assistant event entities and physical button events are outside scope.

## User experience

- Buttons and scenes use the same compact tile presentation as scripts.
- A tile shows the entity's Home Assistant name and icon, without state or an additional action label.
- Tapping a button tile invokes the button.
- Tapping a scene tile activates the scene.
- Button and scene entities appear in the existing **Manage Quick Access** chooser.
- Both domains participate in the existing search and domain-filter behavior.
- Each entity exposes exactly one **Add** action.
- The existing management screen supports removing and reordering configured button and scene controls.
- A known unavailable entity cannot be invoked and remains available for removal from the configured controls.
- Confirmation, PIN entry, progress, and outcome feedback use the existing Quick Access flows.

## Functional behavior

- Entity discovery includes the `button` and `scene` domains and continues to require direct Wi-Fi.
- A button resolves to the standard `button.press` Home Assistant action.
- A scene resolves to the standard `scene.turn_on` Home Assistant action.
- Each request identifies only the configured entity ID and supplies no optional action data.
- Buttons and scenes are treated as stateless controls. Their reported state does not select or change the operation, and the app does not display it on the tile.
- Invoking a button or scene does not require a pre-action state refresh, matching the existing script behavior.
- Normal action requests follow the user's selected connection policy.
- Only one global action request may be active at a time.
- The app never automatically retries or replays an action request, including after refreshing an expired access token.
- A successful Home Assistant response produces **Requested**. It does not prove that a button's downstream effect occurred or that every entity in a scene reached its requested state.
- A definite rejection before execution produces **Failed**. A lost or ambiguous response produces **Outcome uncertain**.
- Discovery refreshes the stored friendly name and icon without changing the configured entity ID.
- The app prevents duplicate Quick Access controls for the same button or scene entity.

Home Assistant references:

- [Button entity](https://developers.home-assistant.io/docs/core/entity/button/)
- [Scene entity](https://developers.home-assistant.io/docs/core/entity/scene/)
- [Scenes integration](https://www.home-assistant.io/integrations/scene/)
- [REST API](https://developers.home-assistant.io/docs/api/rest/)

## Security behavior

- Button and scene controls follow the configured whole-app PIN mode.
- In selected-actions PIN mode, either control can be marked as PIN-protected.
- Either control can use the existing optional confirmation setting.
- Neither domain requires confirmation automatically.
- Confirmation occurs before PIN authorization, matching existing Quick Access actions.
- Actions use the existing OAuth credentials, encrypted token storage, connection policy, and account-reset behavior.

## Acceptance criteria

- Discovery returns supported button and scene entities and continues to exclude unsupported domains.
- The management screen can search and filter both domains and offers exactly one Add action for each entity.
- Adding the same button or scene entity twice does not create a duplicate control.
- Button and scene controls can be added, removed, and reordered without affecting existing control types.
- Quick Access tiles show the friendly name and icon but no entity state or additional action label.
- A button request calls `button.press` with only its entity ID.
- A scene request calls `scene.turn_on` with only its entity ID.
- Requests use Wi-Fi-only or Companion fallback transport according to the saved connection policy.
- A successful response displays **Requested** without claiming completion.
- A definite HTTP rejection displays **Failed**; a transport failure with an uncertain request outcome displays **Outcome uncertain**.
- A 401 may refresh credentials but never replays the action request automatically.
- A known unavailable button or scene cannot be invoked and can still be removed in management.
- Optional confirmation is honored for both domains, and canceling it sends no request.
- Selected-action PIN protection is honored for both domains, and a failed or canceled unlock sends no request.
- Whole-app PIN protection prevents access to these controls until the app is unlocked.
- Existing scripts, locks, covers, lights, and switches retain their current discovery and action behavior.
- Unit tests cover entity-to-action mapping, duplicate prevention, action outcomes, confirmation, PIN gating, and no-replay behavior.
- Compose tests at the Karoo viewport cover discovery, management, long tile names, unavailable entities, confirmation, PIN entry, and outcome feedback.
- Tests with a fake Home Assistant server cover successful requests, definite rejection, expired credentials, malformed responses, and interrupted connectivity for both domains.
