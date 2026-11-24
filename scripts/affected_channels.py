#!/usr/bin/env python3
"""Calcula versión y canales afectados por un tag o un release manual."""

from __future__ import annotations

import argparse
import json
import os
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from release_lib import (
    ALL_CHANNELS,
    ReleaseError,
    changed_paths,
    channels_for_paths,
    parse_tag,
    previous_tag,
    valid_version,
    version_from_properties,
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Canales afectados por una release")
    parser.add_argument("--tag", default="", help="Tag empujado (vX.Y.Z o <canal>-vX.Y.Z)")
    parser.add_argument(
        "--channel",
        default="auto",
        choices=["auto", "all", *ALL_CHANNELS],
        help="Canal manual; auto calcula por rutas",
    )
    parser.add_argument("--version", default="", help="Versión manual X.Y.Z")
    parser.add_argument("--base", default="", help="Tag/commit base; por defecto el tag anterior")
    parser.add_argument("--head", default="HEAD", help="Commit de destino")
    parser.add_argument("--json-out", default="", help="Archivo JSON de salida")
    args = parser.parse_args()

    tag = args.tag.strip()
    if not tag and os.environ.get("GITHUB_REF_TYPE") == "tag":
        tag = os.environ.get("GITHUB_REF_NAME", "").strip()

    try:
        if tag:
            info = parse_tag(tag)
            if info is None:
                raise ReleaseError(
                    f"El tag {tag} no tiene el formato vX.Y.Z, desktop-vX.Y.Z, mobile-vX.Y.Z o tv-vX.Y.Z."
                )
            version = info.version
            head = tag
            base = args.base.strip() or previous_tag(tag)
            if info.channel:
                channels = [info.channel]
            else:
                canales = channels_for_paths(changed_paths(base, head))
                if not canales:
                    raise ReleaseError(
                        f"No hay cambios de código entre {base or 'el inicio'} y {tag}. "
                        "No se publica nada para evitar releases vacíos."
                    )
                channels = [c for c in ALL_CHANNELS if c in canales]
            source = "tag"
        else:
            version = args.version.strip() or version_from_properties()
            if not valid_version(version):
                raise ReleaseError(f"Versión inválida: {version}. Usa el formato X.Y.Z.")
            head = args.head.strip() or "HEAD"
            base = args.base.strip() or previous_tag(head)
            if args.channel in ALL_CHANNELS:
                channels = [args.channel]
            elif args.channel == "all":
                channels = list(ALL_CHANNELS)
            else:
                canales = channels_for_paths(changed_paths(base, head))
                # Un release manual sin diferencias sirve para republicar; se hace completo.
                channels = [c for c in ALL_CHANNELS if c in canales] or list(ALL_CHANNELS)
            source = "manual"

        resultado = {
            "version": version,
            "channels": channels,
            "base": base or "",
            "head": head,
            "tag": tag,
            "source": source,
        }
        texto = json.dumps(resultado, ensure_ascii=False)
        if args.json_out:
            with open(args.json_out, "w", encoding="utf-8") as fh:
                fh.write(texto + "\n")
        print(texto)
        return 0
    except ReleaseError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())