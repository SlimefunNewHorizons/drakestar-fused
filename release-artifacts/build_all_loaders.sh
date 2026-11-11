#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VERSION="1.0.0-alpha.1"
MC_VER="1.21.1"

echo "=================================================================="
echo "⚡ DrakeStar Fused Multi-Loader Artifact Builder v${VERSION} (${MC_VER})"
echo "=================================================================="

# Helper para asegurar JAR
ensure_jar() {
    local target="$1"
    local url="$2"
    if [ ! -f "$target" ]; then
        mkdir -p "$(dirname "$target")"
        echo "⬇️ Descargando $(basename "$target")..."
        curl -sL "$url" -o "$target"
    fi
}

DEPS_DIR="$REPO_ROOT/.deps"
mkdir -p "$DEPS_DIR/common" "$DEPS_DIR/fabric" "$DEPS_DIR/neoforge" "$DEPS_DIR/forge" "$DEPS_DIR/stubs/net/minecraftforge/fml/common"

# 1. Resolver dependencias
SLF4J_CACHE="/home/jack/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar"
if [ -f "$SLF4J_CACHE" ]; then
    SLF4J_JAR="$SLF4J_CACHE"
else
    SLF4J_JAR="$DEPS_DIR/common/slf4j-api-2.0.17.jar"
    ensure_jar "$SLF4J_JAR" "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar"
fi

MIXIN_CACHE="/home/jack/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin/0.16.5+mixin.0.8.7/80fc3a9f592673cea87f4cd702f87991c6c9fe4d/sponge-mixin-0.16.5+mixin.0.8.7.jar"
if [ -f "$MIXIN_CACHE" ]; then
    MIXIN_JAR="$MIXIN_CACHE"
else
    MIXIN_JAR="$DEPS_DIR/fabric/sponge-mixin-0.16.5+mixin.0.8.7.jar"
    ensure_jar "$MIXIN_JAR" "https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.16.5+mixin.0.8.7/sponge-mixin-0.16.5+mixin.0.8.7.jar"
fi

ASM_CACHE="/home/jack/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-tree/9.9.1/b6b1b3366296163b4b1f540731aad0a2baa484d8/asm-tree-9.9.1.jar"
if [ -f "$ASM_CACHE" ]; then
    ASM_JAR="$ASM_CACHE"
else
    ASM_JAR="$DEPS_DIR/fabric/asm-tree-9.9.1.jar"
    ensure_jar "$ASM_JAR" "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar"
fi

NEO_CACHE="/home/jack/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.11.48/8097c6382d38648441484f58e1e95f9ac838ac18/neoforge-21.11.48-universal.jar"
if [ -f "$NEO_CACHE" ]; then
    NEO_JAR="$NEO_CACHE"
else
    NEO_JAR="$DEPS_DIR/neoforge/neoforge-21.11.48-universal.jar"
    ensure_jar "$NEO_JAR" "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.11.48/neoforge-21.11.48-universal.jar"
fi

BUS_CACHE="/home/jack/.gradle/caches/modules-2/files-2.1/net.neoforged/bus/8.0.5/5b2d33285ab5d1554e9798ad98c40d6ea3868bd5/bus-8.0.5.jar"
if [ -f "$BUS_CACHE" ]; then
    BUS_JAR="$BUS_CACHE"
else
    BUS_JAR="$DEPS_DIR/neoforge/bus-8.0.5.jar"
    ensure_jar "$BUS_JAR" "https://maven.neoforged.net/releases/net/neoforged/bus/8.0.5/bus-8.0.5.jar"
fi

FML_CACHE="/home/jack/.gradle/caches/modules-2/files-2.1/net.neoforged.fancymodloader/loader/10.0.36/f72ab4d3e755fe736829883d90430a281527aca/loader-10.0.36.jar"
if [ -f "$FML_CACHE" ]; then
    FML_JAR="$FML_CACHE"
else
    FML_JAR="$DEPS_DIR/neoforge/loader-10.0.36.jar"
    ensure_jar "$FML_JAR" "https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/10.0.36/loader-10.0.36.jar"
fi

FORGE_UNI_CACHE="/home/jack/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-universal.jar"
if [ -f "$FORGE_UNI_CACHE" ]; then
    FORGE_UNI="$FORGE_UNI_CACHE"
else
    FORGE_UNI="$DEPS_DIR/forge/forge-1.20.1-47.4.0-universal.jar"
    ensure_jar "$FORGE_UNI" "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-universal.jar"
fi

FORGE_FML="$DEPS_DIR/forge/fmlcore-1.20.1-47.4.0.jar"
ensure_jar "$FORGE_FML" "https://maven.minecraftforge.net/net/minecraftforge/fmlcore/1.20.1-47.4.0/fmlcore-1.20.1-47.4.0.jar"

FORGE_BUS="$DEPS_DIR/forge/eventbus-6.0.0.jar"
ensure_jar "$FORGE_BUS" "https://maven.minecraftforge.net/net/minecraftforge/eventbus/6.0.0/eventbus-6.0.0.jar"

# Asegurar dependencias de Forge si no existen
mkdir -p .deps/forge .deps/stubs/net/minecraftforge/fml/common
if [ ! -f "$FORGE_FML" ]; then
    echo "⬇️ Descargando fmlcore para Forge..."
    curl -sL "https://maven.minecraftforge.net/net/minecraftforge/fmlcore/1.20.1-47.4.0/fmlcore-1.20.1-47.4.0.jar" -o "$FORGE_FML"
fi
if [ ! -f "$FORGE_BUS" ]; then
    echo "⬇️ Descargando eventbus para Forge..."
    curl -sL "https://maven.minecraftforge.net/net/minecraftforge/eventbus/6.0.0/eventbus-6.0.0.jar" -o "$FORGE_BUS"
fi

# Generar stub de anotación @Mod para Forge si no existe
if [ ! -f ".deps/stubs/net/minecraftforge/fml/common/Mod.class" ]; then
    cat << 'EOF' > .deps/stubs/net/minecraftforge/fml/common/Mod.java
package net.minecraftforge.fml.common;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}
EOF
    javac -d .deps/stubs .deps/stubs/net/minecraftforge/fml/common/Mod.java
fi

# 2. Compilar módulo común
echo "📦 Compilando módulo 'common'..."
mkdir -p common/build/classes/java/main
javac -proc:none -encoding UTF-8 -cp "$SLF4J_JAR" -d common/build/classes/java/main $(find common/src/main/java -name "*.java")

# 3. Compilar módulo Fabric
echo "🧵 Compilando módulo 'fabric'..."
mkdir -p fabric/build/classes/java/main
CP_FABRIC="common/build/classes/java/main:$SLF4J_JAR:$MIXIN_JAR:$ASM_JAR"
javac -proc:none -encoding UTF-8 -cp "$CP_FABRIC" -d fabric/build/classes/java/main $(find fabric/src/main/java -name "*.java")

# 4. Compilar módulo NeoForge
echo "⚡ Compilando módulo 'neoforge'..."
mkdir -p neoforge/build/classes/java/main
CP_NEO="common/build/classes/java/main:$SLF4J_JAR:$NEO_JAR:$BUS_JAR:$FML_JAR"
javac -proc:none -encoding UTF-8 -cp "$CP_NEO" -d neoforge/build/classes/java/main $(find neoforge/src/main/java -name "*.java")

# 5. Compilar módulo Forge Clásico
echo "🔨 Compilando módulo 'forge'..."
mkdir -p forge/build/classes/java/main
CP_FORGE="common/build/classes/java/main:.deps/stubs:$SLF4J_JAR:$FORGE_UNI:$FORGE_FML:$FORGE_BUS"
javac -proc:none -encoding UTF-8 -cp "$CP_FORGE" -d forge/build/classes/java/main $(find forge/src/main/java -name "*.java")

# 6. Preparar directorios de staging y empaquetado de JARs
mkdir -p dist

# Staging Fabric
echo "📦 Empaquetando JAR para Fabric Loader..."
STAGING_FABRIC="/tmp/drakestar-fused-staging-fabric"
rm -rf "$STAGING_FABRIC" && mkdir -p "$STAGING_FABRIC"
cp -r common/build/classes/java/main/* "$STAGING_FABRIC/"
cp -r fabric/build/classes/java/main/* "$STAGING_FABRIC/"
cp -r fabric/src/main/resources/* "$STAGING_FABRIC/"
mkdir -p "$STAGING_FABRIC/assets"
cp assets/drakestar-fused-icon.png "$STAGING_FABRIC/assets/"
jar cf "dist/drakestar-fused-fabric-${MC_VER}-${VERSION}.jar" -C "$STAGING_FABRIC" .

# Staging NeoForge
echo "📦 Empaquetando JAR para NeoForge..."
STAGING_NEO="/tmp/drakestar-fused-staging-neoforge"
rm -rf "$STAGING_NEO" && mkdir -p "$STAGING_NEO/META-INF"
cp -r common/build/classes/java/main/* "$STAGING_NEO/"
cp -r neoforge/build/classes/java/main/* "$STAGING_NEO/"
cp neoforge/src/main/resources/neoforge.mods.toml "$STAGING_NEO/META-INF/"
cp neoforge/src/main/resources/neoforge.mods.toml "$STAGING_NEO/"
mkdir -p "$STAGING_NEO/assets"
cp assets/drakestar-fused-icon.png "$STAGING_NEO/assets/"
jar cf "dist/drakestar-fused-neoforge-${MC_VER}-${VERSION}.jar" -C "$STAGING_NEO" .

# Staging Forge
echo "📦 Empaquetando JAR para Forge..."
STAGING_FORGE="/tmp/drakestar-fused-staging-forge"
rm -rf "$STAGING_FORGE" && mkdir -p "$STAGING_FORGE/META-INF"
cp -r common/build/classes/java/main/* "$STAGING_FORGE/"
cp -r forge/build/classes/java/main/* "$STAGING_FORGE/"
cp forge/src/main/resources/mods.toml "$STAGING_FORGE/META-INF/"
cp forge/src/main/resources/mods.toml "$STAGING_FORGE/"
mkdir -p "$STAGING_FORGE/assets"
cp assets/drakestar-fused-icon.png "$STAGING_FORGE/assets/"
jar cf "dist/drakestar-fused-forge-${MC_VER}-${VERSION}.jar" -C "$STAGING_FORGE" .

echo "=================================================================="
echo "✅ Todos los artefactos multi-loader compilados y empaquetados en dist/:"
echo "=================================================================="
ls -lh dist/
sha256sum dist/*.jar
