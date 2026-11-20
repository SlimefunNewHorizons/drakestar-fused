# Paso 1: mediciones del 17 de noviembre de 2026

Estado: **paso 1 pasa en laboratorio (20-nov)**. Se conserva el experimento y sus fallos para continuar
desde hechos reproducibles. No se valida Slimefun dentro de un cliente.

Artefacto publicado: [Purpur 26.2 build 140](https://github.com/SlimefunNewHorizons/DrakesPurpur/releases/tag/v26.2.140-stable),
`purpur-paperclip-26.2.build.140-stable.jar`, 97.296.375 bytes,
SHA-256 `750811c856cb0e36a9b117598d7d57834b53779f5d17d84ca04f47e747f00145`.
El servidor parcheado declara SHA-256
`fb075e60df8eb6e5512a78646e643dc76817bdb8fc753c4a85020e055e80a1d6`.
JVM OpenJDK 25.0.5.0.3 en Linux amd64, heap máximo 2 GiB y dos procesadores
activos. Cada arranque usa su propia carpeta y caché copiada. Todos los builds
y ejecuciones se hicieron mediante `~/ai-hub/scripts/build_seguro.sh 25`,
respetando su espera por carga y su lock global.

## Control sin transformaciones

[Consola](control/console.txt), [resultado](control/result.json).
Mismo JAR y mismos hashes de fuentes que el segundo intento aislado.
`user.dir` cambia, pero `File("")` y `Path.of("")` siguen apuntando al anfitrión.
Aunque se pasan rutas absolutas de configuración, plugins y mundos, se crean
**23 archivos** en el anfitrión y se observan **40 eventos** de escritura,
incluidos `plugins/`, `drakes-data/`, bases SQLite y `logs/latest.log`.
El centinela se conserva. Los JARs de suites se extraen en la ruta relativa
`plugins/` del anfitrión, diferente de la ruta absoluta de plugins del arranque.

Llega a `Done`, recibe `stop` y finaliza toda la JVM con código 0: no aparece
la marca de supervivencia del anfitrión y sí su
[marca de apagado](control/host-shutdown.txt). Sin timeout ni terminación
forzada; 72,80 s, RSS máximo muestreado 895.968 KiB. El supervisor devuelve 1
porque esos hechos incumplen el paso 1, aunque el proceso Java devuelva 0.

## Primer intento aislado

[Consola](isolated-1/console.txt), [resultado](isolated-1/result.json).
El transformador no pudo resolver `SpikeHooks$ServerFile` al reconstruir los
stack maps de log4j. El resolver sólo veía recursos del cargador aislado,
mientras que la clase puente pertenece al anfitrión. No llegó a `Done`.
Se retiró manualmente **sólo la JVM del laboratorio**, PID 2980074, con SIGTERM
(salida 143, 78,96 s). La marca `forced_termination` aún no existía en esa
versión del supervisor: su ausencia no significa una salida natural.

Se corrigió el resolver para consultar también los recursos del cargador del
anfitrión. Los hashes de las fuentes anteriores constan en el resultado; el
primer intento es evidencia histórica y no representa las fuentes finales.

## Segundo intento aislado

[Consola](isolated-2/console.txt), [resultado](isolated-2/result.json),
[hilos tras la parada](isolated-2/threads-after-stop.txt),
[resumen de hilos](isolated-2/remaining-threads.json).

Llegó a `Done`, recibió `stop`, guardó los tres mundos e interceptó
`System.exit(0)`. La marca [host-alive-after-stop.txt](isolated-2/host-alive-after-stop.txt)
prueba que el código del anfitrión volvió a ejecutarse después de la parada.
No persistieron archivos nuevos/modificados/borrados en el anfitrión y su
`user.dir` se conservó. RSS máximo muestreado: 1.135.676 KiB.

**No pasa**: quedaron seis hilos de aplicación no daemon (cuatro
`Dimension-Data-IO-Worker`, `Craft Async Scheduler Management Thread` y
`pool-8-thread-1`). `Server thread` ya había terminado, pero la JVM no podía
finalizar normalmente al salir el anfitrión. El supervisor agotó 240 s y
retiró el grupo del laboratorio con SIGTERM: salida 143, duración 241,32 s.
Interceptar `System.exit` no sustituye la gestión de los ejecutores que Purpur
deja vivos al asumir que terminará todo el proceso. El volcado no atribuye el
propietario de `pool-8-thread-1`.

**Límite del observador**: se usó `jcmd Thread.print` tras detectar la marca de
supervivencia. El attach de HotSpot creó, cerró y borró
`.attach_pid2983995` en el directorio observado; son los tres eventos de
inotify conservados en el resultado. Por ello esta ejecución tampoco acredita
cero escrituras transitorias. No se eliminan esos eventos ni se atribuyen al
servidor. Una futura prueba debe obtener hilos desde el anfitrión o arrancar
el listener de attach antes de vigilar el directorio.

Se detuvo la corrección tras los dos intentos, conforme a la regla de Jack.
No hubo un tercer intento aislado. El control sin transformaciones mide el
comportamiento original del mismo build y no constituye otra corrección.

## Tercer intento aislado (20 de noviembre de 2026)

[Consola](isolated-3/console.txt), [resultado](isolated-3/result.json),
[hilos tras la parada](isolated-3/non-daemon-threads-after-stop.txt).

Causa de los hilos del segundo intento, leída en el bytecode del build 140:
`Util.DIMENSION_DATA_IO_POOL` (`Dimension-Data-IO-Worker-*`) y el ejecutor
`management` de `CraftAsyncScheduler` se crean con `Executors.new*` y el
servidor nunca los apaga porque asume que la JVM muere tras `stop`.
`SpikeLoader` ahora envuelve toda llamada `Executors.new*` que devuelve
`ExecutorService`/`ScheduledExecutorService` con `SpikeHooks.track`, y el
anfitrión aplica `shutdownNow` + espera de 10 s tras interceptar la salida.
El listado de hilos se toma **dentro del proceso** (sin `jcmd`, sin
`.attach_pid` en el directorio vigilado).

Mismo JAR (SHA-256 `750811c8…0145`), JDK 25.0.5.1. Dos ejecuciones
consecutivas pasan (`step1_pass: true`); se conserva la segunda: `Done`,
`stop`, `System.exit(0)` interceptado, anfitrión vivo después de la parada,
**0 eventos de escritura** en el anfitrión, `user.dir` intacto, 14 ejecutores
retirados (0 atascados), salida natural 0 sin terminación forzada en 109,46 s,
RSS máximo 1.169.272 KiB.

Quedan tres `Craft Scheduler Thread` no daemon al momento de la parada: son
del `ThreadPoolExecutor` construido directamente por `CraftAsyncScheduler`
(no pasa por `Executors`), con `allowCoreThreadTimeOut`, y terminan solos
por inactividad; por eso la JVM salió sin ayuda. En un cliente real eso deja
el cargador del servidor retenido ~60 s tras salir del mundo. Mismo ERROR
de mundo plano que antes (`No key layers in MapLike[{}]`), ajeno a la parada.

## Verificación independiente (20-nov)

[Consola](verify-20261120/console.txt), [resultado](verify-20261120/result.json).
Revisor distinto al autor; mismo JAR (SHA-256 `750811c8…0145`) y **mismos hashes
de fuentes que el tercer intento aislado**, `--mode isolated`, carpeta nueva,
por `build_seguro.sh 25` con carga ~4,5. Resultado: `step1_pass=true`, `Done`,
`stop` enviado, anfitrión vivo tras la parada, salida 0 sin timeout ni
terminación forzada, cero archivos creados/modificados/borrados en el anfitrión,
`Path.of("")` intacto, 120,06 s, RSS máximo 1.212.948 KiB. Reproduce el paso 1.

Contraste: el prototipo anterior sin versionar (`common/.../embedded/`, 14-nov,
reescritura manual de bytecode) también deja el anfitrión vivo y sin fugas de
archivos, pero tras `stop` quedan **25–28 hilos** (9 no-daemon: Craft Scheduler,
Dimension-Data-IO, pool-8) y la JVM no termina; interrumpirlos no basta. Confirma
que hace falta retirar los ejecutores como hace `SpikeHooks`.

## Alcance y continuación

- [x] Laboratorio ejecutable con `Done`/`stop`, hashes, RSS, centinela e inotify.
- [x] Dos intentos reales sobre el build 140 y evidencia del defecto de parada.
- [x] Control real con el mismo build y las mismas fuentes finales.
- [x] Paso 1 en laboratorio: ejecutores retirados y prueba repetida sin
  contaminación del observador, anfitrión vivo (tercer intento). Pendiente
  menor: retirar también el `ThreadPoolExecutor` directo de CraftAsyncScheduler.
- [ ] Paso 2: cliente Fabric 26.2 real, quickplay, RAM y captura del juego.
- [ ] Paso 3: JNI doble, log4j y Rust activos.
- [ ] Paso 4: NeoForge/Forge con el mismo núcleo y pruebas propias.
- [ ] Paso 5: cambiar plantilla del launcher después de las cuatro puertas.

Los logs declaran `Initialized 0 plugins`, Rust nativo/híbrido apagado y SAORI
sin token (esperado en una carpeta nueva sin credenciales). El build contiene
nueve JARs `drakes-bundled`, pero **su mera presencia no prueba su activación**.
No se ensayaron Slimefun, addons ni la biblioteca Rust dentro del proceso.
Hay un ERROR de generación plana (`No key layers in MapLike[{}]`); estos logs
no son evidencia de arranque sin errores.

No se tocó Dallas, ningún mundo existente, servicios de SAORI, README principal
ni launcher. El código sin versionar del checkout original se conservó.
