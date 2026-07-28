# Release process

Karoo Home Assistant Companion releases are built, signed, reviewed, and published by `.github/workflows/release.yml`. Do not create release tags or upload APKs manually.

## Versioning and changelog

Every pull request must have exactly one version label:

- `version:major` for incompatible product or distribution changes;
- `version:minor` for rider-facing features;
- `version:patch` for fixes and dependency maintenance; or
- `version:skip` when no application release is needed.

The highest label since the previous published release determines the next semantic version. The first release is `0.1.0`. PR titles become public changelog entries, while `version:skip`, `skip-changelog`, Renovate, and dependency-only entries are excluded.

Only squash merges are enabled so release automation can associate commits with PR labels reliably.

## Signing identity

GitHub Actions signs release builds using these encrypted repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The signing key is the permanent Android application identity. Keep an encrypted recovery copy of the key and its credentials in separate secure locations. Losing them prevents compatible application updates. Never commit, upload as a workflow artifact, or paste signing material into an issue or pull request.

## Publishing

1. Confirm all intended PRs are merged to protected `main`, CI is green, and release labels are correct.
2. Open the `Release` workflow and choose **Run workflow** from `main`.
3. Choose whether the build is a beta.
4. Review the generated draft release, changelog, signed APK, and proposed version.
5. Approve the protected `production` environment.
6. Confirm the published release contains:
   - `karoo-hass-companion.apk`
   - `manifest.json`
   - `karoo-hass-companion-icon.png`
7. Verify the APK SHA-256 matches `manifest.json` and install the release on a physical Karoo.

The workflow uses the GitHub run number as Android `versionCode`. It will not publish from a branch other than `main`, and a rerun updates the same draft instead of incrementing from an unpublished tag.

Installed builds discover updates through the latest stable GitHub release manifest. Beta releases are available for manual testing but do not form a separate automatic-update channel; beta installations receive an update when a newer stable release is published.
