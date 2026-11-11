#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VERSION="1.0.0-alpha.1"
MC_VER="1.21.1"

echo "=================================================================="
echo "⚡ DrakeStar Fused Multi-Loader Artifact Builder v${VERSION} (${MC_VER})"
echo "=================================================================="

# 1. Resolver librerías de compilación
SLF4J_JAR="/home/jack/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar"
MIXIN_JAR="/home/jack/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin/0.16.5+mixin.0.8.7/80fc3a9f592673cea87f4cd702f87991c6c9fe4d/sponge-mixin-0.16.5+mixin.0.8.7.jar"
ASM_JAR="/home/jack/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-tree/9.9.1/b6b1b3366296163b4b1f540731aad0a2baa484d8/asm-tree-9.9.1.jar"

NEO_JAR="/home/jack/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.11.48/8097c6382d38648441484f58e1e95f9ac838ac18/neoforge-21.11.48-universal.jar"
BUS_JAR="/home/jack/.gradle/caches/modules-2/files-2.1/net.neoforged/bus/8.0.5/5b2d33285ab5d1554e9798ad98c40d6ea3868bd5/bus-8.0.5.jar"
FML_JAR="/home/jack/.gradle/caches/modules-2/files-2.1/net.neoforged.fancymodloader/loader/10.0.36/f72ab4d3e755fe736829883d90430a281527aca/loader-10.0.36.jar"

FORGE_UNI="/home/jack/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-universal.jar"
FORGE_FML=".deps/forge/fmlcore-1.20.1-47.4.0.jar"
FORGE_BUS=".deps/forge/eventbus-6.0.0.jar"

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
