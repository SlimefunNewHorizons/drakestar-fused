#!/usr/bin/env python3
"""Comprueba que el .jar de Fused (Fabric, NeoForge o Forge) sea universal (Minecraft 1.20 a 26.2):
sin mixins, sin referencias a nombres internos de Minecraft (salvo MinecraftServer, cuyo nombre no cambia)
y con bytecode de Java 17 como maximo."""
import re
import sys
import zipfile

EMBEDDED_HOST = {"Embed", "SpikeHooks", "SpikeLoader"}
ALLOWED = {b"net/minecraft/server/MinecraftServer"}
DESCRIPTORS = (
    "fabric.mod.json",
    "META-INF/neoforge.mods.toml",
    "META-INF/mods.toml",
)


def main(paths: list[str]) -> int:
    if not paths:
        print("ERROR: se requiere al menos una ruta de .jar")
        return 1
    failed = 0
    for path in paths:
        z = zipfile.ZipFile(path)
        names = z.namelist()
        errors = []
        present = [d for d in DESCRIPTORS if d in names]
        if not present:
            errors.append(f"falta descriptor de loader ({', '.join(DESCRIPTORS)})")
        for d in present:
            text = z.read(d).decode("utf-8", errors="replace")
            if "${version}" in text:
                errors.append(f"{d}: contiene ${{version}} sin expandir")
        classes = [n for n in names if n.endswith(".class")]
        if not classes:
            errors.append("no hay clases")
        for n in names:
            if "mixin" in n.lower():
                errors.append(f"no debe haber mixins: {n}")
        for n in classes:
            data = z.read(n)
            major = int.from_bytes(data[6:8], "big")
            # host embebido (tools/embedded-spike): Java 25 a proposito, solo se carga en 26.2 por reflexion
            embedded_host = "/" not in n and n.split("$")[0].removesuffix(".class") in EMBEDDED_HOST
            if major > (69 if embedded_host else 61):
                errors.append(f"{n}: bytecode Java {major - 44} (maximo 17)")
            for ref in set(re.findall(rb"net/minecraft/[A-Za-z0-9_/$]+", data)):
                if ref not in ALLOWED:
                    errors.append(f"{n}: referencia a Minecraft {ref.decode()}")
        for e in errors:
            print("ERROR:", e)
        if errors:
            failed = 1
        else:
            print(f"OK: {path} ({len(classes)} clases, descriptor={present[0]}, universal)")
    return failed


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

