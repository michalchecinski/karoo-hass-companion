# Karoo Home Assistant Companion

## The idea

Karoo Home Assistant Companion is a focused Home Assistant remote for the
Hammerhead Karoo 3. It gives a rider quick access to a small, personally chosen
set of Home Assistant controls through an interface designed for a bike
computer.

The inspiration is closer to Home Assistant's CarPlay Quick Access experience
than to its full dashboard. The app is not intended to reproduce Lovelace,
browse the whole smart home, show history or sensor dashboards, or expose
arbitrary service calls. It presents only the controls the user deliberately
selected.

Example uses include unlocking a door or opening a garage after a ride,
turning on a light, or starting a Home Assistant script that prepares the home
before arrival, such as adjusting heating or air conditioning.

## Why it should exist

A Karoo may be the connected device a cyclist can reach most easily while
riding or handling their bike. A few useful home actions could be quicker and
more convenient to access there than through a phone.

A full Home Assistant client would bring unnecessary complexity to a small
screen and expose far more of the home than the rider needs in that moment. A
deliberately limited companion can prioritize speed, clarity, and confidence
for a small number of familiar actions.

The app should also be straightforward to adopt. It should work with supported
Home Assistant capabilities and the user's existing installation, without
requiring a project-specific Home Assistant component or a separate
project-operated service.

## What the product should provide

Normal use centers on one user-managed Quick Access collection. Different
supported control types live together rather than being separated into
smart-home domain dashboards.

The product should let the user:

- connect the Karoo app to an existing Home Assistant installation;
- choose whether Home Assistant connections are limited to direct Wi-Fi or may
  fall back through the paired phone and Hammerhead Companion app;
- choose, remove, and reorder a small set of controls;
- recognize controls through names, icons, capabilities, and state provided by
  Home Assistant;
- invoke simple actions and operate supported stateful devices;
- choose whether PIN protection is disabled, applies to the whole app, or
  applies only to individually selected actions regardless of their Home
  Assistant entity type; and
- understand whether an action was requested, failed, completed, or has an
  uncertain outcome.

The exact Home Assistant control types included should be at least scripts locks and covers, but probably all of the entity ypes but not in the v1?

## How the product should work

At a product level, the intended experience is:

1. The rider connects the app to Home Assistant using a supported form of
   access.
2. They choose whether the app may connect only over direct Wi-Fi or may use
   Hammerhead Companion through the paired phone as a fallback.
3. They select a small set of Home Assistant controls for Quick Access.
4. The app presents those controls in a simple, user-defined order (list or tiles).
5. The rider chooses an explicit action.
6. Depending on the selected PIN mode, the app requires no PIN, protects all
   access, or protects only the actions the user marked for protection.
7. The app reports the result as clearly and honestly as the available Home
   Assistant information allows.

Setup and control management should happen on the Karoo where practical, apart
from authorization or account management that belongs to Home Assistant.

## Product experience principles

### Focused

The app should remain a curated companion rather than become a general Home
Assistant dashboard or administration client.

### Glanceable and glove-friendly

The interface should favor large, high-contrast controls, clear text and icons,
and imprecise or gloved touch on the Karoo's small portrait display. Showing
fewer controls and scrolling is preferable to shrinking them into a dense
grid. State should not be communicated by color alone.

### Familiar

Home Assistant remains the source of truth for the identity and behavior of
its controls. If a selected control disappears or changes, the app should not
silently replace it with something that merely looks similar.

### Deliberate

Actions should result from visible user intent. Controls with greater
consequences, such as unlocking a door or opening a gate, should receive
protection proportionate to their risk.

### User-selected PIN protection

The user should be able to choose between three PIN protection modes:

- disable PIN protection;
- protect access to the whole app; or
- protect only actions individually selected by the user.

When PIN protection is disabled, Home Assistant controls in the app are
available without this additional local authorization step. The app should
explain this consequence when the user selects that mode.

In the selected-actions mode, protection is assigned to a configured action
rather than inferred from its Home Assistant entity type. The user may
therefore protect any script, lock, cover, light, switch, button, or other
supported action while leaving another action of the same type unprotected.

The exact PIN rules, relocking behavior, failed-attempt handling, and recovery
flow should be determined during specification-driven development.

### User-selected connection security

The user should be able to choose between two connection policies:

- **Wi-Fi only:** Home Assistant communication is restricted to a direct Karoo
  Wi-Fi connection.
- **Allow Hammerhead Companion fallback:** when direct Wi-Fi is unavailable,
  the app may use the paired phone and Hammerhead Companion app to reach Home
  Assistant.

This is a security and availability choice, not only a connectivity
optimization. Companion fallback can make controls available in more
situations, but it adds the paired phone and Companion app to the connection
path. The app should explain this trade-off and must not enable fallback
without the user's explicit choice.

Quick Access entity discovery and refresh should follow the same connection
policy selected by the user. If discovery cannot connect, the app should show
the connection failure without also implying that Home Assistant contains no
supported entities. An empty entity result should be shown only after a
successful discovery request returns no supported entities.

### Honest

Home Assistant accepting a request does not always prove that a physical
device moved or that every step inside a script completed. The app should not
claim more certainty than it can establish.

## Product boundaries

The product is not intended to be:

- a complete Home Assistant dashboard or all-entity browser;
- a history, analytics, or sensor-monitoring application;
- an arbitrary service-call or automation-building console;
- a Home Assistant server-management client;
- a background automation or delayed-action system;
- a project-operated cloud relay or hosted smart-home service; or
- a ride data field, map layer, or background tracking extension.

## Platform constraints

- Karoo 3 is the hardware target. Its small display, touch interaction,
  connectivity, application lifecycle, and Android environment shape the
  product experience.
- Home Assistant remains responsible for users, permissions, entity state,
  capabilities, and execution of home actions.
- The app should rely on supported Home Assistant access mechanisms and Karoo
  platform capabilities.
- Karoo network access may use direct Wi-Fi or, when explicitly allowed by the
  user, a fallback through the paired phone and Hammerhead Companion app. These
  paths may have different behavior and limitations.
- Authentication, connectivity, usability, security, and lifecycle behavior
  need validation on a physical Karoo 3.

These constraints describe the environment in which the product operates; they
do not select a particular implementation. Detailed route selection, server
address, transport security, failure handling, and fallback behavior should be
determined during specification-driven development.
