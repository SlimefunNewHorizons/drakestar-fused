#!/usr/bin/env python3
"""Comprueba que el .jar de Fused sea universal (Minecraft 1.20 a 26.2): sin mixins, sin referencias a nombres internos
de Minecraft (salvo MinecraftServer, cuyo nombre no cambia) y con bytecode de Java 17 como maximo."""
import re
import sys
import zipfile

ALLOWED = {b"net/minecraft/server/MinecraftServer"}


def main(path: str) -> int:
    z = zipfile.ZipFile(path)
    names = z.namelist()
    errors = []
    if "fabric.mod.json" not in names:
        errors.append("falta fabric.mod.json")
    classes = [n for n in names if n.endswith(".class")]
    if not classes:
        errors.append("no hay clases")
    for n in names:
        if "mixin" in n.lower():
            errors.append(f"no debe haber mixins: {n}")
    for n in classes:
        data = z.read(n)
        major = int.from_bytes(data[6:8], "big")
        if major > 61:
            errors.append(f"{n}: bytecode Java {major - 44} (maximo 17)")
        for ref in set(re.findall(rb"net/minecraft/[A-Za-z0-9_/$]+", data)):
            if ref not in ALLOWED:
                errors.append(f"{n}: referencia a Minecraft {ref.decode()}")
    for e in errors:
        print("ERROR:", e)
    if not errors:
        print(f"OK: {path} ({len(classes)} clases, universal)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
