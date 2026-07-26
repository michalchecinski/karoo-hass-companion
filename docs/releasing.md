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

The release key is stored outside the repository at:

```text
~/.android/keystores/karoo-hass-companion.jks
```

Its password is stored in macOS Keychain under the `karoo-hass-companion-release` service. GitHub Actions receives the same material through these encrypted repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The key is the permanent Android application identity. Back up the encrypted JKS and its password separately before the first public release. Losing either prevents compatible application updates. Never commit, upload as a workflow artifact, or paste the key material into an issue or pull request.

Certificate SHA-256 fingerprint:

```text
C1:EB:70:90:AE:A8:09:A8:1D:D3:C5:70:E3:24:BF:88:FD:4C:9C:6A:33:95:48:CF:E8:03:34:A6:DF:FB:AF:A2
```

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

## Recovery and rollback

- A failed or cancelled run may leave a draft release. Correct the problem and rerun the workflow; do not create a replacement tag manually.
- Do not reuse a published version or version code.
- Android releases cannot be rolled back over a newer installed version. Publish a new patch release with the correction.
- Restore signing credentials only from the encrypted recovery copy and verify the certificate fingerprint above before publishing.
