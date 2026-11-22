# Paso 4 (en laboratorio): módulos universales NeoForge, Forge y Fabric sobre el mismo núcleo + monolito `DrakesPurpur` 26.2 + Rust FFM/JNI, 21 de noviembre de 2026

Estado: **el Paso 4 de `docs/EMBEDDED_SERVER_SPIKE.md` (portar el arranque a NeoForge y Forge como módulos finos universales sobre el mismo núcleo `common`, verificados con `check_jar.py` y ejecutados en la misma JVM que el monolito `DrakesPurpur` 26.2 embebido con motor Rust FFM/JNI, Log4j2 aislado y JNI doble) pasa en laboratorio**. No acredita aún un cliente gráfico Fabric/Forge/NeoForge 26.2 real con quickplay (paso 2) ni el cambio de plantilla del launcher (paso 5).

Artefactos probados bajo `~/ai-hub/scripts/build_seguro.sh 25` (OpenJDK `25.0.5.1+2-1-24.04.1-Ubuntu`):
- Monolito: `purpur-paperclip-26.2.build.140-stable.jar` (`SHA-256: 750811c856cb0e36a9b117598d7d57834b53779f5d17d84ca04f47e747f00145`).
- JARs universales compilados por Gradle (`:fabric:build :neoforge:build :forge:build`, `--release 17`, validados con `release-artifacts/check_jar.py`):
  - `drakestar-fused-neoforge-universal-1.0.0-alpha.3.jar` (`SHA-256: 2b31447959838e6502ed7a362cc2c38c7b5364c849cb16c50ead55fc1f97c991`, 7 clases, `META-INF/neoforge.mods.toml`, `@Mod("drakestar_fused")`).
  - `drakestar-fused-forge-universal-1.0.0-alpha.3.jar` (`SHA-256: 58bf750edd711daac36035488ca83c23731e4efe793fd86f757d2e1367f79814`, 7 clases, `META-INF/mods.toml`, `@Mod("drakestar_fused")`).
  - `drakestar-fused-fabric-universal-1.0.0-alpha.3.jar` (`SHA-256: 372eebfef14914a37b05d3e4f8ebfd96846b2e0790e38f5bd5d125c36ab30c99`, 8 clases, `fabric.mod.json`, `ModInitializer`).
- Bibliotecas nativas Rust en `server/natives/`:
  - `libdrakes_hybrid_ffi.so` (`SHA-256: 427e2b032663b165254e572d5bbfe42bd1f5df040a639a4cec56f0caf7239487`, ABI `1`).
  - `libodysseia_ffi.so` (`SHA-256: 9a799ea50fafeda67c7b443f1ab6ce417cff39b21939b1b88bb5d55f1fe299f4`, ABI `1`).
  - `libslimefun_ffi.so` (`SHA-256: 67902065512bdef74030268eec0f0aaec746047f14754f14bccca25022e4e33f`, ABI `2`).

## Diagnóstico y control (`control/`, `--loader-jni-conflict`)

1. **Empaquetado universal sin Loom/ForgeGradle acoplado a una sola versión:**
   - Igual que `fabric/build.gradle`, `neoforge/build.gradle` y `forge/build.gradle` compilan ahora contra stubs limpios de las firmas públicas estables de cada cargador (`net.neoforged.bus.api.IEventBus`, `net.neoforged.fml.common.Mod`, `net.neoforged.neoforge.common.NeoForge`, `ServerStartingEvent`/`ServerStoppingEvent`; y `net.minecraftforge.eventbus.api.IEventBus`, `net.minecraftforge.fml.common.Mod`, `net.minecraftforge.common.MinecraftForge`, `ServerStartingEvent`/`ServerStoppingEvent`) con bytecode Java 17 (`--release 17`), cero mixins y ninguna referencia interna salvo `net/minecraft/server/MinecraftServer`.
   - `release-artifacts/check_jar.py` valida los tres descriptores (`fabric.mod.json`, `META-INF/neoforge.mods.toml`, `META-INF/mods.toml`), la expansión de `${version}`, el tope de bytecode Java 17 (`major <= 61`) y la ausencia de mixins/referencias internas.
2. **Aislamiento de ruta JNI en `SlimefunRustNativeBridge` (`common`):**
   - Cada módulo (`DrakeStarFusedNeoForge`, `DrakeStarFusedForge`, `DrakeStarFusedMod`) invoca `SlimefunRustNativeBridge.init(gameDir)` y `FusedCraftServer.bootstrap(gameDir)` al recibir `ServerStartingEvent` / `SERVER_STARTING`, y `FusedCraftServer.shutdown()` + `SlimefunRustNativeBridge.shutdown()` al recibir `ServerStoppingEvent` / `SERVER_STOPPING`.
   - Si `SlimefunRustNativeBridge.init` llama a `System.load` directamente sobre `server/natives/libdrakes_hybrid_ffi.so` o `libslimefun_ffi.so` (corrida [`control/`](control/result.json), `--loader-jni-conflict` / `-Ddrakestar.fused.jniDirectPath=true`):
     - `spike-loader-neoforge` toma `server/natives/libdrakes_hybrid_ffi.so`;
     - `spike-loader-forge` recibe `UnsatisfiedLinkError: Native Library .../libdrakes_hybrid_ffi.so already loaded in another classloader` y cae al segundo candidato `server/natives/libslimefun_ffi.so`;
     - `spike-loader-fabric` falla en ambos candidatos (`rustLoaded=false`, `loaders_all_active=false`);
     - el monolito `DrakesPurpur` (`SpikeLoader`) falla al cargar `server/natives/libslimefun_ffi.so` en `DrakesKernelBridge` / `SlimefunRustEngine` (`isRustLoaded=false`, `isNativeLinked=false`, `rust_all_active=false`).
   - Con `SlimefunRustNativeBridge.stageForCurrentLoader` (`client-natives/<loaderName>/<libName>`), cada cargador copia la biblioteca JNI a su ruta propia antes de `System.load`, evitando toda colisión entre cargadores de mods y con `DrakesKernelBridge`/`SlimefunRustEngine` del servidor embebido (`loaders_all_active=true`, `rust_all_active=true`).

## Resultados verificados (`control/`, `run-1/` y `run-2/`)

| Métrica | `control` (`--loader-jni-conflict`) | `run-1` (`--loader-mods`) | `run-2` (`--loader-mods`) |
|---|---|---|---|
| `neoforge` (`@Mod(drakestar_fused)`) inicio → parada | `initialized=true, rustLoaded=true` (`natives/libdrakes_hybrid_ffi.so`) → `false/false` | `initialized=true, rustLoaded=true` (`client-natives/spike-loader-neoforge/libdrakes_hybrid_ffi.so`) → `false/false` | `initialized=true, rustLoaded=true` (`client-natives/spike-loader-neoforge/libdrakes_hybrid_ffi.so`) → `false/false` |
| `forge` (`@Mod(drakestar_fused)`) inicio → parada | `initialized=true, rustLoaded=true` (`natives/libslimefun_ffi.so`, `lastError=already loaded`) → `false/false` | `initialized=true, rustLoaded=true` (`client-natives/spike-loader-forge/libdrakes_hybrid_ffi.so`, `lastError=null`) → `false/false` | `initialized=true, rustLoaded=true` (`client-natives/spike-loader-forge/libdrakes_hybrid_ffi.so`, `lastError=null`) → `false/false` |
| `fabric` (`ModInitializer(drakestar-fused)`) inicio → parada | `initialized=true, rustLoaded=false` (`lastError=already loaded`) → `false/false` | `initialized=true, rustLoaded=true` (`client-natives/spike-loader-fabric/libdrakes_hybrid_ffi.so`, `lastError=null`) → `false/false` | `initialized=true, rustLoaded=true` (`client-natives/spike-loader-fabric/libdrakes_hybrid_ffi.so`, `lastError=null`) → `false/false` |
| `loaders_all_active` | `false` | `true` | `true` |
| `DrakesKernelBridge.isRustLoaded()` / `SlimefunRustEngine.isNativeLinked()` | `false` / `false` | `true` / `true` (`isNative=true`) | `true` / `true` (`isNative=true`) |
| `DrakesNative` + `HybridNative` + `HostRustProbe` (`rust_all_active`) | `false` | `true` (`sum=600`, `price=90.0`, `packWords=128`, `parityOk=true`) | `true` (`sum=600`, `price=90.0`, `packWords=128`, `parityOk=true`) |
| Log4j2 (`client_log_lines` / `client_log_foreign_lines` / `server_log_has_done`) | `2` / `0` / `true` (`10 717` B) | `2` / `0` / `true` (`10 657` B) | `2` / `0` / `true` (`10 656` B) |
| JNI doble (`UnsatisfiedLinkError` en consola / epoll tras `stop`) | `0` / `true` | `0` / `true` en ambos cargadores | `0` / `true` en ambos cargadores |
| `step1_pass` (anfitrión vivo, salida 0 sin forzar, 0 escrituras en anfitrión) | `true` | `true` | `true` |
| Duración / RSS máx. | `112,36 s` / `1 171 920 KiB` | `109,30 s` / `996 760 KiB` | `104,90 s` / `1 062 620 KiB` |

Pendiente del plan `docs/EMBEDDED_SERVER_SPIKE.md`: pasos 2 (cliente Fabric/Forge/NeoForge 26.2 gráfico real con quickplay) y 5 (plantilla del launcher).
