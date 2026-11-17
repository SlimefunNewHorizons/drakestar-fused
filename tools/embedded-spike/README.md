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
apagado en las clases cargadas por ese cargador. **Es experimental**: no cubre
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

Los siguientes pasos de `docs/EMBEDDED_SERVER_SPIKE.md` requieren evidencia
propia. Este laboratorio no acredita cliente real, JNI doble, compatibilidad
log4j del cliente, Rust activo ni soporte de ningún loader.
