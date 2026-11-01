#!/usr/bin/env bash
# Restore the head unit to stock: removes what this repo installs (Home CyberZec,
# DFSK Helper, Italian overlays) and puts the stock Home back. Touches nothing else.
# Usage: ./restore_stock.sh [-s <serial>] [--yes] [-h]
set -u

usage() {
  cat <<'USAGE'
Usage: restore_stock.sh [-s <serial>] [--yes]

  (no option)   DRY RUN: list what from this repo is installed and what would be done.
  --yes         Really do it (no questions asked).
  -s <serial>   adb device serial / ip:port (or set ADB_SERIAL).
  -h, --help    Show this help.

Removes: it.dfsk.overlay.* (Italian overlays), it.cyberzec.home, it.dfsk.helper.
Sets the home back to com.android.launcher3/.Launcher.
Other apps (e.g. E5 Launcher Plus) are NOT touched.
USAGE
}

SERIAL="${ADB_SERIAL:-}"
DO=0
while [ $# -gt 0 ]; do
  case "$1" in
    -s) [ $# -ge 2 ] || { usage; exit 2; }; SERIAL="$2"; shift ;;
    --yes) DO=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1"; usage; exit 2 ;;
  esac
  shift
done

command -v adb >/dev/null 2>&1 || { echo "ERROR: adb not found. Install Android platform-tools first."; exit 1; }

ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

if [ -z "$SERIAL" ]; then
  n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  if [ "$n" -eq 0 ]; then echo "ERROR: no device connected (try: adb connect <ip>:5555, or use -s)."; exit 1; fi
  if [ "$n" -gt 1 ]; then echo "ERROR: $n devices connected, choose one with -s <serial>."; exit 1; fi
fi
[ "$("${ADB[@]}" get-state 2>/dev/null)" = "device" ] || { echo "ERROR: device not reachable${SERIAL:+ ($SERIAL)}."; exit 1; }

STOCK_HOME="com.android.launcher3/.Launcher"
APPS="it.cyberzec.home it.dfsk.helper"

pkgs=$("${ADB[@]}" shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://')
has() { printf '%s\n' "$pkgs" | grep -qx "$1"; }

overlays=$(printf '%s\n' "$pkgs" | grep '^it\.dfsk\.overlay\.' || true)
apps=""
for p in $APPS; do has "$p" && apps="$apps $p"; done
apps=${apps# }

cur_home=$("${ADB[@]}" shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | tr -d '\r' | tail -n1)
set_home=0
if has it.cyberzec.home || [ "${cur_home%%/*}" = "it.cyberzec.home" ]; then set_home=1; fi

if [ -z "$overlays" ] && [ -z "$apps" ] && [ $set_home -eq 0 ]; then
  echo "Nothing from this repo is installed: the unit is already stock."
  exit 0
fi

echo "Found on the unit (current home: ${cur_home:-unknown}):"
[ $set_home -eq 1 ] && echo "  - home to reset -> $STOCK_HOME"
for o in $overlays; do echo "  - overlay  $o"; done
for a in $apps; do echo "  - app      $a"; done

if [ $DO -eq 0 ]; then
  echo
  echo "DRY RUN: nothing changed. Would do, in order:"
  [ $set_home -eq 1 ] && echo "  - set home to $STOCK_HOME"
  [ -n "$overlays" ] && echo "  - disable + uninstall each overlay listed above"
  [ -n "$apps" ] && echo "  - uninstall: $apps"
  echo "  - verify nothing is left"
  echo "Run again with --yes to apply."
  exit 0
fi

# Every step tolerates "not installed": errors are ignored on purpose.
if [ $set_home -eq 1 ]; then
  echo "Setting home to $STOCK_HOME"
  "${ADB[@]}" shell cmd package set-home-activity "$STOCK_HOME" >/dev/null 2>&1
fi
for o in $overlays; do
  echo "Removing overlay $o"
  "${ADB[@]}" shell cmd overlay disable "$o" >/dev/null 2>&1
  "${ADB[@]}" uninstall "$o" >/dev/null 2>&1
done
for a in $apps; do
  echo "Uninstalling $a"
  "${ADB[@]}" uninstall "$a" >/dev/null 2>&1
done

# Verify
left=$("${ADB[@]}" shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://' | grep -E '^(it\.dfsk\.overlay\..*|it\.cyberzec\.home|it\.dfsk\.helper)$' || true)
echo
if [ -z "$left" ]; then
  echo "OK: nothing from this repo is installed anymore."
  rc=0
else
  echo "NOT DONE, still installed:"
  printf '  %s\n' $left
  echo "Retry, or remove by hand: adb uninstall <package>"
  rc=1
fi
echo
echo "Final step: restart the infotainment (or turn the car off and on) so the stock Home"
echo "reloads its original texts. That is the state to show the dealership."
exit $rc
