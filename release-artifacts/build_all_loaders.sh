#!/usr/bin/env bash
set -e

VERSION="1.0.0-alpha.1"
MC_VER="1.21.1"

echo "Building multi-loader artifacts for DrakeStar Fused v${VERSION} (MC ${MC_VER})..."

mkdir -p dist

# 1. Fabric Loader JAR
jar cf dist/drakestar-fused-fabric-${MC_VER}-${VERSION}.jar \
  -C fabric/src/main/resources . \
  -C common/src/main/java .

# 2. NeoForge Loader JAR
jar cf dist/drakestar-fused-neoforge-${MC_VER}-${VERSION}.jar \
  -C neoforge/src/main/resources . \
  -C common/src/main/java .

# 3. Classic Forge JAR
jar cf dist/drakestar-fused-forge-${MC_VER}-${VERSION}.jar \
  -C forge/src/main/resources . \
  -C common/src/main/java .

echo "✓ Artifacts generated in dist/:"
ls -lh dist/
