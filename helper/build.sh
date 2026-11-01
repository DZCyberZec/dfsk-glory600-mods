#!/bin/bash
# Build DFSK-Helper without Gradle. Requires: build-tools 35.0.0, platforms;android-28, JDK.
#   ./build.sh            -> DFSK-Helper.apk         (user app, own debug key, v1.1, versionCode 2)
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-28/android.jar
OUT=build
rm -rf $OUT && mkdir -p $OUT/classes $OUT/testclasses $OUT/res

# 1. JVM self-checks of the pure-Java logic
"$JAVA_HOME/bin/javac" --release 8 -nowarn -Xlint:-options -d $OUT/testclasses \
    src/it/dfsk/helper/{CallState,ReverseState,WebAuth,WebPanel,QrEncoder,Lang}.java test/*.java
for t in CallStateTest ReverseStateTest WebTest; do "$JAVA_HOME/bin/java" -ea -cp $OUT/testclasses $t; done

# 2. resources
MANIFEST=AndroidManifest.xml
$BT/aapt2 compile --dir res -o $OUT/res.zip
$BT/aapt2 link -o $OUT/unsigned.apk -I "$JAR" --manifest $MANIFEST \
    --min-sdk-version 21 --target-sdk-version 28 $OUT/res.zip

# 3. code
"$JAVA_HOME/bin/javac" --release 8 -nowarn -Xlint:-options -classpath "$JAR" -d $OUT/classes $(find src -name '*.java')
$BT/d8 --release --min-api 21 --lib "$JAR" --output $OUT $(find $OUT/classes -name '*.class')
(cd $OUT && zip -q unsigned.apk classes.dex)

# 4. align + sign
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
APK=DFSK-Helper.apk
# Release builds: KEYSTORE=/path/to.jks KS_PASS=... KS_ALIAS=... ./build.sh (default: local throwaway key)
KS=${KEYSTORE:-debug.keystore}; KS_PASS=${KS_PASS:-android}; KS_ALIAS=${KS_ALIAS:-dfskhelper}; export KS_PASS
[ -f $KS ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore $KS -storepass android -keypass android \
    -alias dfskhelper -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=DFSK Helper" -noprompt
$BT/apksigner sign --ks $KS --ks-pass env:KS_PASS --ks-key-alias $KS_ALIAS --out $APK $OUT/aligned.apk
$BT/apksigner verify --print-certs $APK | grep "SHA-256"
$BT/aapt2 dump badging $APK | head -1
echo "OK -> $(pwd)/$APK"
