#!/bin/zsh
set -euo pipefail

if (( $# != 3 )); then
  print -u2 "Usage: $0 <adb-serial> <candidate.png> <karoo-hass-capture-task-suffix>"
  exit 64
fi

serial="$1"
output="$2"
expected_avd="$3"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk_root/platform-tools/adb"

if [[ "$serial" != emulator-<-> || "$expected_avd" != karoo-hass-capture-?* ]]; then
  print -u2 "Use an explicit emulator serial and a dedicated karoo-hass-capture-<unique-task-suffix> AVD."
  exit 64
fi
if [[ ! -x "$adb" ]]; then
  print -u2 "adb not found at $adb"
  exit 69
fi

validate_target() {
  local actual_avd resumed_package
  actual_avd="$($adb -s "$serial" emu avd name | tr -d '\r' | sed -n '1p')" || return $?
  if [[ "$actual_avd" != "$expected_avd" ]]; then
    print -u2 "Expected dedicated AVD $expected_avd; found ${actual_avd:-unknown}."
    return 65
  fi
  resumed_package="$($adb -s "$serial" shell dumpsys activity activities | awk '
    /^[[:space:]]*(mResumedActivity|topResumedActivity|ResumedActivity)[=:]/ {
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^[a-zA-Z][a-zA-Z0-9_.]*\//) {
          split($i, component, "/"); print component[1]
        }
      }
    }' | sort -u)" || return $?
  if [[ "$resumed_package" != com.karoohass ]]; then
    print -u2 "Expected com.karoohass in the foreground; found ${resumed_package:-unknown}."
    return 65
  fi
}

dimensions() {
  sips -g pixelWidth -g pixelHeight "$1" | awk '
    /pixelWidth/ { width=$2 } /pixelHeight/ { height=$2 }
    END { print width "x" height }'
}

validate_target || exit $?
size="$($adb -s "$serial" shell wm size | awk '/Physical size/ { print $3 }' | tr -d '\r')"
if [[ "$size" != "480x800" ]]; then
  print -u2 "Expected the 480x800 Karoo profile; found ${size:-unknown}."
  exit 65
fi

mkdir -p "${output:h}"
capture_dir="$(mktemp -d "${output:h}/.karoo-capture.XXXXXXXX")"
trap 'rm -rf "$capture_dir"' EXIT
raw="$capture_dir/raw.png"
candidate="$capture_dir/candidate.png"

$adb -s "$serial" exec-out screencap -p > "$raw"
validate_target || exit $?
if [[ "$(dimensions "$raw")" != "480x800" ]]; then
  print -u2 "Raw capture is not 480x800; existing output was preserved."
  exit 65
fi
sips --cropToHeightWidth 656 480 --cropOffset 48 0 "$raw" --out "$candidate" >/dev/null
if [[ "$(dimensions "$candidate")" != "480x656" ]]; then
  print -u2 "Cropped capture is not 480x656; existing output was preserved."
  exit 65
fi
validate_target || exit $?
mv -f "$candidate" "$output"
print "Wrote validated app-only candidate: $output"
