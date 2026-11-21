# Paso 3 (parcial): aislamiento de Log4j2 (`-Dlog4j.configurationFile`) + JNI doble, 21 de noviembre de 2026

Estado: **el aislamiento de Log4j2 frente a `-Dlog4j.configurationFile` del anfitrión pasa en laboratorio junto con JNI doble (`netty-epoll`, `JNA`, `sqlite`)**. No cubre aún el motor Rust por FFM activo (`native.enabled=true`) ni un cliente Fabric/Forge/NeoForge real.

Mismo artefacto que el paso 1 (Purpur 26.2 build 140, SHA-256 `750811c8…0145`), JVM OpenJDK 25.0.5.1, vía `~/ai-hub/scripts/build_seguro.sh 25`.

## Causa raíz reproducida (`control/`)

Los launchers de Minecraft configuran Log4j2 del cliente mediante la propiedad global de JVM `-Dlog4j.configurationFile=<client-log4j2.xml>`. Aunque el servidor embebido se cargue en un `URLClassLoader` aislado (`SpikeLoader`) con sus propios JARs `log4j-api` y `log4j-core` y su propio recurso `log4j2.xml`, `org.apache.logging.log4j.util.PropertiesUtil$Environment` ordena las fuentes de propiedades por prioridad ascendente (`PropertySource$Comparator`):

1. `SystemPropertiesPropertySource` (`priority = 0`): invoca `System.getProperty("log4j2.configurationFile")` y `System.getProperty("log4j.configurationFile")`.
2. `EnvironmentPropertySource` (`priority = 100`).
3. `PropertyFilePropertySource` (`log4j2.component.properties`, `priority = 200`).

Por tanto:
- Sin interceptar `log4j.configurationFile` / `log4j2.configurationFile` en `SpikeHooks.property` (corrida [`control/`](control/result.json)), `SystemPropertiesPropertySource` dentro del cargador del servidor lee la ruta `client-log4j2.xml` del anfitrión: las **99 líneas** del servidor se vuelcan en `client-logs/latest.log` con el patrón `[CLIENT ...]`, `server/logs/latest.log` no llega a crearse (`null`) y la consola del servidor pierde su appender (`ready=false`, timeout a los 241,22 s).
- Un recurso `log4j2.component.properties` por cargador (`priority = 200`) tampoco basta por sí solo porque `SystemPropertiesPropertySource` (`priority = 0`) tiene precedencia estricta.

## Solución verificada (`run-1/` y `run-2/`)

`SpikeLoader` ya redirige las llamadas a `System.getProperty` de las clases cargadas en el servidor hacia `SpikeHooks.property`. Al filtrar `"log4j.configurationFile"` y `"log4j2.configurationFile"` en `SpikeHooks.property` (devolviendo `null` / `fallback` sólo para el cargador del servidor, sin alterar `System.getProperties()` del anfitrión):

- El cargador del anfitrión (`spike-client-log`) conserva `-Dlog4j.configurationFile=client-log4j2.xml`, escribe `[CLIENT SpikeClient] SPIKE-CLIENT before server` antes del arranque y `[CLIENT SpikeClient] SPIKE-CLIENT after stop` tras la parada del servidor, y mantiene su `LoggerContext` iniciado (`context.started=true`).
- El cargador del servidor (`SpikeLoader`) no ve la propiedad global del cliente, recurre a su propio `findResource("log4j2.xml")`, escribe su arranque completo (`Done`) en `server/logs/latest.log` (`10 414` bytes) y en consola, y **0 líneas** del servidor se fugan a `client-logs/latest.log`.
- Ejecutado simultáneamente con `--preload-natives` (JNI doble de `netty-epoll`, `JNA` y `sqlite 3.49.1`).

| Métrica | `control` (sin filtro en `SpikeHooks.property`) | `run-1` (`--preload-natives --host-log4j`) | `run-2` (`--preload-natives --host-log4j`, `SpikeLoader` en `HEAD`) |
|---|---|---|---|
| `client_log_lines` | 100 (1 propia + 99 fugadas del servidor) | 2 (sólo `before server` y `after stop`) | 2 (sólo `before server` y `after stop`) |
| `client_log_foreign_lines` | 99 | 0 | 0 |
| `server_log_bytes` / `server_log_has_done` | `null` / `false` | `10 414` / `true` | `10 414` / `true` |
| `client_log_after_stop` | `null` | `context.started=true` | `context.started=true` |
| JNI doble (`UnsatisfiedLinkError` / epoll tras `stop`) | no activado | `0` / `true` en ambos cargadores | `0` / `true` en ambos cargadores |
| Anfitrión vivo, salida 0 sin forzar, 0 escrituras | no (timeout 241,22 s, salida 143) | sí (`step1_pass=true`) | sí (`step1_pass=true`) |
| Duración / RSS máx. | 241,22 s / 0,97 GiB | 105,24 s / 0,92 GiB | 104,96 s / 0,98 GiB |

Pendiente del paso 3: motor Rust por FFM activado (`native.enabled=true`) con otro cargador.
