# Servidor embebido en el mismo proceso — prueba de concepto (2026-11-12)

Objetivo de Jack: jugar Slimefun (monolito DrakesPurpur + optimizaciones Rust + 100+ addons) en solitario/LAN **dentro del proceso de Minecraft**, con los mods del jugador y en Fabric, Forge y NeoForge, sin levantar un servidor aparte ni convertir plugins en mods.

## Lo que se probó (evidencia)

Un programa Java mínimo (`Embed`, JVM 25) cargó `purpur-paperclip-26.2.build.140-stable.jar` (el monolito de DrakesPurpur) en un `URLClassLoader` aislado (padre = plataforma) dentro de **su** JVM y llamó a `io.papermc.paperclip.Main.main("--nogui")` desde un hilo propio:

- El servidor llegó a `Done (15.5 s)` y abrió el puerto en 14 s.
- El cargador de la aplicación **no veía** clases `net.minecraft.*` (aislamiento correcto: no choca con las clases del cliente, que viven en el cargador del juego).
- La JVM anfitriona siguió viva con el servidor corriendo.

Es decir: **el monolito completo (Slimefun, 9 suites, núcleo Drakes) sí arranca dentro de otro proceso Java sin ser un proceso aparte.** La lógica es solo de cargadores de clases, así que es la misma para Fabric, Forge y NeoForge (cambia solo el arranque de cada mod).

## Lo que NO está resuelto (riesgos reales, por orden)

1. **Directorio de trabajo**: Paperclip usa rutas relativas al `user.dir` de la JVM (el directorio del juego). Mundo, `plugins/`, `config/` y `server.properties` del servidor quedarían mezclados con los del cliente; `logs/latest.log` lo escriben los dos. Hay que redirigir las rutas (propiedades de Paper/Paperclip o un launcher que fije el cwd del servidor).
2. **`System.exit` / `Runtime.halt`** al parar el servidor: mataría la JVM del cliente. Hay que impedir el apagado completo o interceptar la salida (no hay SecurityManager en Java 25).
3. **Bibliotecas nativas JNI** (sqlite, netty-epoll, Rust `libdrakes_hybrid_ffi` por FFM): solo un cargador de clases puede cargar una misma `.so`; si cliente y servidor cargan la misma, `UnsatisfiedLinkError`.
4. **Estáticos compartidos** (log4j, SLF4J, propiedades del sistema) entre cliente y servidor.
5. **Memoria**: sigue siendo un servidor completo (3–4 GB aparte del cliente); se ahorra solo la segunda JVM, no el servidor.
6. **Entrada del jugador**: conectar el cliente al puerto cuando esté listo, igual en 1.20–26.2 (la API de conexión cambia entre versiones). Idea: el mod bloquea su inicialización hasta que el servidor responda y el launcher pasa `--quickPlayMultiplayer 127.0.0.1:<puerto>`.
7. **Versiones**: el monolito existe **solo para 26.2** (Java 25). Para 1.20–1.21 habría que usar Purpur + Slimefun4-Drake (Java 17/21), sin las optimizaciones del núcleo de DrakesPurpur.
8. **Mods de servidor del jugador** no se aplican al mundo de plugins (el servidor embebido es Purpur, no Fabric/Forge): los mods de **cliente** sí conviven.

## Plan por pasos (cada uno verificable)

1. Probar en `Embed` con `user.dir` aislado: lanzar el servidor con rutas propias y confirmar que no toca el directorio del anfitrión (riesgo 1) y qué ocurre al `stop` (riesgo 2).
2. Probar el arranque dentro de un cliente Fabric 26.2 real (hilo desde el entrypoint, bloqueo hasta puerto abierto) con quickplay; registrar logs y RAM. **Pasa en CI con Slimefun sembrado (23-nov)**.
3. Resolver 3 y 4 con pruebas reales (JNI doble, log4j). **Pasa completo en laboratorio**: JNI de netty-epoll/JNA/sqlite (`docs/evidence/embedded-step3-jni-20261120`), log4j del anfitrión con `-Dlog4j.configurationFile` (`docs/evidence/embedded-step3-log4j-20261121`) y **Rust FFM/JNI activo (`native.enabled=true`, `hybrid.engine.enabled=true`, `libdrakes_hybrid_ffi.so`/`libodysseia_ffi.so`/`libslimefun_ffi.so` en ambos cargadores; 21-nov, `docs/evidence/embedded-step3-rust-ffm-20261121`)**.
4. Portar el arranque a NeoForge/Forge (módulo fino sobre el mismo núcleo).
5. Solo entonces cambiar la plantilla «Slimefun solitario» del launcher a este modo; mientras tanto el **servidor local gestionado** (proceso aparte invisible) es el camino soportado.

Hasta que los pasos 1–4 pasen, el README no debe afirmar que Fused ejecuta Slimefun «dentro del juego».

## Laboratorio reproducible del paso 1 (2026-11-17)

El laboratorio [tools/embedded-spike](../tools/embedded-spike/README.md) ejecuta
Paperclip en la JVM de un anfitrión Java desechable, con mundo nuevo y puerto
loopback. Incluye control con `user.dir` modificado y un cargador experimental
que redirige rutas e intercepta salida y hooks de apagado. No se integra en el
mod ni en el launcher.

El criterio automático exige `Done`, envío de `stop`, supervivencia del
anfitrión, cero escrituras en su directorio (inotify y SHA del centinela),
conservación de `user.dir` en el modo aislado y finalización sin matar el
proceso. Un servidor que sale con código 0 puede fallar esta prueba: puede
haber matado al anfitrión. Una JVM que sobrevive también puede fallarla si deja
hilos del servidor que impiden finalizar el laboratorio.

El estado y las capturas de cada intento están en
[evidence/embedded-step1-20261117](evidence/embedded-step1-20261117/README.md).
El objetivo global sigue pendiente; los pasos 2–5 conservan sus requisitos.

## Estado por paso (laboratorio, sin cliente gráfico)

- Paso 1: pasa en modo `isolated` — [evidence/embedded-step1-20261117](evidence/embedded-step1-20261117/README.md).
- Paso 3: JNI doble, Log4j2 y Rust FFM/JNI pasan — [JNI](evidence/embedded-step3-jni-20261120/README.md), [Log4j](evidence/embedded-step3-log4j-20261121/README.md), [Rust](evidence/embedded-step3-rust-ffm-20261121/README.md).
- Paso 4: módulos universales NeoForge/Forge/Fabric sobre `common`, cargados en la misma JVM que el monolito 26.2, con ruta JNI propia por cargador — [evidence/embedded-step4-neoforge-forge-20261121](evidence/embedded-step4-neoforge-forge-20261121/README.md).
- Paso 2: **pasa en CI** (cliente Fabric 26.2 real en Xvfb, misma JVM, quickplay, jugador dentro del mundo, `stop` sin matar el cliente) — [evidence/embedded-step2-client-20261122-ci](evidence/embedded-step2-client-20261122-ci/README.md). Segunda corrida con suites ya extraídas: 7 plugins Drakes y el motor Rust activos dentro del cliente, pero **el jar b140 no trae Slimefun** (DrakesTech falla por dependencia) — [evidence/embedded-step2-client-20261123-suites](evidence/embedded-step2-client-20261123-suites/README.md). **Tercera corrida (23-nov) con el núcleo Slimefun 26.x sembrado (`slimefun-core` build 3 desde `port-26x` `9a630d1b5`, CI #45313470220): Slimefun (435 ítems + 158 de DrakesTech) habilitado dentro del cliente, jugador dentro, `stop` sin matar el cliente y `async_shutdown_nag: 0`** — [evidence/embedded-step2-client-20261123-slimefun](evidence/embedded-step2-client-20261123-slimefun/README.md). **Cuarta corrida (24-nov) con el monolito `b141` autocontenido (`26.2-drakes.141`, SHA-256 `30ff10f6…`, CI #45324294951) y extracción previa en `Embed.java`: en un único primer arranque limpio sin siembra externa se habilitan los 10/10 plugins (`Slimefun` + las 9 suites, incluyendo `DrakesTech`, `DrakesServer` y `DrakesMultiverse`; 435 ítems Slimefun + 157 de 8 addons, 474 ingredientes cruzados), `joined_seconds: 44.0`, `client_alive_after_stop: true`, `unsatisfied_link: 0` y `async_shutdown_nag: 0`** — [evidence/embedded-step2-client-20261124-b141](evidence/embedded-step2-client-20261124-b141/README.md).
- Paso 5 (plantilla del launcher): **pendiente**. Los pasos 1–4 y el monolito `b141` con Slimefun + 9 suites en primer arranque ya están verificados con evidencia; falta conectar la plantilla del launcher cuando se integre el arranque embebido en el mod y el launcher.
