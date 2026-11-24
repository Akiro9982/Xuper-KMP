#!/usr/bin/env python3
"""Sube app.versionName en version.properties sin tocar el resto del archivo."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from release_lib import ReleaseError, valid_version, version_from_properties


def main() -> int:
    parser = argparse.ArgumentParser(description="Cambiar la versión de la app")
    parser.add_argument("version", nargs="?", help="Nueva versión X.Y.Z")
    grupo = parser.add_mutually_exclusive_group()
    grupo.add_argument("--major", action="store_true", help="Subir major")
    grupo.add_argument("--minor", action="store_true", help="Subir minor")
    grupo.add_argument("--patch", action="store_true", help="Subir patch (por defecto)")
    parser.add_argument("--file", default="version.properties")
    args = parser.parse_args()

    try:
        actual = version_from_properties(args.file)
        if not valid_version(actual):
            raise ReleaseError(f"La versión actual no es válida: {actual}")
        major, minor, patch = (int(x) for x in actual.split("."))

        if args.version:
            nueva = args.version.strip()
            if not valid_version(nueva):
                raise ReleaseError("La versión nueva debe tener el formato X.Y.Z")
        else:
            if args.major:
                major, minor, patch = major + 1, 0, 0
            elif args.minor:
                minor, patch = minor + 1, 0
            else:
                patch += 1
            nueva = f"{major}.{minor}.{patch}"

        archivo = Path(args.file)
        texto = archivo.read_text(encoding="utf-8") if archivo.exists() else ""
        patron = re.compile(r"(?m)^app\.versionName=.*$")
        if patron.search(texto):
            texto = patron.sub(f"app.versionName={nueva}", texto, count=1)
        else:
            texto = texto.rstrip() + f"\napp.versionName={nueva}\n"
        archivo.write_text(texto, encoding="utf-8")
        print(nueva)
        return 0
    except (ReleaseError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
