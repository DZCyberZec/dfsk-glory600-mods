#!/bin/bash
# Starts the fake car (real web panel code, fake backend) on port 8091.
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
H=../../helper/src/it/dfsk/helper
rm -rf build && mkdir build
"$JAVA_HOME/bin/javac" -nowarn -d build $H/{WebAuth,WebPanel,Lang}.java FakeCar.java
exec "$JAVA_HOME/bin/java" -cp build FakeCar
