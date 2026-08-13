#!/bin/zsh
set -euo pipefail

if (( $# != 2 )); then
  print -u2 "Usage: $0 <adb-serial> <output.png>"
  exit 64
fi

serial="$1"
output="$2"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk_root/platform-tools/adb"

if [[ ! -x "$adb" ]]; then
  print -u2 "adb not found at $adb"
  exit 69
fi

size="$($adb -s "$serial" shell wm size | awk '/Physical size/ { print $3 }')"
if [[ "$size" != "480x800" ]]; then
  print -u2 "Expected the 480x800 Karoo AVD; found ${size:-unknown}."
  exit 65
fi

mkdir -p "${output:h}"
raw="${TMPDIR:-/tmp}/karoo-screenshot-${serial}.png"
trap 'rm -f "$raw"' EXIT

$adb -s "$serial" exec-out screencap -p > "$raw"
sips --cropToHeightWidth 656 480 --cropOffset 48 0 "$raw" --out "$output" >/dev/null

dimensions="$(sips -g pixelWidth -g pixelHeight "$output" | awk '/pixelWidth/ { width=$2 } /pixelHeight/ { height=$2 } END { print width "x" height }')"
if [[ "$dimensions" != "480x656" ]]; then
  print -u2 "Unexpected output dimensions: $dimensions"
  exit 65
fi

print "Wrote app-only screenshot: $output"
