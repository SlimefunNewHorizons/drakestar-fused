# Paso 2 — cliente Fabric 26.2 real con el monolito embebido (CI, 2026-11-22)

Corrida: GitHub Actions `Spike paso 2 (cliente Fabric 26.2 real)` #45077407156, commit `b55b112`, Temurin 25.0.5, Xvfb, `-Xmx4G`.

Resultado (`result.json`): **pass**.

- Misma JVM: `Embed.main` lanzado desde el entrypoint del mod Fabric (`fused_embedded_spike`), pid 3648; `host.minecraft.distinct=true` (clases del servidor en otro cargador que las del cliente).
- Servidor DrakesPurpur `26.2-140-experimental/horizon-rust-hybrid-engine@ab5d46e`: `Done (9.825s)`, listo a los 23 s.
- Quickplay: el cliente conecta a `127.0.0.1:59687`, `SpikeJack joined the game` a los 30 s; captura `in-world.png`.
- `stop`: el servidor guarda y se apaga; `server.exit.intercepted=0`, el cliente sigue vivo (`client_alive_after_stop=true`, `after-stop.png`).
- `UnsatisfiedLinkError`: 0. RSS máximo cliente+servidor: 2.15 GiB.
- Los 401 de Mojang/Realms son esperables (cuenta offline, token `0`).

Límite de esta evidencia: es el primer arranque del monolito; `DrakesBundled` extrae las 9 suites a `plugins/` pero en esta corrida se inicializaron 0 plugins (no hay líneas `Enabling`). Que Slimefun y las suites se habiliten dentro del cliente queda por demostrar en una segunda corrida con `plugins/` ya poblado.
