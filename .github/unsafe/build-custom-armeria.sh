#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$(mktemp -d)"
CUSTOM_VERSION="1.33.4-SNAPSHOT"
ORIGINAL_VERSION="1.33.3"

echo "Building armeria-core with UNSAFE disabled..."

cd "${WORK_DIR}"

# Clone Armeria repository
git clone --depth 1 --branch "armeria-${ORIGINAL_VERSION}" https://github.com/line/armeria.git

cd armeria

# Apply patch to remove unsafe access
git apply "${SCRIPT_DIR}/unsafe.patch"

# Update version to snapshot version
sed -i "s/version=${ORIGINAL_VERSION}/version=${CUSTOM_VERSION}/" gradle.properties

# Build the core module
./gradlew :core:jar -x javadoc -x :docs-client:nodeSetup -x :docs-client:npmSetup -x :docs-client:npmInstall -x :docs-client:eslint -x :docs-client:lint -x :docs-client:buildWeb -x :docs-client:copyWeb

# Find the built JAR
CORE_JAR="core/build/libs/armeria-${CUSTOM_VERSION}.jar"
if [ ! -f "$CORE_JAR" ]; then
    echo "Error: Could not find armeria JAR at $CORE_JAR"
    exit 1
fi

# Install core JAR to Maven local repository
mvn install:install-file -q \
    -Dfile="$CORE_JAR" \
    -DgroupId=com.linecorp.armeria \
    -DartifactId=armeria \
    -Dversion="${CUSTOM_VERSION}" \
    -Dpackaging=jar

# Update dependencies
DEPS="${SCRIPT_DIR}/../../dependencyManagement/build.gradle.kts"

# Update only the armeria core artifact version
sed -i '/com\.linecorp\.armeria:armeria:/d' "${DEPS}"
sed -i '/^val DEPENDENCIES = listOf(/a\  "com.linecorp.armeria:armeria:'"${CUSTOM_VERSION}"'",' "${DEPS}"

echo "armeria ${CUSTOM_VERSION} built and installed (UNSAFE disabled)."
