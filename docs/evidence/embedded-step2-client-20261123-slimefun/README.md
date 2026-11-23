# Paso 2c — Slimefun dentro de un cliente Fabric 26.2 real (CI, 2026-11-23)

Corrida: GitHub Actions #45220671700, commit `f17aaeb`. La primera corrida extrae las suites; antes de la segunda se siembra en `plugins/` el núcleo Slimefun 26.x publicado (`maven.drakescraft.cl`, `slimefun-core-11.0-Universal-26.x-20261029.125426-2.jar`, SHA-256 `4e8324718a5039f7141b5c5b888db246d5586ce4a256fe26700667c24ba235a7`), porque el monolito b140 no lo incluye.

Resultado (`result.json`): **pass**: misma JVM (`Embed.main` desde el entrypoint Fabric), servidor listo a los 23 s, jugador dentro a los 27 s, `stop` sin matar el cliente, 0 `UnsatisfiedLinkError`, RSS máx. 2,60 GiB (2 729 428 KiB).

Dentro del cliente (`server-log-excerpt.txt`):

- `Enabling Slimefun v11.0-Universal-26.x-SNAPSHOT`, `Slimefun has finished loading in 972ms`, **435 ítems de Slimefun + 157 de 1 addon**.
- `DrakesTech` se habilita (antes fallaba con `UnknownDependencyException: [Slimefun]`): InfinityExpansion, Supreme, FluffyMachines, Nanotech (158 ítems), FoxyMachines; `Cross-addon recipe ingredients resolved: {Slimefun=474}`.
- Plugins habilitados: DrakesBio, DrakesCombat, DrakesCore (con `libslimefun_ffi.so` cargada), DrakesGenerators, DrakesMagic, DrakesMultiverse, DrakesTech, DrakesUtility, Slimefun.

Pendiente (no bloquea el paso 2, sí la plantilla del paso 5):

- El monolito publicado aún no trae Slimefun; aquí se sembró aparte.
- Slimefun deja tareas async al parar (`Nag author … not properly shutting down its async tasks`); el cliente sobrevive, pero hay que revisarlo antes de reinicios repetidos en la misma sesión.
- Siguen los fallos de contenido del b140: `plugin.yml` inválido (Odysseia, `:` sin comillas) y `DrakesMultiverse` sin `config.yml`.
- Motor Rust de Purpur y de Slimefun apagados por configuración en esta corrida (el FFM activo se probó en el paso 3).
