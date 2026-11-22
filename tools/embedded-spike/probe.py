#!/usr/bin/env python3
"""Reproducible step 1 experiment. Use build_seguro.sh 25 for compilation/run.

The child JVM is a disposable laboratory host, not a separate server launched by
a Minecraft client. Paperclip is loaded inside that host's JVM by Embed.java.
"""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import selectors
import shutil
import signal
import socket
import subprocess
import struct
import time
import zipfile


def snapshot(root):
    return {
        str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in sorted(root.rglob("*")) if p.is_file()
    }


def run(args):
    jar = args.jar.resolve(strict=True)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    host, server, classes = (output / n for n in ("host", "server", "classes"))
    for p in (host, server, classes):
        p.mkdir()
    (host / "sentinel.txt").write_text("Host must remain unchanged.\n")
    if args.mojang_cache:
        cache = server / "cache"
        cache.mkdir()
        shutil.copy2(args.mojang_cache, cache / args.mojang_cache.name)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "level-name=spike\nlevel-type=minecraft:flat\nview-distance=2\n"
        "simulation-distance=2\nspawn-protection=0\nenable-rcon=false\n"
        "enable-query=false\nmax-players=1\n")
    (server / "log4j2.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?><Configuration status="WARN">'
        '<Appenders><Console name="Console" target="SYSTEM_OUT">'
        '<PatternLayout pattern="[%d{HH:mm:ss} %level] %msg%n"/></Console>'
        f'<File name="File" fileName="{server}/logs/latest.log">'
        '<PatternLayout pattern="[%d{HH:mm:ss} %level] %msg%n"/></File></Appenders>'
        '<Loggers><Root level="info"><AppenderRef ref="Console"/>'
        '<AppenderRef ref="File"/></Root></Loggers></Configuration>')
    if args.host_log4j:
        (server / "client-log4j2.xml").write_text(
            '<?xml version="1.0" encoding="UTF-8"?><Configuration status="WARN">'
            f'<Appenders><File name="Client" fileName="{server}/client-logs/latest.log">'
            '<PatternLayout pattern="[CLIENT %c] %msg%n"/></File></Appenders>'
            '<Loggers><Root level="info"><AppenderRef ref="Client"/></Root></Loggers></Configuration>')
    rust_native_hashes = {}
    if args.rust_ffm:
        if not args.natives_dir:
            raise ValueError("--rust-ffm requires --natives-dir")
        natives_src = args.natives_dir.resolve(strict=True)
        natives_dst = server / "natives"
        natives_dst.mkdir()
        for name in ("libdrakes_hybrid_ffi.so", "libodysseia_ffi.so", "libslimefun_ffi.so"):
            src_file = (natives_src / name).resolve(strict=True)
            dst_file = natives_dst / name
            shutil.copy2(src_file, dst_file)
            rust_native_hashes[name] = hashlib.sha256(dst_file.read_bytes()).hexdigest()
        drakes_data = server / "drakes-data"
        drakes_data.mkdir()
        (drakes_data / "perf.yml").write_text(
            "enabled: true\n"
            "status:\n  enabled: true\n  file: drakes-data/perf-status.json\n  interval-seconds: 10\n"
            "load-governor:\n  enabled: false\n"
            "hybrid:\n  engine:\n    enabled: true\n"
            "native:\n  enabled: true\n  dir: natives\n  ring-capacity: 64\n  max-entries: 8192\n"
            "  odysseia:\n    pulses: true\n    aura: true\n"
            "  slimefun:\n    fill: true\n    sum: true\n    price: true\n    tick-engine: true\n"
            "  hybrid:\n    enabled: true\n    min-batch: 2048\n    bits: true\n    noise: true\n"
            "    region: true\n    stats: true\n    prefetch:\n      enabled: false\n")
    before = snapshot(host)
    # The host initially contains only sentinel.txt. Watching its directory also
    # catches transient new subtrees, which before/after hashes alone miss.
    libc = ctypes.CDLL(None, use_errno=True)
    watch_fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
    if watch_fd < 0 or libc.inotify_add_watch(watch_fd, os.fsencode(host), 0xFCE) < 0:
        raise OSError(ctypes.get_errno(), "Cannot observe host writes")
    host_events = []

    def read_host_events():
        while True:
            try:
                events = os.read(watch_fd, 65536)
            except BlockingIOError:
                return
            pos = 0
            while pos < len(events):
                _, mask, _, length = struct.unpack_from("iIII", events, pos)
                name = events[pos + 16:pos + 16 + length].split(b"\0")[0]
                host_events.append({"mask": hex(mask), "name": os.fsdecode(name)})
                pos += 16 + length
    java_home = Path(os.environ["JAVA_HOME"])
    repo_root = Path(__file__).resolve().parents[2]
    loader_jar_hashes = {}
    loader_jvm_props = []
    if args.loader_mods:
        stubs_out = output / "loader-stubs"
        for loader_name, stub_dirs in (
            ("fabric", [repo_root / "fabric/stubs"]),
            ("neoforge", [repo_root / "neoforge/stubs", repo_root / "fabric/stubs/net/minecraft"]),
            ("forge", [repo_root / "forge/stubs", repo_root / "fabric/stubs/net/minecraft"]),
        ):
            dest = stubs_out / loader_name
            dest.mkdir(parents=True, exist_ok=True)
            java_files = []
            for sdir in stub_dirs:
                java_files.extend(sorted(sdir.rglob("*.java")))
            subprocess.run([str(java_home / "bin/javac"), "--release", "17", "-d", str(dest),
                            *map(str, java_files)], check=True)
        jar_paths = []
        for mod_dir, prefix in (
            ("fabric", "drakestar-fused-fabric-universal-"),
            ("neoforge", "drakestar-fused-neoforge-universal-"),
            ("forge", "drakestar-fused-forge-universal-"),
        ):
            candidates = sorted((repo_root / mod_dir / "build/libs").glob(f"{prefix}*.jar"))
            candidates = [c for c in candidates if not c.name.endswith("-sources.jar")]
            if not candidates:
                raise FileNotFoundError(f"Missing built universal jar for {mod_dir} in {repo_root / mod_dir / 'build/libs'}")
            chosen = candidates[-1].resolve(strict=True)
            jar_paths.append(str(chosen))
            loader_jar_hashes[chosen.name] = hashlib.sha256(chosen.read_bytes()).hexdigest()
        loader_jvm_props = [
            f"-Ddrakestar.fused.loaderJars={os.pathsep.join(jar_paths)}",
            f"-Ddrakestar.fused.loaderStubs={stubs_out}",
        ]
    source_hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
        for p in sorted(Path(__file__).parent.iterdir()) if p.suffix in (".java", ".py")}
    subprocess.run([str(java_home / "bin/javac"), "-d", str(classes),
                    *map(str, sorted(Path(__file__).parent.glob("*.java")))], check=True)
    java = str(java_home / "bin/java")
    version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True)
    command = [java, "-Xms256m", "-Xmx2g", "-XX:ActiveProcessorCount=2",
               "--enable-native-access=ALL-UNNAMED",
               *loader_jvm_props,
               "-cp", str(classes),
               "Embed", str(jar), str(server), args.mode,
               *(["natives"] if args.preload_natives else []),
               *(["log4j"] if args.host_log4j else []),
               *(["rust"] if args.rust_ffm else []),
               *(["rust-jni-conflict"] if args.rust_jni_conflict else []),
               *(["loaders"] if args.loader_mods else []),
               *(["loader-jni-conflict"] if args.loader_jni_conflict else [])]
    started = time.monotonic()
    proc = subprocess.Popen(command, cwd=host, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            start_new_session=True)
    ready = stop_sent = stopped = timed_out = alive = forced = False
    stop_at = None
    peak_rss = 0
    data = b""
    selector = selectors.DefaultSelector()
    selector.register(proc.stdout, selectors.EVENT_READ)
    try:
        with (output / "console.txt").open("wb") as log:
            while True:
                read_host_events()
                try:
                    status = Path(f"/proc/{proc.pid}/status").read_text()
                    peak_rss = max(peak_rss, int(status.split("VmRSS:")[1].split()[0]))
                except (FileNotFoundError, IndexError):
                    pass
                for key, _ in selector.select(0.1):
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if chunk:
                        log.write(chunk)
                        log.flush()
                        data += chunk
                    else:
                        selector.unregister(key.fileobj)
                ready = b"Done (" in data
                stopped = b"Stopping server" in data
                alive = (server / "host-alive-after-stop.txt").exists()
                if ready and not stop_sent:
                    proc.stdin.write(b"stop\n")
                    proc.stdin.flush()
                    stop_sent, stop_at = True, time.monotonic()
                if stopped and stop_at and time.monotonic() - stop_at > 15:
                    (server / "check-host-alive").touch()
                if proc.poll() is not None and not selector.get_map():
                    break
                if (server / "startup-failed.txt").exists():
                    break
                if time.monotonic() - started > args.timeout:
                    timed_out = True
                    break
    finally:
        if proc.poll() is None:
            forced = True
            os.killpg(proc.pid, signal.SIGTERM)
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
        selector.close()
        proc.stdout.close()
        proc.stdin.close()
        read_host_events()
        os.close(watch_fd)
    after = snapshot(host)
    with zipfile.ZipFile(jar) as archive:
        versions = archive.read("META-INF/versions.list").decode().strip()
    result = {
        "artifact": jar.name, "artifact_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "paperclip_versions": versions, "java": (version.stdout + version.stderr).strip(),
        "same_jvm": "Paperclip invoked reflectively by Embed, no server subprocess",
        "mode": args.mode,
        "host_pid": proc.pid, "loopback_port": port,
        "ready": ready, "stop_sent": stop_sent, "stopping_logged": stopped,
        "host_survived_stop": alive, "host_shutdown_logged": b"SPIKE host.shutdown" in data,
        "host_shutdown_marker": (server / "host-shutdown.txt").exists(),
        "exit_code": proc.returncode, "timed_out": timed_out,
        "forced_termination": forced,
        "startup_failed": (server / "startup-failed.txt").exists(),
        "seconds": round(time.monotonic() - started, 2), "peak_rss_kib": peak_rss,
        "host_files_created": sorted(after.keys() - before.keys()),
        "host_files_modified": sorted(k for k in before if after.get(k) != before[k]),
        "host_files_deleted": sorted(before.keys() - after.keys()),
        "host_write_events": host_events,
        "host_before": before, "host_after": after,
        "nio_cwd_unchanged": b"SPIKE cwd.unchanged=true" in data,
        "host_minecraft_absent": b"SPIKE host.minecraft.absent=true" in data,
        "host_minecraft_distinct": b"SPIKE host.minecraft.distinct=true" in data,
        "probe_sources_sha256": source_hashes,
        "server_files": sorted(str(p.relative_to(server)) for p in server.rglob("*") if p.is_file()),
        "preload_natives": args.preload_natives,
        "host_natives": [l for l in data.decode(errors="replace").splitlines() if ("SPIKE host.native" in l or "SPIKE server.native" in l)],
        "unsatisfied_link": data.count(b"UnsatisfiedLinkError"),
        "natives_after_stop": ((server / "natives-after-stop.txt").read_text().splitlines()
                               if (server / "natives-after-stop.txt").exists() else []),
        "server_sqlite_files": {str(p.relative_to(server)): p.stat().st_size for p in server.rglob("*.db")},
        "host_log4j": args.host_log4j,
        **(log4j_report(server) if args.host_log4j else {}),
        "rust_ffm": args.rust_ffm,
        **(rust_report(server, data, rust_native_hashes, args.rust_jni_conflict) if args.rust_ffm else {}),
        "loader_mods": args.loader_mods,
        **(loader_mods_report(server, loader_jar_hashes, args.loader_jni_conflict) if args.loader_mods else {}),
        "step1_pass": bool(ready and stop_sent and alive and before == after
                           and not host_events and not forced and not timed_out and proc.returncode == 0),
    }
    (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if not k.startswith(("host_before", "host_after", "server_files"))}, indent=2))
    return 0 if result["step1_pass"] else 1


def log4j_report(server):
    """Where each logger's lines ended up (riesgo 4: estáticos log4j compartidos)."""
    client = server / "client-logs/latest.log"
    own = server / "logs/latest.log"
    lines = client.read_text(errors="replace").splitlines() if client.exists() else []
    after = server / "client-log-after-stop.txt"
    return {
        "client_log_lines": len(lines),
        "client_log_own_lines": [l for l in lines if l.startswith("[CLIENT SpikeClient]")],
        "client_log_foreign_lines": sum(not l.startswith("[CLIENT SpikeClient]") for l in lines),
        "server_log_bytes": own.stat().st_size if own.exists() else None,
        "server_log_has_done": own.exists() and "Done (" in own.read_text(errors="replace"),
        "client_log_after_stop": after.read_text().strip() if after.exists() else None,
    }


def rust_report(server, data, rust_native_hashes, jni_conflict):
    """Step 3 (Rust FFM + JNI): host and embedded server loading libdrakes_hybrid_ffi/libodysseia_ffi/libslimefun_ffi."""
    text = data.decode(errors="replace")
    own = server / "logs/latest.log"
    server_text = own.read_text(errors="replace") if own.exists() else text
    after = server / "rust-ffm-after-stop.txt"
    after_lines = after.read_text(errors="replace").splitlines() if after.exists() else []
    markers = ("[Native]", "[Hybrid]", "[DrakesPurpur]", "[RustEngine]", "already loaded in another classloader")
    return {
        "rust_jni_conflict": jni_conflict,
        "rust_natives_sha256": rust_native_hashes,
        "host_rust_ffm": [l for l in text.splitlines() if "SPIKE host.rust.ffm=" in l],
        "server_rust_logs": [l for l in server_text.splitlines() if any(m in l for m in markers)],
        "rust_after_stop": after_lines,
        "rust_all_active": bool(
            after_lines
            and any("enabled=true tickEngineAllowed=true" in l and "pulseCounter=org.purpurmc.purpur.nativelib.DrakesNative$Odysseia$Native" in l for l in after_lines)
            and any("libraryLoaded=true bitsEnabled=true" in l and "parityOk=true" in l for l in after_lines)
            and any("kernelBridge.isRustLoaded=true SlimefunRustEngine.isNativeLinked=true" in l for l in after_lines)
        ),
    }


def loader_mods_report(server, loader_jar_hashes, jni_conflict):
    """Step 4 (NeoForge, Forge and Fabric universal mod jars in the same JVM as embedded DrakesPurpur)."""
    after = server / "loader-mods-after-stop.txt"
    lines = after.read_text(errors="replace").splitlines() if after.exists() else []
    start_ok = all(
        any(l.startswith(f"start.loader={name} ") and "initialized=true" in l and "pluginsDirExists=true" in l and "rustLoaded=true" in l for l in lines)
        for name in ("neoforge", "forge", "fabric")
    )
    stop_ok = all(
        any(l.startswith(f"stop.loader={name} ") and "initialized=false" in l and "rustLoaded=false" in l for l in lines)
        for name in ("neoforge", "forge", "fabric")
    )
    return {
        "loader_jni_conflict": jni_conflict,
        "loader_jars_sha256": loader_jar_hashes,
        "loader_lifecycle_lines": lines,
        "loaders_all_active": bool(start_ok and stop_ok),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="New disposable directory")
    parser.add_argument("--mojang-cache", type=Path)
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--mode", choices=("baseline", "isolated"), default="baseline")
    parser.add_argument("--preload-natives", action="store_true",
                        help="Step 3: host loads netty-epoll/JNA/sqlite natives first (isolated only)")
    parser.add_argument("--host-log4j", action="store_true",
                        help="Step 3: host configures its own log4j via -Dlog4j.configurationFile first (isolated only)")
    parser.add_argument("--rust-ffm", action="store_true",
                        help="Step 3: enable perf.yml native.*=true and preload Rust FFM/JNI in host loader (isolated only)")
    parser.add_argument("--natives-dir", type=Path,
                        help="Directory containing libdrakes_hybrid_ffi.so, libodysseia_ffi.so and libslimefun_ffi.so")
    parser.add_argument("--rust-jni-conflict", action="store_true",
                        help="Step 3 control: host calls System.load on server/natives/libslimefun_ffi.so directly")
    parser.add_argument("--loader-mods", action="store_true",
                        help="Step 4: load and exercise built NeoForge, Forge and Fabric universal JARs in isolated mod loaders")
    parser.add_argument("--loader-jni-conflict", action="store_true",
                        help="Step 4 control: disable per-loader JNI staging in SlimefunRustNativeBridge")
    raise SystemExit(run(parser.parse_args()))
