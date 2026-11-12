<div align="center">

<img src="./assets/drakestar-fused-banner.svg" alt="DrakeStar Fused Banner" width="100%" />

# ⚡ DrakeStar Fused (`drakestar-fused`)
### *Fabric bridge mod (alpha) — plugin host in the integrated server is a design goal, not implemented*

[![DrakeStar Fused CI](https://github.com/SlimefunNewHorizons/drakestar-fused/actions/workflows/build-test.yml/badge.svg)](https://github.com/SlimefunNewHorizons/drakestar-fused/actions/workflows/build-test.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Fabric Loader](https://img.shields.io/badge/Loader-Fabric%20(Forge%20%2F%20NeoForge%3A%20planned)-c084fc.svg)](https://fabricmc.net/)
[![Target Minecraft](https://img.shields.io/badge/Minecraft-1.20%20to%2026.2%20(Fabric%20only)-00f0ff.svg)](https://papermc.io/)
[![Architecture](https://img.shields.io/badge/Status-alpha%20%C2%B7%20no%20plugin%20host%20yet-f59e0b.svg)](https://github.com/SlimefunNewHorizons)

**English** · [Español](#-visión-arquitectónica-en-español)

</div>

---

## Estado real / Actual status (alpha.3)

- **Funciona hoy**: un solo `.jar` de Fabric (`drakestar-fused-fabric-universal-<version>.jar`) para **Minecraft 1.20 a 26.2**, sin mixins (usa los eventos `SERVER_STARTING/STOPPING` de Fabric API). Probado con el launcher DrakeStar en 1.20.1, 1.21.1, 1.21.11, 26.1 y 26.2; en 1.21.1 se enganchó al crear un mundo en solitario.
- **Decisión (2026-11-12)**: Fused **no** es el camino para jugar Slimefun. Un host Bukkit dentro del servidor integrado no cubre Fabric+Forge+NeoForge de 1.20 a 26.2 ni las optimizaciones del núcleo de DrakesPurpur (Cardboard: solo Fabric 1.21.x y Slimefun falla en las cabezas de jugador; Arclight: hasta 1.21.1). El launcher DrakeStar arranca el DrakesPurpur real en segundo plano («Slimefun en solitario / LAN»). Fused queda como mod opcional en alfa.
- **Aun no**: el host Bukkit/Purpur dentro del servidor integrado **todavia no ejecuta plugins**. Hoy prepara `plugins/`, detecta jars y enlaza el puente Rust si existe. Lo demas de este README es el objetivo de diseño.
- Las releases `v1.0.0-alpha.1` y `alpha.2` usaban mixins sin remapear y **fallan al arrancar** en el juego real: no usarlas.
- Para publicar: subir `mod_version` en `gradle.properties` y empujar la etiqueta `vX.Y.Z`; el flujo compila, valida el jar (`release-artifacts/check_jar.py`) y crea la release.
- Sin descargas remotas ni navegador automatico: `-Ddrakestar.fused.provision=true` activa la descarga de suites; `-Ddrakestar.fused.openIssue=true` abre el enlace de reporte de fallos.

---

> [!IMPORTANT]
> **Todo lo que sigue desde aquí hasta «Networking» es la VISIÓN / DISEÑO OBJETIVO, no el estado real.** Hoy (alpha.3) el mod **no ejecuta plugins Bukkit**, no hay `CraftServer`, no se verificó compatibilidad con shaders/mods de cliente ni rendimiento. Para jugar Slimefun el camino soportado es el **servidor local gestionado del [DrakeStar Launcher](https://github.com/SlimefunNewHorizons/DrakeStar-Launcher)** (arranca el DrakesPurpur real en segundo plano y entra solo). Ver «Estado real» arriba.
>
> *Everything below up to «Networking» is the design vision, not the current state: alpha.3 does not run Bukkit plugins and nothing here (single-JVM host, CraftServer, shader/mod compatibility, Rust off-heap tickers inside the client) has been verified.*

## 🏛️ Executive Summary — design vision (not implemented)

**DrakeStar Fused** is an open-source bridge mod engineered for Minecraft Java Edition. It solves the historical dilemma of running enterprise Bukkit plugins (specifically **Slimefun v11** and the **StarSuites** 20,000+ technical item matrix) alongside modern client-side performance and aesthetic modpacks (such as **Iris Shaders**, **Sodium**, **Distant Horizons**, and **Sodium-Extra**).

Traditionally, playing heavy Bukkit modded environments locally required running **two separate JVM instances**:
1. A dedicated headless server (`paper.jar` / `purpur.jar`) consuming 3 to 6 GB of RAM.
2. A Minecraft Java client instance consuming another 4 to 8 GB of RAM.

This dual-JVM approach doubles CPU overhead, causes severe garbage collection pauses on laptops and mid-tier workstations, and introduces port forwarding headaches for local play.

**DrakeStar Fused fuses the Bukkit runtime directly inside Minecraft's internal `IntegratedServer`**.

---

## 🔮 Key Architectural Pillars — target design (not implemented)

```
+---------------------------------------------------------------------------------+
|                         MINECRAFT CLIENT (SINGLE JVM)                           |
|                                                                                 |
|  +--------------------------------+   +---------------------------------------+  |
|  |     RENDER ENGINE (CLIENT)     |   |          INTEGRATED SERVER            |  |
|  |   Iris / Sodium / Embeddium    |   |                                       |  |
|  |   Modern UI / Blackwall HUD    |   |  +---------------------------------+  |  |
|  +--------------------------------+   |  |     DrakeStar Fused Mixins      |  |  |
|                  |                    |  +---------------------------------+  |  |
|                  | Local Connection   |                  |                    |  |
|                  | (Direct Memory)    |  +---------------------------------+  |  |
|                  v                    |  |   Bukkit API / CraftServer Host |  |  |
|  +--------------------------------+   |  +---------------------------------+  |  |
|  |   Integrated Player Connection |   |                  |                    |  |
|  +--------------------------------+   |  +---------------------------------+  |  |
|                                       |  | StarSuites & Slimefun Plugins   |  |  |
|                                       |  | (drakes-core, drakes-tech, etc) |  |  |
|                                       |  +---------------------------------+  |  |
|                                       |                  | (FFM ABI 1)        |  |
|                                       |  +---------------------------------+  |  |
|                                       |  | libdrakes_hybrid_ffi.so (Rust)  |  |  |
|                                       |  |  - Energy Grids SIMD Ticker     |  |  |
|                                       |  |  - Off-Heap Block Storage       |  |  |
|                                       |  +---------------------------------+  |  |
|                                       +---------------------------------------+  |
+---------------------------------------------------------------------------------+
```

1. **Zero Dual-JVM Overhead**:
   - Single process lifecycle. When you pause, open your world to LAN, or close to title, the internal server and Bukkit lifecycles start and stop seamlessly in the same JVM.
2. **Purpur 26.2 Engine Injection via Fabric Mixins**:
   - Hooks into `net.minecraft.server.integrated.IntegratedServer` during world initialization.
   - Bootstraps a lightweight `CraftServer` instance with Bukkit `PluginManager`, loading `.jar` plugins from `instance/plugins/`.
3. **Rust SIMD Off-Heap Energy & Machine Tickers**:
   - Direct JNI / Java 25 Foreign Function & Memory (FFM) binding to `libdrakes_hybrid_ffi.so`.
   - Massive energy networks (Networks V6, Infinity Expansion, Quantum Storage) calculate off-heap with AVX-512 VNNI instructions without triggering Java GC pauses.
4. **Shader & Client Mod Harmony** *(goal; not verified)*:
   - Intended to coexist with Fabric client mods (Sodium, Iris, REI, minimaps). Only the launcher's performance set has been launched alongside Fused alpha.3.

---

## 🗂️ Project Structure

```
drakestar-fused/
├── assets/
│   └── drakestar-fused-banner.svg     # Official vector visual banner
├── common/                             # Shared engine abstractions & FFM bridges
│   └── src/main/java/com/drakescraft/fused/
│       ├── api/FusedLifecycleListener.java
│       └── rust/SlimefunRustNativeBridge.java
├── fabric/                             # Fabric Loader module (First Priority)
│   ├── build.gradle
│   ├── src/main/java/com/drakescraft/fused/
│   │   ├── DrakeStarFusedClient.java
│   │   ├── DrakeStarFusedMod.java
│   │   ├── mixin/
│   │   │   ├── IntegratedServerMixin.java
│   │   │   └── MinecraftServerMixin.java
│   │   └── bridge/
│   │       ├── FusedCraftServer.java
│   │       └── FusedPluginLoader.java
│   └── src/main/resources/
│       ├── fabric.mod.json
│       └── drakestar-fused.mixins.json
├── neoforge/                           # NeoForge compatibility module
│   └── src/main/java/com/drakescraft/fused/neoforge/DrakeStarFusedNeoForge.java
├── forge/                              # Classic Forge compatibility module
│   └── src/main/java/com/drakescraft/fused/forge/DrakeStarFusedForge.java
├── docs/
│   └── NETWORKING_AND_LAN_GUIDE.md    # Critical Guide on LAN vs Essential Mod conflict
└── README.md
```

---

## ⚠️ Networking & LAN Hosting Guidelines (Essential Mod Conflict)

Please read our dedicated [Networking and LAN Guide](docs/NETWORKING_AND_LAN_GUIDE.md).

> [!CAUTION]
> **DO NOT USE ESSENTIAL MOD WITH DRAKESTAR FUSED.**  
> Mods like **Essential Mod** or **e4mc** hijack the internal packet layer with proprietary UPnP tunnels. With over 20,000 custom Slimefun items and off-heap Rust tickers, Essential Mod drops custom payload packets, corrumpts world tile entities, and causes thread lockups.

**Recommended Multiplayer Approaches:**
* **Option A (Friends):** Use native Minecraft **Open to LAN** or connect via private virtual mesh network (**Tailscale / WireGuard**).
* **Option B (Slimefun today):** use the **DrakeStar Launcher** «Slimefun solo / LAN» instance: it runs the DrakesPurpur server locally and joins it (see the launcher's `docs/LAUNCHER.md`).

---

## 🛠️ Building & Compiling on Nova (Intel i7 Tiger Lake / AVX-512)

Following the sovereign hardware rules of node **Nova**, builds are strictly constrained to preserve KDE Plasma UI smoothness:

```bash
# Recommended build command on Nova (uses max 4 threads to keep 4 threads free)
export MAVEN_OPTS="-Xmx3584m -XX:+UseParallelGC"
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx3584m -Dorg.gradle.parallel=true -Dorg.gradle.workers.max=4"

# Build Fabric Mod JAR
./gradlew build
```

---

## 🇪🇸 Visión Arquitectónica en Español — diseño objetivo, no estado real

### ¿Por qué existe DrakeStar Fused?
El ecosistema de **Slimefun** enriquecido con **StarSuites** contiene más de 20,000 ítems, máquinas cuánticas, reactores nucleares, bio-ingeniería genética y redes de energía. Hasta la fecha, jugarlo requería montar un servidor Paper/Purpur dedicado y luego abrir un cliente de Minecraft por separado para conectarse a `localhost`.

En laptops y computadores personales, tener dos instancias completas de Java devora entre 8 y 14 GB de memoria RAM y satura la CPU con la recolección de basura (GC).

**Objetivo de diseño (no implementado en alpha.3): DrakeStar Fused uniría los tres mundos:**
1. **Fabric Loader**: Te permite cargar tus mods favoritos de optimización (Sodium, Iris, Lithium, Indium) y shaders cinematográficos.
2. **Purpur / Bukkit**: Inyecta en el servidor interno (`IntegratedServer`) la capacidad de cargar plugins Spigot/Paper/Purpur sin requerir una segunda ventana o servidor externo.
3. **Rust Off-Heap SIMD (`Slimefun-Rust`)**: Conecta el cálculo de redes eléctricas y almacenamiento masivo a librerías nativas en Rust compiladas con AVX-512, liberando a Java de cualquier micro-lag o caída de TPS.

### Roadmap de Soporte Multi-Loader (planificado; hoy solo existe el jar de Fabric):
1. 🚀 **Módulo 1 (Fabric Loader):** Implementación principal y más liviana.
2. 🛠️ **Módulo 2 (NeoForge Loader):** Para usuarios de modpacks modernos basados en NeoForge.
3. ⚙️ **Módulo 3 (Forge Clásico):** Soporte de retrocompatibilidad para packs tradicionales.

---

## 📜 Licencia & Créditos Soberanos

- **Autor & Arquitecto:** JackStar6677 — Arquitecto Jefe de DrakesCraft & Star.
- **Organización Upstream:** [SlimefunNewHorizons](https://github.com/SlimefunNewHorizons)
- **Licencia:** MIT License. Código abierto para la comunidad.
