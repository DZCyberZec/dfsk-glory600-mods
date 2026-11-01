#!/bin/bash
# Build Home CyberZec without Gradle (same steps as helper_app/build.sh).
# Requires: build-tools 35.0.0, platforms;android-28, JDK. Signed with your own key (created on first run).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-28/android.jar
OUT=build
APK=HomeCyberZec.apk
rm -rf $OUT && mkdir -p $OUT/classes $OUT/testclasses

# 1. JVM self-check of the pure-Java logic
"$JAVA_HOME/bin/javac" --release 8 -nowarn -Xlint:-options -d $OUT/testclasses src/it/cyberzec/home/Logic.java test/LogicTest.java
"$JAVA_HOME/bin/java" -ea -cp $OUT/testclasses it.cyberzec.home.LogicTest

# 2. resources (R.java needed by the code)
$BT/aapt2 compile --dir res -o $OUT/res.zip
$BT/aapt2 link -o $OUT/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 21 --target-sdk-version 28 --java $OUT/gen $OUT/res.zip

# 3. code
"$JAVA_HOME/bin/javac" --release 8 -nowarn -Xlint:-options -classpath "$JAR" -d $OUT/classes \
    $(find src $OUT/gen -name '*.java')
$BT/d8 --release --min-api 21 --lib "$JAR" --output $OUT $(find $OUT/classes -name '*.class')
(cd $OUT && zip -q unsigned.apk classes.dex)

# 4. align + sign
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
# Release builds: KEYSTORE=/path/to.jks KS_PASS=... KS_ALIAS=... ./build.sh (default: local throwaway key)
KS=${KEYSTORE:-debug.keystore}; KS_PASS=${KS_PASS:-android}; KS_ALIAS=${KS_ALIAS:-cyberzec}; export KS_PASS
[ -f $KS ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore $KS -storepass android -keypass android \
    -alias cyberzec -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Home CyberZec" -noprompt
$BT/apksigner sign --ks $KS --ks-pass env:KS_PASS --ks-key-alias $KS_ALIAS --out $APK $OUT/aligned.apk
$BT/apksigner verify --print-certs $APK | grep "SHA-256"
$BT/aapt2 dump badging $APK | head -1
echo "OK -> $(pwd)/$APK"
