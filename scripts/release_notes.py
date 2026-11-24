#!/usr/bin/env python3
"""Genera el cuerpo Markdown de un release para un canal."""

from __future__ import annotations

import argparse
import sys

from release_lib import ALL_CHANNELS, ReleaseError, release_notes, valid_version

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Notas de release por canal")
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", required=True, choices=list(ALL_CHANNELS))
    parser.add_argument("--base", default="", help="Tag/commit base; vacío = historial completo")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--output", default="", help="Archivo de salida; vacío = stdout")
    args = parser.parse_args()

    try:
        if not valid_version(args.version):
            raise ReleaseError(f"Versión inválida: {args.version}")
        texto = release_notes(
            version=args.version,
            channel=args.channel,
            base=args.base.strip() or None,
            head=args.head.strip() or "HEAD",
        )
        if args.output:
            with open(args.output, "w", encoding="utf-8") as fh:
                fh.write(texto)
        else:
            print(texto)
        return 0
    except ReleaseError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())