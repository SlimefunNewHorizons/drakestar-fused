# Paso 3 (parcial): JNI doble, 20 de noviembre de 2026

Estado: **la parte JNI de netty-epoll, JNA y sqlite pasa en laboratorio**. No cubre log4j del cliente,
el motor Rust (apagado por defecto en `perf.yml`) ni un cliente real.

Mismo artefacto que el paso 1 (Purpur 26.2 build 140, SHA-256 `750811c8…0145`), JVM OpenJDK 25.0.5.1,
`--mode isolated --preload-natives`, vía `~/ai-hub/scripts/build_seguro.sh 25`.

Antes de arrancar el servidor, el anfitrión crea su propio cargador (`spike-client`, padre = plataforma,
como el cargador del juego) con los mismos JARs `netty-*`, `jna-*` y `sqlite-jdbc` que trae Paperclip y
carga sus nativas: `Epoll.isAvailable()`, `Native.getNativeSize`, `SQLiteJDBCLoader.initialize()` y una
consulta `select sqlite_version()`. El cliente vanilla trae netty-epoll y JNA; sqlite simula un mod que la incluya.

| | run-1 | run-2 |
|---|---|---|
| Nativas del anfitrión | epoll, JNA, sqlite 3.49.1 | ídem |
| `Done` y `stop` | sí | sí |
| `UnsatisfiedLinkError` en consola | 0 | 0 |
| SQLite del servidor | `kernel.db`, `drakes_modalities.db` | 28 672 + 12 288 bytes |
| epoll del servidor (su cargador, tras `stop`) | sin medir (stdout cerrado) | `true` |
| epoll del anfitrión tras `stop` | sin medir | `true` |
| Anfitrión vivo, salida 0 sin forzar, 0 escrituras | sí | sí |
| Duración / RSS máx. | 105,8 s / 1,04 GiB | 99,9 s / 0,98 GiB |

Por qué funciona: netty, JNA y sqlite-jdbc extraen la `.so` a un archivo temporal con nombre único por
carga, así que cada cargador enlaza su propia copia y la regla «una `.so` por cargador» no se rompe.
Quedaría expuesto un mod que use `System.loadLibrary` sobre la misma ruta fija que el servidor.

run-1 usó una versión previa del sondeo (sin `natives-after-stop.txt`); sus hashes de fuentes difieren.
run-2 corresponde a las fuentes de este commit.

Pendiente del paso 3: log4j/SLF4J compartidos con el cliente (riesgo 4) y el motor Rust por FFM
activado (`native.enabled=true`) con otro cargador del mismo `.so`.
