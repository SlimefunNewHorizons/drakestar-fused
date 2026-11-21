# Laboratorio del servidor embebido (paso 1)

Experimento independiente del mod, requiere Linux y JDK 25. `probe.py` compila
el anfitrión desechable `Embed`; este carga Paperclip dentro de **su misma JVM**.
El proceso Python sólo supervisa el experimento. No se inicia un cliente de
Minecraft ni se valida Fabric/Forge/NeoForge aquí.

```sh
~/ai-hub/scripts/build_seguro.sh 25 "$PWD" \
  python3 tools/embedded-spike/probe.py \
  --jar /ruta/purpur-paperclip-26.2.build.140-stable.jar \
  --mojang-cache /ruta/mojang_26.2.jar \
  --output /tmp/fused-baseline --mode baseline
```

El directorio de salida debe ser nuevo. El JAR debe ser de confianza: ejecuta
código del servidor y sus plugins. Se usan un mundo nuevo, puerto efímero en
`127.0.0.1`, modo offline y ningún archivo de configuración de producción.
La caché Mojang es opcional; Paperclip puede descargarla si falta. No pasar una
carpeta de servidor existente como salida. El laboratorio acepta la EULA para
este servidor desechable.

`baseline` cambia `user.dir`, configura `bundlerRepoDir` y pasa rutas absolutas
para mundo, plugins y configuraciones. Sirve para medir las rutas que siguen
escapando y el comportamiento real de `stop`. **Código 1 significa que el paso
1 no cumple los criterios**, incluso si el servidor sale con código 0.

`--mode isolated` ensaya un cargador que transforma las llamadas de rutas y
salida usando la API Class-File de JDK 25. Conserva el `user.dir` del anfitrión,
reemplaza el cargador generado por Paperclip y proporciona un log4j2.xml propio.
Intercepta llamadas directas a `System.exit`, `Runtime.exit/halt` y hooks de
apagado en las clases cargadas por ese cargador, y registra los ejecutores
creados con `Executors.new*` para apagarlos tras `stop`. **Es experimental**: no cubre
automáticamente cargadores de plugins, llamadas reflectivas, código nativo ni
APIs de archivo que reciben cadenas sin pasar por `File`/`Path`. No es una
frontera de seguridad ni una implementación integrada en Fused.

Cada ejecución conserva `console.txt` y `result.json`: SHA-256 del JAR y
fuentes, JVM, PID, puerto, RSS máximo observado, duración, `Done`, orden `stop`,
supervivencia del anfitrión y archivos modificados. inotify vigila el directorio
del anfitrión (inicialmente sólo contiene un centinela) para detectar también
la creación y retirada de archivos/subdirectorios transitorios. No pretende
auditar todas las escrituras del sistema de archivos fuera del laboratorio.

El supervisor espera a `Done (` antes de enviar `stop`; un puerto abierto por
sí solo no acredita preparación. Sólo acepta éxito con anfitrión vivo después
de la parada, salida normal, cero eventos de escritura y centinela intacto.
El modo aislado comprueba además que termina `Server thread` y conserva
`user.dir`. La prueba tiene plazo y retira su grupo de procesos al agotarlo.
Las marcas de supervivencia se escriben en disco porque el servidor puede
reemplazar/cerrar `System.out` durante la parada.

`--preload-natives` (sólo `isolated`) hace que el anfitrión cargue antes netty-epoll, JNA y sqlite en
su propio cargador, como haría el cliente; ver `docs/evidence/embedded-step3-jni-20261120`.

`--host-log4j` (sólo `isolated`) configura el Log4j del anfitrión en un cargador propio con la propiedad
global `-Dlog4j.configurationFile` (como hace el launcher de Minecraft) antes de iniciar el servidor, y
comprueba que el servidor escriba en su propio `logs/latest.log` sin contaminar `client-logs/latest.log`
y que el contexto del cliente siga activo tras `stop`; ver `docs/evidence/embedded-step3-log4j-20261121`.

`--rust-ffm --natives-dir <dir>` (sólo `isolated`) copia `libdrakes_hybrid_ffi.so`, `libodysseia_ffi.so` y
`libslimefun_ffi.so` a `server/natives/`, habilita todas las banderas `native.*` y `hybrid.engine.enabled` en
`drakes-data/perf.yml`, precarga los tres `.so` por FFM (`SymbolLookup.libraryLookup`) más `libslimefun_ffi.so`
por JNI (`System.load`) en un cargador propio del anfitrión (`spike-client-rust`), y verifica tras `stop` que
`DrakesNative`, `HybridNative`, `DrakesKernelBridge` y `SlimefunRustEngine` queden activos junto con el
anfitrión. `--rust-jni-conflict` reproduce el fallo de control cuando ambos cargadores llaman a `System.load`
sobre la misma ruta `server/natives/libslimefun_ffi.so`; ver `docs/evidence/embedded-step3-rust-ffm-20261121`.

Los siguientes pasos de `docs/EMBEDDED_SERVER_SPIKE.md` requieren evidencia
propia. Este laboratorio no acredita cliente real ni soporte de ningún loader.
