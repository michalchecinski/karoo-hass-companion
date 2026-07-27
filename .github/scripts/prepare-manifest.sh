#!/bin/sh

set -eu

if [ "$#" -ne 4 ]; then
    echo "Usage: $0 VERSION VERSION_CODE RELEASE_NOTES_FILE APK_SHA256" >&2
    exit 2
fi

version=$1
version_code=$2
release_notes_file=$3
apk_sha256=$4

if ! printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-beta[0-9]*)?$'; then
    echo "Invalid release version: $version" >&2
    exit 2
fi

case "$version_code" in
    '' | *[!0-9]*)
        echo "Version code must be a positive integer." >&2
        exit 2
        ;;
esac

if [ "$version_code" -lt 1 ]; then
    echo "Version code must be a positive integer." >&2
    exit 2
fi

case "$apk_sha256" in
    *[!0-9a-fA-F]*)
        echo "APK checksum must be hexadecimal." >&2
        exit 2
        ;;
esac

if [ "${#apk_sha256}" -ne 64 ]; then
    echo "APK checksum must contain 64 hexadecimal characters." >&2
    exit 2
fi

if [ ! -f "$release_notes_file" ]; then
    echo "Release notes file not found: $release_notes_file" >&2
    exit 2
fi

temporary_manifest=$(mktemp "${TMPDIR:-/tmp}/karoo-hass-manifest.XXXXXX")
trap 'rm -f "$temporary_manifest"' EXIT HUP INT TERM

jq \
    --arg version "$version" \
    --argjson version_code "$version_code" \
    --arg apk_sha256 "$(printf '%s' "$apk_sha256" | tr '[:upper:]' '[:lower:]')" \
    --rawfile release_notes "$release_notes_file" \
    '
      walk(
        if type == "string" then
          gsub("__VERSION_NUMBER_PLACEHOLDER__"; $version)
          | gsub("__APK_SHA256_PLACEHOLDER__"; $apk_sha256)
          | gsub("__RELEASE_NOTES_PLACEHOLDER__"; $release_notes)
        else
          .
        end
      )
      | .latestVersionCode = $version_code
    ' \
    manifest-template.json > "$temporary_manifest"

jq --exit-status 'type == "object"' "$temporary_manifest" > /dev/null
mv "$temporary_manifest" manifest.json
trap - EXIT HUP INT TERM

echo "Prepared manifest.json for $version."
