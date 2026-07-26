# Karoo Home Assistant Companion

[![CI](https://github.com/michalchecinski/karoo-hass-companion/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/michalchecinski/karoo-hass-companion/actions/workflows/ci.yml)

Karoo Home Assistant Companion is a focused Home Assistant remote for the Hammerhead Karoo bike computer. It is intended to provide quick access to a small, user-selected collection of actions rather than reproduce a complete Home Assistant dashboard.

> **Unofficial project:** Not affiliated with or endorsed by the Open Home Foundation or Home Assistant.

## Overview

The project is currently pre-release and the documented v1 MVP is not yet implemented. There is no supported release APK to install.

- Product direction: [`docs/app-idea.md`](docs/app-idea.md)
- v1 plan: [`docs/v1-mvp-plan.md`](docs/v1-mvp-plan.md)
- Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)

## Development

Build and verify the application with JDK 17:

```bash
./gradlew ktlintCheck check assembleDebug
```

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full development workflow. Maintainers should use [`docs/releasing.md`](docs/releasing.md) for signed releases.
