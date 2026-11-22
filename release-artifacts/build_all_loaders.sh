#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VERSION="$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '\r')"
if [ -z "$VERSION" ]; then
    VERSION="1.0.0-alpha.3"
fi
MC_VER="1.21.1"

GRADLE_BIN="$(command -v gradle || true)"
if [ -z "$GRADLE_BIN" ]; then
    for candidate in \
        "${HOME}/.gradle/wrapper/dists"/gradle-*/3m7h6ceboy5k31n8kzwzuxssm/gradle-*/bin/gradle \
        "${HOME}/.gradle/wrapper/dists"/gradle-*/*/gradle-*/bin/gradle \
        /home/jack/.gradle/wrapper/dists/gradle-*/3m7h6ceboy5k31n8kzwzuxssm/gradle-*/bin/gradle \
        /home/jack/.gradle/wrapper/dists/gradle-*/*/gradle-*/bin/gradle; do
        if [ -x "$candidate" ]; then
            GRADLE_BIN="$candidate"
            break
        fi
    done
fi

echo "=================================================================="
echo "⚡ DrakeStar Fused Multi-Loader Artifact Builder v${VERSION} (${MC_VER})"
echo "=================================================================="

if [ -n "$GRADLE_BIN" ]; then
    echo "🔧 Usando Gradle: $GRADLE_BIN"
    GRADLE_USER_HOME="${GRADLE_USER_HOME:-/home/jack/.gradle}" "$GRADLE_BIN" --no-daemon :fabric:build :neoforge:build :forge:build
else
    echo "⚠️ Binario de Gradle no encontrado; compilando con javac --release 17..."
    SLF4J_JAR=""
    for candidate in \
        "${HOME}/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api"/2.*/*/slf4j-api-2.*.jar \
        /home/jack/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.*/*/slf4j-api-2.*.jar \
        "$REPO_ROOT/.deps/common"/slf4j-api-*.jar; do
        if [ -f "$candidate" ]; then
            SLF4J_JAR="$candidate"
            break
        fi
    done
    if [ -z "$SLF4J_JAR" ]; then
        mkdir -p "$REPO_ROOT/.deps/common"
        SLF4J_JAR="$REPO_ROOT/.deps/common/slf4j-api-2.0.17.jar"
        curl -sL "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar" -o "$SLF4J_JAR"
    fi
    mkdir -p common/build/classes/java/main
    javac --release 17 -proc:none -encoding UTF-8 -cp "$SLF4J_JAR" -d common/build/classes/java/main $(find common/src/main/java -name "*.java")
    for loader in fabric neoforge forge; do
        mkdir -p "${loader}/build/classes/java/main" "${loader}/build/libs"
        javac --release 17 -proc:none -encoding UTF-8 -cp "common/build/classes/java/main:$SLF4J_JAR" \
            -d "${loader}/build/classes/java/main" \
            $(find "${loader}/src/main/java" "${loader}/stubs" "fabric/stubs/net/minecraft" -name "*.java" 2>/dev/null)
        rm -rf "${loader}/build/classes/java/main/net"
        STAGING="/tmp/drakestar-fused-staging-${loader}"
        rm -rf "$STAGING" && mkdir -p "$STAGING/META-INF" "$STAGING/assets"
        cp -r common/build/classes/java/main/* "$STAGING/"
        cp -r "${loader}/build/classes/java/main/"* "$STAGING/"
        cp assets/drakestar-fused-icon.png "$STAGING/assets/"
        cp LICENSE "$STAGING/LICENSE_drakestar-fused-${loader}-universal"
        if [ "$loader" = "fabric" ]; then
            sed "s/\${version}/${VERSION}/g" fabric/src/main/resources/fabric.mod.json > "$STAGING/fabric.mod.json"
        elif [ "$loader" = "neoforge" ]; then
            sed "s/\${version}/${VERSION}/g" neoforge/src/main/resources/neoforge.mods.toml > "$STAGING/META-INF/neoforge.mods.toml"
            cp "$STAGING/META-INF/neoforge.mods.toml" "$STAGING/neoforge.mods.toml"
        else
            sed "s/\${version}/${VERSION}/g" forge/src/main/resources/mods.toml > "$STAGING/META-INF/mods.toml"
            cp "$STAGING/META-INF/mods.toml" "$STAGING/mods.toml"
        fi
        jar cf "${loader}/build/libs/drakestar-fused-${loader}-universal-${VERSION}.jar" -C "$STAGING" .
    done
fi

mkdir -p dist
cp "fabric/build/libs/drakestar-fused-fabric-universal-${VERSION}.jar" "dist/drakestar-fused-fabric-${MC_VER}-${VERSION}.jar"
cp "neoforge/build/libs/drakestar-fused-neoforge-universal-${VERSION}.jar" "dist/drakestar-fused-neoforge-${MC_VER}-${VERSION}.jar"
cp "forge/build/libs/drakestar-fused-forge-universal-${VERSION}.jar" "dist/drakestar-fused-forge-${MC_VER}-${VERSION}.jar"

python3 release-artifacts/check_jar.py \
    "fabric/build/libs/drakestar-fused-fabric-universal-${VERSION}.jar" \
    "neoforge/build/libs/drakestar-fused-neoforge-universal-${VERSION}.jar" \
    "forge/build/libs/drakestar-fused-forge-universal-${VERSION}.jar" \
    dist/*.jar

echo "=================================================================="
echo "✅ Todos los artefactos multi-loader compilados y empaquetados en dist/:"
echo "=================================================================="
ls -lh fabric/build/libs/ neoforge/build/libs/ forge/build/libs/ dist/
sha256sum fabric/build/libs/drakestar-fused-*-universal-*.jar neoforge/build/libs/drakestar-fused-*-universal-*.jar forge/build/libs/drakestar-fused-*-universal-*.jar dist/*.jar
