#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$(mktemp -d)"
CUSTOM_VERSION="4.32.2-SNAPSHOT"
ORIGINAL_VERSION="4.32.1"

echo "Building protobuf-java with UNSAFE disabled..."

cd "${WORK_DIR}"

# Download original artifact and extract
curl -sL -o "protobuf-java-${ORIGINAL_VERSION}.jar" "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/${ORIGINAL_VERSION}/protobuf-java-${ORIGINAL_VERSION}.jar"

# Extract JAR
mkdir classes
cd classes
jar -xf "../protobuf-java-${ORIGINAL_VERSION}.jar"
cd ..

# Clone protobuf repository
git clone --depth 1 --branch "v32.1" https://github.com/protocolbuffers/protobuf.git

# Apply patch to remove unsafe access
sed -i 's/private static final sun\.misc\.Unsafe UNSAFE = getUnsafe();/private static final sun.misc.Unsafe UNSAFE = null;/' protobuf/java/core/src/main/java/com/google/protobuf/UnsafeUtil.java

# Compile modified classes
javac -cp protobuf-java-${ORIGINAL_VERSION}.jar -d classes protobuf/java/core/src/main/java/com/google/protobuf/UnsafeUtil.java

# Update version and create new JAR
sed -i "s/Implementation-Version: ${ORIGINAL_VERSION}/Implementation-Version: ${CUSTOM_VERSION}/" classes/META-INF/MANIFEST.MF
find classes/META-INF -name "pom.properties" -exec sed -i "s/version=${ORIGINAL_VERSION}/version=${CUSTOM_VERSION}/" {} \;
jar -cf "protobuf-java-${CUSTOM_VERSION}.jar" -C classes .

# Install to Maven local repository
mvn install:install-file -q \
    -Dfile="protobuf-java-${CUSTOM_VERSION}.jar" \
    -DgroupId=com.google.protobuf \
    -DartifactId=protobuf-java \
    -Dversion="${CUSTOM_VERSION}" \
    -Dpackaging=jar

# Update dependencies
DEPS="${SCRIPT_DIR}/../../dependencyManagement/build.gradle.kts"
sed -i '/com\.google\.protobuf:protobuf-bom:/d' "${DEPS}"
sed -i '/com\.google\.protobuf:protobuf-java/d' "${DEPS}"
sed -i '/^val DEPENDENCIES = listOf(/a\  "com.google.protobuf:protobuf-java:'"${CUSTOM_VERSION}"'",' "${DEPS}"
sed -i '/^val DEPENDENCIES = listOf(/a\  "com.google.protobuf:protobuf-java-util:${ORIGINAL_VERSION}",' "${DEPS}"

echo "protobuf-java ${CUSTOM_VERSION} built and installed (UNSAFE disabled)."
