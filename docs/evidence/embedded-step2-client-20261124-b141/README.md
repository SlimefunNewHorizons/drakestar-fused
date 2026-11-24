# Paso 2d — Monolito DrakesPurpur b141 con Slimefun 26.x + 9 suites en primer arranque (CI, 2026-11-24)

Corrida: GitHub Actions [#45324294951](https://github.com/SlimefunNewHorizons/drakestar-fused/actions/runs/45324294951), commit `1cfdf4c`.

## Qué cambió respecto a 2c (`b140` + siembra externa)

1. **Monolito `b141` autocontenido**: compilado en GitHub Actions ([#45320688989](https://github.com/SlimefunNewHorizons/DrakesPurpur/actions/runs/45320688989), `DrakesPurpur` `feat/slimefun-native-engine` `197511e3f`) y publicado en la pre-release [`26.2-drakes.141`](https://github.com/SlimefunNewHorizons/DrakesPurpur/releases/tag/26.2-drakes.141) (`purpur-paperclip-26.2.build.141-stable.jar`, SHA-256 `30ff10f692c12df7a8260ed0f1ba1fa898968b7ee561d4ac087545a19a1e7ffe`, conservando `v26.2.140-stable` como `Latest`).
   - Incluye en `drakes-bundled/` los **10 jars** validados por `scripts/empaquetar_monolito.sh`: `Slimefun.jar` (`slimefun-core-11.0-Universal-26.x-20261124.023000-3.jar`, SHA-256 `3941762f3cb99f2b5a0eaeae234b2380527cf495c5039660d989bddbe6455579`) + las 9 suites de `Drakes-Suites@26.x` (`DrakesBio`, `DrakesCombat`, `DrakesCore`, `DrakesGenerators`, `DrakesMagic`, `drakes-multiverse`, `DrakesServer`, `DrakesTech`, `DrakesUtility`).
   - Corrige los dos defectos de contenido que tenía `b140`: `DrakesServer` tiene `plugin.yml` válido y `drakes-multiverse` incluye su `config.yml`.
2. **Extracción previa al escaneo de Paper en el primer arranque**: `Embed.java` invoca `org.purpurmc.purpur.bundled.DrakesBundledPlugins.initialize()` desde `serverLoader` (`SpikeLoader`, con rutas redirigidas a `server/`) antes de `Main.main(...)`. Así `PluginInitializerManager.load()` encuentra los 10 jars en `server/plugins/` en el **primer arranque** de una instancia limpia, sin necesitar un segundo arranque (`--seed-plugins`) ni descarga externa de `Slimefun.jar`.

## Resultado (`result.json`)

- `pass: true`
- `same_jvm: "Embed.main from Fabric client entrypoint"`
- `server_ready_seconds: 34.0`, `joined_seconds: 44.0` (`Done (17.485s)!`)
- `client_alive_after_stop: true`, `host_alive_after_stop_marker: true`
- `unsatisfied_link: 0`, `async_shutdown_nag: 0`
- `peak_rss_kib: 2249868` (2,15 GiB)
- **10/10 plugins habilitados en el primer arranque**:
  - `DrakesBio`, `DrakesCombat`, `DrakesCore`, `DrakesGenerators`, `DrakesMagic`, `DrakesMultiverse`, `DrakesServer`, `DrakesTech`, `DrakesUtility`, `Slimefun`
  - `Slimefun v11.0-Universal-26.x-SNAPSHOT`: `435 Items from Slimefun, 157 Items from 8 Addons`
  - `DrakesTech`: `Cross-addon recipe ingredients resolved: {Slimefun=474}`, `NanotechModule (158 items, 15 máquinas, 8 multibloques)`, `InfinityExpansionModule`, `SupremeModule`, `FluffyMachinesModule`, `FoxyMachinesModule`.
  - `DrakesServer` y `DrakesMultiverse` inicializados y deshabilitados limpiamente al recibir `stop`.
