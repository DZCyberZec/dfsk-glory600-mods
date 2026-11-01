#!/bin/bash
# Builds one Italian RRO overlay per app listed in targets.txt -> out/<App>_it_overlay.apk
# Requires: build-tools 35.0.0, JDK, and the car's framework-res.apk (not included, it is DFSK/BW's file):
#   adb pull /system/framework/framework-res.apk translations/framework-res.apk
# Signed with your own key (created on first run). Not yet tested on the car with a non-platform key.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
BT=${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/35.0.0
FW=framework-res.apk; [ -f $FW ] || { echo "missing $FW (see header)"; exit 1; }
KS=overlay.keystore
[ -f $KS ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore $KS -storepass android -keypass android \
    -alias overlay -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=DFSK IT overlay" -noprompt
mkdir -p out
grep -v '^#' targets.txt | while read -r A T LOCALES; do
    [ -n "$A" ] || continue
    W=out/build_$A; rm -rf $W; mkdir -p $W/res/values-it
    cp $A/values-it/strings.xml $A/values-it/plurals.xml $W/res/values-it/ 2>/dev/null || true
    # arrays that reference the target's @string cannot be linked: keep only plain-text ones
    [ -f $A/values-it/arrays.xml ] && python3 -I - $A/values-it/arrays.xml $W/res/values-it/arrays.xml <<'PY'
import sys, xml.etree.ElementTree as ET
r = ET.parse(sys.argv[1]).getroot()
for a in list(r):
    if any((i.text or '').startswith(('@', '?')) for i in a): r.remove(a)
if len(r): ET.ElementTree(r).write(sys.argv[2], encoding='utf-8', xml_declaration=True)
PY
    for L in $LOCALES; do mkdir -p $W/res/values-$L; cp $W/res/values-it/*.xml $W/res/values-$L/; done
    cat > $W/AndroidManifest.xml <<M
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="it.dfsk.overlay.$(echo $A | tr 'A-Z' 'a-z')" android:versionCode="1" android:versionName="1.0">
  <overlay android:targetPackage="$T" android:priority="10"/>
  <application android:hasCode="false"/>
</manifest>
M
    $BT/aapt2 compile --dir $W/res -o $W/res.zip
    $BT/aapt2 link -o $W/u.apk -I $FW --manifest $W/AndroidManifest.xml --min-sdk-version 28 --target-sdk-version 28 --no-resource-removal $W/res.zip
    $BT/zipalign -f -p 4 $W/u.apk $W/a.apk
    $BT/apksigner sign --ks $KS --ks-pass pass:android --out out/${A}_it_overlay.apk $W/a.apk
    echo "OK out/${A}_it_overlay.apk -> $T"
done
