#!/usr/bin/env python3
"""Paso 2: cliente Fabric 26.2 real (Xvfb + mesa) con el monolito embebido y quickplay.

Supervisa: espera 'server.ready', captura pantalla, comprueba que el jugador entró,
envía 'stop' por stdin del cliente y verifica que el cliente sigue vivo.
"""
import argparse, json, os, shutil, socket, subprocess, sys, time, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPIKE = HERE.parent


def shot(display, dest):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "x11grab", "-video_size", "1280x720",
                    "-i", display, "-frames:v", "1", str(dest)], check=False, timeout=30)


def rss_kib(pid):
    try:
        return int(next(l for l in open(f"/proc/{pid}/status") if l.startswith("VmRSS")).split()[1])
    except (OSError, StopIteration):
        return 0


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--client-root", type=Path, required=True)
    p.add_argument("--jar", type=Path, required=True)
    p.add_argument("--mojang-cache", type=Path)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--java-home", type=Path, default=Path("/usr/lib/jvm/java-25-openjdk-amd64"))
    p.add_argument("--display", default=":77")
    p.add_argument("--timeout", type=int, default=900)
    p.add_argument("--join-timeout", type=int, default=420)
    p.add_argument("--seed-plugins", type=Path, help="plugins/ de una corrida previa (el 1er arranque solo extrae las suites)")
    a = p.parse_args()
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    inst, server, build = out / "instance", out / "server", out / "build"
    for d in (inst / "mods", server, build):
        d.mkdir(parents=True)
    launch = json.load(open(a.client_root / "launch.json"))
    javac = str(a.java_home / "bin/javac")
    stubs = SPIKE.parent.parent / "fabric/stubs/net/fabricmc/api/ClientModInitializer.java"
    srcs = [*map(str, sorted(SPIKE.glob("*.java"))), str(HERE / "SpikeClientMod.java"), str(stubs)]
    subprocess.run([javac, "--release", "25", "-d", str(build), *srcs], check=True)
    shutil.rmtree(build / "net")  # la API real la aporta Fabric Loader
    mod = inst / "mods/fused-embedded-spike.jar"
    with zipfile.ZipFile(mod, "w") as z:
        z.write(HERE / "mod/fabric.mod.json", "fabric.mod.json")
        for c in build.rglob("*.class"):
            z.write(c, c.relative_to(build).as_posix())
    if a.seed_plugins:
        shutil.copytree(a.seed_plugins, server / "plugins")
    # Servidor desechable, igual que probe.py.
    if a.mojang_cache:
        (server / "cache").mkdir()
        shutil.copy2(a.mojang_cache, server / "cache" / a.mojang_cache.name)
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=spike\n"
        "level-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n"
        "enable-rcon=false\nenable-query=false\nmax-players=1\nenforce-secure-profile=false\n")
    (server / "log4j2.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?><Configuration status="WARN">'
        '<Appenders><Console name="Console" target="SYSTEM_OUT">'
        '<PatternLayout pattern="[%d{HH:mm:ss} %level] [srv] %msg%n"/></Console>'
        f'<File name="File" fileName="{server}/logs/latest.log">'
        '<PatternLayout pattern="[%d{HH:mm:ss} %level] %msg%n"/></File></Appenders>'
        '<Loggers><Root level="info"><AppenderRef ref="Console"/>'
        '<AppenderRef ref="File"/></Root></Loggers></Configuration>')
    # Primer arranque: la pantalla de accesibilidad bloquea el quickplay (join-7.png, run 45076538078).
    (inst / "options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\n"
                                      "joinedFirstServer:true\ntutorialStep:none\nnarrator:0\n")
    root = a.client_root.resolve()
    cmd = [str(a.java_home / "bin/java"), "-Xmx4G", *launch["jvmArgs"],
           f"-Dfused.spike.server={server}", f"-Dfused.spike.jar={a.jar.resolve()}",
           "-cp", ":".join(launch["classpath"]), launch["mainClass"],
           "--username", "SpikeJack", "--uuid", "00000000-0000-0000-0000-000000000001",
           "--accessToken", "0", "--userType", "legacy", "--version", "26.2",
           "--gameDir", str(inst), "--assetsDir", str(root / "assets"),
           "--assetIndex", launch["assetIndex"], "--width", "1280", "--height", "720",
           "--quickPlayMultiplayer", f"127.0.0.1:{port}"]
    (out / "command.json").write_text(json.dumps(cmd, indent=1))
    xvfb = subprocess.Popen(["Xvfb", a.display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    env = dict(os.environ, DISPLAY=a.display, LIBGL_ALWAYS_SOFTWARE="1", GALLIUM_DRIVER="llvmpipe")
    console = open(out / "client-console.txt", "wb")
    client = subprocess.Popen(cmd, cwd=inst, stdin=subprocess.PIPE, stdout=console,
                              stderr=subprocess.STDOUT, env=env, start_new_session=True)
    res = {"port": port, "client_pid": client.pid, "same_jvm": "Embed.main from Fabric client entrypoint"}
    started, peak, stage = time.monotonic(), 0, "boot"
    def text():
        return (out / "client-console.txt").read_text(errors="replace")
    try:
        while time.monotonic() - started < a.timeout and client.poll() is None:
            peak = max(peak, rss_kib(client.pid))
            t = text()
            if stage == "boot" and ("SPIKE-CLIENT embed.failed=" in t or "server.ready=false" in t):
                res["boot_failure"] = next(l for l in t.splitlines() if "SPIKE-CLIENT" in l)
                break
            if stage == "boot" and "SPIKE-CLIENT server.ready port=" in t:
                res["server_ready_seconds"] = round(time.monotonic() - started, 1)
                stage = "join"
                join_at, shots = time.monotonic(), 0
            if stage == "join" and time.monotonic() - join_at > 60 * (shots + 1):
                shots += 1
                shot(a.display, out / f"join-{shots}.png")
            if stage == "join" and time.monotonic() - join_at > a.join_timeout:
                # Sin unirse: volcar hilos del cliente para ver dónde se cuelga.
                with open(out / "client-threads.txt", "w") as f:
                    subprocess.run([str(a.java_home / "bin/jcmd"), str(client.pid), "Thread.print", "-l"],
                                   stdout=f, stderr=subprocess.STDOUT, check=False, timeout=60)
                res["join_timeout"] = True
                break
            if stage == "join" and ("SpikeJack joined the game" in t or "SpikeJack[/" in t):
                res["joined_seconds"] = round(time.monotonic() - started, 1)
                time.sleep(25)
                shot(a.display, out / "in-world.png")
                client.stdin.write(b"stop\n"); client.stdin.flush()
                res["stop_sent"] = True
                stage = "stopping"
                stop_at = time.monotonic()
            if stage == "stopping" and time.monotonic() - stop_at > 40:
                (server / "check-host-alive").write_text("x")
                time.sleep(15)
                shot(a.display, out / "after-stop.png")
                res["client_alive_after_stop"] = client.poll() is None
                stage = "done"
                break
            if stage == "boot" and time.monotonic() - started > 60 and not (out / "boot.png").exists():
                shot(a.display, out / "boot.png")
            time.sleep(1)
    finally:
        res["stage"] = stage
        res["seconds"] = round(time.monotonic() - started, 1)
        res["peak_rss_kib"] = peak
        res["client_exit"] = client.poll()
        if client.poll() is None:
            os.killpg(client.pid, 15)
            try: client.wait(30)
            except subprocess.TimeoutExpired: os.killpg(client.pid, 9)
        xvfb.terminate()
        t = text()
        res["server_done"] = "Done (" in (server / "logs/latest.log").read_text(errors="replace") \
            if (server / "logs/latest.log").exists() else False
        res["unsatisfied_link"] = t.count("UnsatisfiedLinkError")
        res["async_shutdown_nag"] = t.count("not properly shutting down its async tasks")
        res["plugins_enabled"] = sorted({l.split("Enabling ")[1].split(" ")[0] for l in t.splitlines()
                                         if "] Enabling " in l})
        res["host_alive_after_stop_marker"] = (server / "host-alive-after-stop.txt").exists()
        res["pass"] = bool(res.get("joined_seconds") and res.get("client_alive_after_stop"))
        (out / "result.json").write_text(json.dumps(res, indent=2))
        print(json.dumps(res, indent=2))
    return 0 if res["pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
