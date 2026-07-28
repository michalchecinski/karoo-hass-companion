# Home Assistant companion for Hammerhead Karoo

Karoo Home Assistant Companion is a focused Home Assistant remote for the Hammerhead Karoo bike computer. It provides quick access to a small collection of actions selected by the rider instead of reproducing a complete Home Assistant dashboard.

[![Build](https://github.com/michalchecinski/karoo-hass-companion/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/michalchecinski/karoo-hass-companion/actions/workflows/ci.yml)

##  Features

- Connect the app to your existing Home Assistant.
- Keep your favorite home controls together for quick access.
- Run scripts and control locks, covers, lights, and switches.
- Choose how the app accesses Home Assistant based on your needs: connect only when Karoo has Wi-Fi, or allow your paired phone to provide network access when Wi-Fi is unavailable.
- Protect the whole app or selected controls with a PIN.
- Confirm important actions before unlocking a door or opening a gate.

### Example use cases

- Open your garage door or gate as you arrive.
- Unlock the door without taking out your phone.
- Turn on outdoor or entrance lights when coming home after dark.
- Run a script near the end of your ride to prepare a comfortable temperature at home before you return.

## Installation

### New Karoo (aka Karoo 3)

For New Karoo (aka Karoo 3), you can use Hammerhead's procedure to install the app:

1. Using your phone, long-press [the latest release download link](https://github.com/michalchecinski/karoo-hass-companion/releases/latest/download/karoo-hass-companion.apk) and share it with the Hammerhead Companion app. You can use the QR code below to send that page to your phone.
2. Karoo should show an info prompt about the app installation. Press the "Install" button.
3. Open the app from the main menu.

<details>

<summary>QR code to send this page to your phone</summary>

### Use the following QR code to send this page to your phone for easier uploading of the app to the Karoo via the Companion app on the phone

![QR code for this page](https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=https://github.com/michalchecinski/karoo-hass-companion)

</details>

More information about sideloading apps using the Hammerhead Companion app can be found on the [Hammerhead support page](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).

### Karoo 2

1. Download [the latest release APK file](https://github.com/michalchecinski/karoo-hass-companion/releases/latest/download/karoo-hass-companion.apk).
2. Follow [DCRainmaker's detailed guide to sideloading apps on the Hammerhead Karoo](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).

## Development

Build and verify the application with JDK 17:

```bash
./gradlew ktlintCheck check assembleDebug
```

## How can I help?

Submit a [GitHub issue](https://github.com/michalchecinski/karoo-hass-companion/issues) with a feature request or bug report. To help with development, refer to the [contributing guide](CONTRIBUTING.md).

Is this useful to you? [Buy me a coffee](https://ko-fi.com/michalchecinski).

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/michalchecinski)

This project is under active development and is provided without guarantees.

## Credits

- Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) (Apache-2.0 licensed).
- Built with [Jetpack Compose](https://developer.android.com/compose).

This is an unofficial project and is not affiliated with or endorsed by the Open Home Foundation, Home Assistant, or Hammerhead Navigation.

_"Home Assistant" and its logo are trademarks of their respective owners._

_"Hammerhead", "Karoo", "Karoo SDK", "Karoo 2", "Karoo Extensions", and "karoo-ext" may be trademarks, copyrights, or other property of Hammerhead Navigation Inc. or a parent or subsidiary company._

This project is available under the [Apache License 2.0](LICENSE).
