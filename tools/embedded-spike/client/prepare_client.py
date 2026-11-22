#!/usr/bin/env python3
"""Descarga un cliente Fabric 26.2 mínimo (sin sonidos) para el paso 2 del spike."""
import argparse, hashlib, json, os, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC = "https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json"


def get(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def fetch(url, dest, sha1=None):
    if os.path.exists(dest) and (sha1 is None or hashlib.sha1(open(dest, "rb").read()).hexdigest() == sha1):
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    data = get(url)
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        raise SystemExit(f"sha1 distinto: {url}")
    with open(dest + ".part", "wb") as f:
        f.write(data)
    os.replace(dest + ".part", dest)


def allowed(lib):
    ok = True
    for rule in lib.get("rules", []):
        os_name = rule.get("os", {}).get("name")
        if os_name is None or os_name == "linux":
            ok = rule["action"] == "allow"
        elif rule["action"] == "allow" and os_name != "linux":
            ok = ok and False
    return ok


def maven_path(name):
    g, a, v, *c = name.split(":")
    cls = f"-{c[0]}" if c else ""
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}{cls}.jar"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--mc", default="26.2")
    p.add_argument("--loader", default="0.20.1")
    p.add_argument("--root", required=True)
    a = p.parse_args()
    root = os.path.abspath(a.root)
    man = json.loads(get(MANIFEST))
    vurl = next(v["url"] for v in man["versions"] if v["id"] == a.mc)
    ver = json.loads(get(vurl))
    fab = json.loads(get(FABRIC.format(mc=a.mc, loader=a.loader)))
    jobs, cp = [], []
    client = f"{root}/versions/{a.mc}/{a.mc}.jar"
    jobs.append((ver["downloads"]["client"]["url"], client, ver["downloads"]["client"]["sha1"]))
    for lib in ver["libraries"]:
        art = lib.get("downloads", {}).get("artifact")
        if art and allowed(lib):
            dest = f"{root}/libraries/{art['path']}"
            jobs.append((art["url"], dest, art["sha1"]))
            cp.append(dest)
    for lib in fab["libraries"]:
        path = maven_path(lib["name"])
        dest = f"{root}/libraries/{path}"
        jobs.append((lib["url"].rstrip("/") + "/" + path, dest, lib.get("sha1")))
        cp.append(dest)
    idx = ver["assetIndex"]
    idx_path = f"{root}/assets/indexes/{idx['id']}.json"
    fetch(idx["url"], idx_path, idx["sha1"])
    for name, obj in json.load(open(idx_path))["objects"].items():
        if name.startswith(("minecraft/sounds/", "minecraft/music/")) or name.endswith(".ogg"):
            continue
        h = obj["hash"]
        jobs.append((f"https://resources.download.minecraft.net/{h[:2]}/{h}", f"{root}/assets/objects/{h[:2]}/{h}", h))
    with ThreadPoolExecutor(8) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))
    # Fabric antepone sus librerías; quita duplicados de ASM etc. dejando la versión de Fabric.
    seen, final = set(), []
    for path in [c for c in cp if "/net/fabricmc/" in c or "/org/ow2/asm/" in c] + cp:
        # El clasificador (natives-*) es parte de la clave: lwjgl-3.4.1.jar y su -natives-linux.jar comparten carpeta.
        parts = path.split("/")
        key = ("/".join(parts[:-2]), parts[-1].removeprefix(f"{parts[-3]}-{parts[-2]}"))
        if key not in seen:
            seen.add(key)
            final.append(path)
    final.append(client)
    out = {"mainClass": fab["mainClass"], "classpath": final, "assetIndex": idx["id"],
           "gameArgs": fab.get("arguments", {}).get("game", []),
           "jvmArgs": fab.get("arguments", {}).get("jvm", []), "javaMajor": ver.get("javaVersion", {}).get("majorVersion")}
    json.dump(out, open(f"{root}/launch.json", "w"), indent=2)
    print(f"ok: {len(jobs)} archivos, main={out['mainClass']}, java={out['javaMajor']}")


if __name__ == "__main__":
    sys.exit(main())
