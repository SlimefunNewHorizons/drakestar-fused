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
2. Probar el arranque dentro de un cliente Fabric 26.2 real (hilo desde el entrypoint, bloqueo hasta puerto abierto) con quickplay; registrar logs y RAM.
3. Resolver 3 y 4 con pruebas reales (JNI doble, log4j).
4. Portar el arranque a NeoForge/Forge (módulo fino sobre el mismo núcleo).
5. Solo entonces cambiar la plantilla «Slimefun solitario» del launcher a este modo; mientras tanto el **servidor local gestionado** (proceso aparte invisible) es el camino soportado.

Hasta que los pasos 1–4 pasen, el README no debe afirmar que Fused ejecuta Slimefun «dentro del juego».
