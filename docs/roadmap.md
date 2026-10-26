# Roadmap

## [Icons 1:1 from Home Assistant configuration](features/home-assistant-icons.md)

Render the icon configured for each entity in Home Assistant instead of mapping it to the app's small bundled icon set.

## [WebSocket-based state updates](features/websocket-state-updates.md)

Use live Home Assistant state updates while the app is active, so Quick Access reflects changes made outside the Karoo without manual refreshes.

## [Physical-button navigation](https://github.com/michalchecinski/karoo-hass-companion/issues/23)

Explore navigation and activation of Quick Access controls with Karoo physical buttons, subject to confirming the supported input APIs.

## [Large-control layout](https://github.com/michalchecinski/karoo-hass-companion/issues/25)

Offer a single-column Quick Access layout with larger text and touch targets while preserving the configured controls and their order.

## [Check uncertain result](https://github.com/michalchecinski/karoo-hass-companion/issues/26)

Let riders refresh an entity's current state after an uncertain result without replaying the original service command.

## [Edit existing controls](https://github.com/michalchecinski/karoo-hass-companion/issues/27)

Allow riders to change a configured control's PIN protection and optional confirmation without removing and re-adding it.

## [Repair missing controls](https://github.com/michalchecinski/karoo-hass-companion/issues/28)

Let riders replace a missing entity reference with a compatible entity they choose, preserving the control's place and protections.
