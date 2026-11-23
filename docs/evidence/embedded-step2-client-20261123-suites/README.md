# Paso 2b — cliente Fabric 26.2 con las suites ya extraídas (CI, 2026-11-23)

Corrida: GitHub Actions #45205528946, commit `313a65b`. La primera corrida extrae las suites; la segunda arranca con ese `plugins/` (`--seed-plugins`) dentro del mismo cliente Fabric.

Resultado (`result.json`): **pass** del embebido (misma JVM, quickplay, jugador dentro, `stop` sin matar el cliente, 0 `UnsatisfiedLinkError`, RSS máx. 2.20 GiB).

Plugins habilitados dentro del cliente: DrakesCore (motor Rust: `libslimefun_ffi.so` cargada), DrakesBio, DrakesCombat, DrakesGenerators, DrakesMagic, DrakesUtility (con sus módulos warfare, nukes, ore_chunks, guidetools, worldedit_sf…).

Fallos que apuntan al contenido del **monolito b140**, no al embebido (no reproducido aún fuera del cliente):

- **Slimefun no está**: `DrakesTech` no carga — `UnknownDependencyException: [Slimefun]`.
- Un plugin con `plugin.yml` inválido: descripción sin comillas con `:` («…Standalone: Motor Odysseia…», línea 6, col 58).
- `DrakesMultiverse`: `config.yml` no incluido en `drakes-multiverse.jar` → se deshabilita.

Conclusión: el paso 2 (cliente real + servidor embebido) pasa; **Slimefun dentro del juego sigue sin demostrarse** porque el jar b140 publicado no trae el núcleo Slimefun. Siguiente: release del monolito con Slimefun incluido (o sembrar Slimefun4 26.x en `plugins/`) y repetir este workflow.
