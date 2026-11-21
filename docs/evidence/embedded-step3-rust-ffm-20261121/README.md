# Paso 3 (completo en laboratorio): motor Rust por FFM activo (`native.enabled=true`) + aislamiento JNI/Log4j2, 21 de noviembre de 2026

Estado: **el Paso 3 completo de `docs/EMBEDDED_SERVER_SPIKE.md` (riesgos 3 y 4: JNI doble `netty-epoll`/`JNA`/`sqlite`, aislamiento de Log4j2 frente a `-Dlog4j.configurationFile` del anfitrión, y motor Rust por FFM/JNI activo con `native.enabled=true` y `hybrid.engine.enabled=true`) pasa en laboratorio**. No acredita aún un cliente Fabric/Forge/NeoForge real (pasos 2 y 4) ni el cambio de plantilla del launcher (paso 5).

Artefactos probados bajo `~/ai-hub/scripts/build_seguro.sh 25` (OpenJDK `25.0.5.1+2-1-24.04.1-Ubuntu`):
- Monolito: `purpur-paperclip-26.2.build.140-stable.jar` (`SHA-256: 750811c856cb0e36a9b117598d7d57834b53779f5d17d84ca04f47e747f00145`).
- Bibliotecas nativas Rust en `server/natives/`:
  - `libdrakes_hybrid_ffi.so` (`SHA-256: 427e2b032663b165254e572d5bbfe42bd1f5df040a639a4cec56f0caf7239487`, ABI `1`).
  - `libodysseia_ffi.so` (`SHA-256: 9a799ea50fafeda67c7b443f1ab6ce417cff39b21939b1b88bb5d55f1fe299f4`, ABI `1`).
  - `libslimefun_ffi.so` (`SHA-256: 67902065512bdef74030268eec0f0aaec746047f14754f14bccca25022e4e33f`, ABI `2`).

## Diagnóstico de bytecode (`DrakesPurpur` 26.2 build 140) y control (`control/`)

Al habilitar `native.enabled: true`, `hybrid.engine.enabled: true` y todas las sub-opciones (`odysseia.pulses/aura`, `slimefun.fill/sum/price/tick-engine`, `hybrid.bits/noise/region`) en `drakes-data/perf.yml`, el servidor embebido enlaza las bibliotecas Rust por dos mecanismos distintos de la JVM:

1. **Panama FFM (`SymbolLookup.libraryLookup(Path, Arena.global())`):**
   - `org.purpurmc.purpur.nativelib.DrakesNative` abre `natives/libodysseia_ffi.so` y `natives/libslimefun_ffi.so` mediante `SymbolLookup.libraryLookup`.
   - `org.purpurmc.purpur.nativelib.HybridNative` abre `natives/libdrakes_hybrid_ffi.so` mediante `SymbolLookup.libraryLookup`.
   - En JDK 25, `SymbolLookup.libraryLookup` utiliza `RawNativeLibraries` (`dlopen` asociado al `Arena`, sin restricción de un único `ClassLoader` por ruta). Por ello, el cargador del anfitrión (`spike-client-rust`) y el cargador del servidor (`SpikeLoader`) pueden abrir simultáneamente **la misma ruta física** de los tres `.so` por FFM sin conflicto.
2. **JNI (`System.load(String)`):**
   - Cuando `DrakesNative.tickEngineAllowed()` es `true` (`native.enabled=true` y `native.slimefun.tick-engine=true`), tanto `org.purpurmc.purpur.DrakesKernelBridge.loadRustNativeBridge()` como `org.purpurmc.purpur.rust.SlimefunRustEngine.initPanamaBindings()` ejecutan `System.load(new File("natives/libslimefun_ffi.so").getAbsolutePath())` antes de enlazar los símbolos FFM.
   - Si el cargador del cliente/anfitrión (`spike-client-rust`, o `SlimefunRustNativeBridge.init` del mod) invoca `System.load` sobre esa **misma ruta canónica** `server/natives/libslimefun_ffi.so` (corrida [`control/`](control/result.json), `--rust-jni-conflict`), la tabla `NativeLibraries` de JNI rechaza la segunda carga desde `SpikeLoader` con:
     `[WARN] [DrakesPurpur] Advertencia cargando puente nativo Rust: Native Library .../server/natives/libslimefun_ffi.so already loaded in another classloader`
     dejando `DrakesKernelBridge.isRustLoaded() = false` y `SlimefunRustEngine.isNativeLinked() = false` (`isNative=false`), aunque `DrakesNative` y `HybridNative` (FFM puro) sigan activos.

## Solución verificada (`run-1/` y `run-2/`)

Cuando el cargador del anfitrión (`spike-client-rust`) copia o extrae su biblioteca JNI a una ruta propia por cargador (`client-natives/libslimefun_ffi.so`) antes de `System.load`, mientras ambos cargadores comparten directamente `server/natives/libdrakes_hybrid_ffi.so`, `libodysseia_ffi.so` y `libslimefun_ffi.so` por FFM (`SymbolLookup.libraryLookup`):

- Todos los puentes Rust del servidor (`DrakesNative`, `HybridNative`, `DrakesKernelBridge` y `SlimefunRustEngine`) y del anfitrión (`HostRustProbe` en `spike-client-rust`) quedan activos simultáneamente antes del arranque y después de `stop` (`rust_all_active=true`).
- Convive en la misma corrida con `--preload-natives` (`netty-epoll`, `JNA`, `sqlite 3.49.1`) y `--host-log4j` (`-Dlog4j.configurationFile=client-log4j2.xml`).

| Métrica | `control` (`--rust-jni-conflict`) | `run-1` (`--preload-natives --host-log4j --rust-ffm`) | `run-2` (`--preload-natives --host-log4j --rust-ffm`) |
|---|---|---|---|
| FFM en anfitrión (`spike-client-rust`) antes y tras `stop` | `hybrid.abi=1 odysseia.abi=1 slimefun.abi=2 sum=60/150` | `hybrid.abi=1 odysseia.abi=1 slimefun.abi=2 sum=60/150` | `hybrid.abi=1 odysseia.abi=1 slimefun.abi=2 sum=60/150` |
| `DrakesNative` (FFM Odysseia + Slimefun en servidor) | `enabled=true`, `sum=600`, `price=90.0`, `auraHits=1`, `Odysseia$Native` | `enabled=true`, `sum=600`, `price=90.0`, `auraHits=1`, `Odysseia$Native` | `enabled=true`, `sum=600`, `price=90.0`, `auraHits=1`, `Odysseia$Native` |
| `HybridNative` (FFM bits/noise/region en servidor) | `libraryLoaded=true`, `bitsEnabled=true`, `packWords=128`, `parityOk=true` | `libraryLoaded=true`, `bitsEnabled=true`, `packWords=128`, `parityOk=true` | `libraryLoaded=true`, `bitsEnabled=true`, `packWords=128`, `parityOk=true` |
| `DrakesKernelBridge.isRustLoaded()` / `SlimefunRustEngine.isNativeLinked()` | `false` / `false` (`already loaded in another classloader`) | `true` / `true` (`isNative=true`) | `true` / `true` (`isNative=true`) |
| `rust_all_active` | `false` | `true` | `true` |
| Log4j2 (`client_log_lines` / `client_log_foreign_lines` / `server_log_has_done`) | `2` / `0` / `true` | `2` / `0` / `true` (`10 675` B) | `2` / `0` / `true` (`10 676` B) |
| JNI doble (`UnsatisfiedLinkError` en consola / epoll tras `stop`) | `0` (capturado en WARN) / `true` | `0` / `true` en ambos cargadores | `0` / `true` en ambos cargadores |
| `step1_pass` (anfitrión vivo, salida 0 sin forzar, 0 escrituras en anfitrión) | `true` | `true` | `true` |
| Duración / RSS máx. | `106,07 s` / `966 708 KiB` | `103,97 s` / `1 045 884 KiB` | `108,50 s` / `1 084 592 KiB` |

Pendiente del plan `docs/EMBEDDED_SERVER_SPIKE.md`: pasos 2 (cliente Fabric 26.2 real), 4 (NeoForge/Forge) y 5 (plantilla del launcher).
