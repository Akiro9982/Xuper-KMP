"""Utilidades compartidas por los scripts de release.

No usa dependencias externas: el workflow sólo necesita Python 3 y git.
"""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

ALL_CHANNELS = ("desktop", "mobile", "tv")

CHANNEL_LABELS = {
    "desktop": "Escritorio",
    "mobile": "Android",
    "tv": "Android TV",
}

CHANNEL_TAG_PREFIX = {
    "desktop": "desktop-v",
    "mobile": "mobile-v",
    "tv": "tv-v",
}

CHANNEL_EXTENSION = {
    "desktop": ".msi",
    "mobile": ".apk",
    "tv": ".apk",
}

# Un cambio en core afecta a las tres apps; cada carpeta de app sólo a la suya.
PATH_CHANNELS = {
    "app-mobile/": {"mobile"},
    "app-tv/": {"tv"},
    "desktop/": {"desktop"},
    "core/": set(ALL_CHANNELS),
    "gradle/": set(ALL_CHANNELS),
}

ROOT_ALL_FILES = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
}

IGNORED_PREFIXES = (
    ".github/",
    "docs/",
    "scripts/",
)

IGNORED_FILES = {
    ".gitignore",
    ".gitattributes",
    "README.md",
    "LICENSE",
    "LICENSE.md",
    "version.properties",
}

TAG_RE = re.compile(r"^(?:(desktop|mobile|tv)-)?v(\d+)\.(\d+)\.(\d+)$")
VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
BRACKET_RE = re.compile(r"^\s*\[([A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)\]\s*")
CONVENTIONAL_RE = re.compile(r"^\s*([A-Za-z]+)(?:\([^)]*\))?!?:\s*")

SKIP_KINDS = {"docs", "ci", "chore", "test", "build"}


class ReleaseError(RuntimeError):
    """Error de configuración del release, con mensaje para el usuario."""


@dataclass(frozen=True)
class TagInfo:
    tag: str
    version: str
    channel: str | None


@dataclass(frozen=True)
class Commit:
    sha: str
    subject: str
    paths: tuple[str, ...]
    channels: frozenset[str]
    kind: str


def git(*args: str, check: bool = True) -> str:
    cp = subprocess.run(
        ["git", *args],
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and cp.returncode != 0:
        detalle = cp.stderr.strip() or cp.stdout.strip() or "sin detalle"
        raise ReleaseError(f"git {' '.join(args)} falló: {detalle}")
    return cp.stdout


def version_from_properties(path: str | Path = "version.properties") -> str:
    archivo = Path(path)
    if not archivo.exists():
        return "0.0.0"
    for linea in archivo.read_text(encoding="utf-8").splitlines():
        if linea.strip().startswith("app.versionName="):
            return linea.split("=", 1)[1].strip()
    return "0.0.0"


def valid_version(version: str) -> bool:
    return bool(VERSION_RE.fullmatch(version.strip()))


def parse_tag(tag: str) -> TagInfo | None:
    m = TAG_RE.fullmatch(tag.strip())
    if not m:
        return None
    canal = m.group(1)
    version = f"{m.group(2)}.{m.group(3)}.{m.group(4)}"
    if not valid_version(version):
        return None
    return TagInfo(tag=tag.strip(), version=version, channel=canal)


def previous_tag(ref: str = "HEAD") -> str | None:
    """Etiqueta más reciente antes de ref (no la propia etiqueta)."""
    try:
        return git("describe", "--tags", "--abbrev=0", f"{ref}^").strip() or None
    except ReleaseError:
        return None


def changed_paths(base: str | None, head: str = "HEAD") -> list[str]:
    if not base:
        return [p for p in git("ls-tree", "-r", "--name-only", head).splitlines() if p.strip()]
    return [p for p in git("diff", "--name-only", base, head).splitlines() if p.strip()]


def channels_for_paths(paths: list[str]) -> set[str]:
    """Traduce rutas modificadas a canales afectados.

    Los archivos de documentación, CI, scripts y versión no provocan releases.
    Cualquier ruta de código desconocida se considera global por seguridad.
    """
    canales: set[str] = set()
    for ruta_original in paths:
        ruta = ruta_original.strip().replace("\\", "/")
        if not ruta or ruta in IGNORED_FILES:
            continue
        if any(ruta.startswith(prefijo) for prefijo in IGNORED_PREFIXES):
            continue

        encontrado = False
        for prefijo, afectados in PATH_CHANNELS.items():
            if ruta.startswith(prefijo):
                canales.update(afectados)
                encontrado = True
                break
        if encontrado:
            continue

        # Los archivos de configuración de la raíz afectan a todos los canales.
        if ruta in ROOT_ALL_FILES:
            canales.update(ALL_CHANNELS)
            continue

        # Un archivo nuevo de código en la raíz no debería dejar canales fuera.
        canales.update(ALL_CHANNELS)
    return canales


def commit_kind(subject: str) -> str:
    texto = subject.strip()
    m = BRACKET_RE.match(texto)
    prefijo = m.group(1).lower() if m else ""
    if not prefijo:
        m = CONVENTIONAL_RE.match(texto)
        prefijo = m.group(1).lower() if m else ""

    if prefijo in {"add", "feat", "feature", "nuevo", "nueva"}:
        return "add"
    if prefijo in {"fix", "bugfix", "hotfix", "arreglo", "correccion", "corrección"}:
        return "fix"
    if prefijo in {"update", "upd", "improve", "improvement", "perf", "refactor", "mejora"}:
        return "update"
    if prefijo in {"remove", "delete", "removed", "eliminar"}:
        return "remove"
    if prefijo in {"security", "seguridad"}:
        return "security"
    if prefijo in {"docs", "doc", "documentation"}:
        return "docs"
    if prefijo in {"ci", "build", "chore", "test", "tests", "testing"}:
        return "chore"
    return "other"


def clean_subject(subject: str) -> str:
    texto = BRACKET_RE.sub("", subject.strip(), count=1)
    texto = CONVENTIONAL_RE.sub("", texto, count=1).strip(" -–—:")
    if not texto:
        texto = subject.strip()
    return texto[:1].upper() + texto[1:] if texto else texto


def commit_paths(sha: str) -> tuple[str, ...]:
    salida = git("show", "--pretty=format:", "--name-only", sha)
    return tuple(p for p in salida.splitlines() if p.strip())


def commits_between(base: str | None, head: str = "HEAD") -> list[Commit]:
    rango = f"{base}..{head}" if base else head
    formato = "%H%x1f%s%x1e"
    salida = git("log", "--no-merges", f"--format={formato}", rango)
    commits: list[Commit] = []
    for bloque in salida.split("\x1e"):
        bloque = bloque.strip("\n\r ")
        if not bloque:
            continue
        partes = bloque.split("\x1f", 1)
        if len(partes) != 2:
            continue
        sha, subject = partes[0].strip(), partes[1].strip()
        if not sha:
            continue
        rutas = commit_paths(sha)
        canales = frozenset(channels_for_paths(list(rutas)))
        kind = commit_kind(subject)
        if kind in SKIP_KINDS:
            canales = frozenset()
        commits.append(
            Commit(
                sha=sha,
                subject=subject,
                paths=rutas,
                channels=canales,
                kind=kind,
            )
        )
    return commits


def _seccion(kind: str) -> str:
    return {
        "add": "Novedades",
        "fix": "Correcciones",
        "update": "Mejoras",
        "security": "Seguridad",
        "remove": "Eliminado",
        "other": "Otros cambios",
    }.get(kind, "Otros cambios")


def release_notes(
    version: str,
    channel: str,
    base: str | None,
    head: str = "HEAD",
    max_items: int = 40,
) -> str:
    if channel not in ALL_CHANNELS:
        raise ReleaseError(f"Canal desconocido: {channel}")

    commits = commits_between(base, head)
    vistos: set[str] = set()
    por_tipo: dict[str, list[str]] = {}
    for commit in commits:
        if channel not in commit.channels:
            continue
        texto = clean_subject(commit.subject)
        if not texto or texto.lower() in vistos:
            continue
        vistos.add(texto.lower())
        por_tipo.setdefault(commit.kind, []).append(texto)

    orden = ("add", "fix", "update", "security", "remove", "other")
    lineas: list[str] = [
        f"# Xuper {version} — {CHANNEL_LABELS[channel]}",
        "",
        f"Actualización publicada el {datetime.now(timezone.utc).strftime('%Y-%m-%d')} (UTC).",
        "",
        "## Cambios",
        "",
    ]

    total = 0
    for kind in orden:
        elementos = por_tipo.get(kind, [])
        if not elementos:
            continue
        lineas.append(f"### {_seccion(kind)}")
        for elemento in elementos[:max_items]:
            lineas.append(f"- {elemento}")
            total += 1
        if len(elementos) > max_items:
            lineas.append(f"- … y {len(elementos) - max_items} cambios más.")
        lineas.append("")

    if total == 0:
        lineas.extend([
            "- Mantenimiento y mejoras internas de esta plataforma.",
            "",
        ])

    lineas.extend([
        "## Cómo actualizar",
        "",
    ])
    if channel == "desktop":
        lineas.extend([
            "- Descarga el archivo .msi y ejecútalo. Se instala por usuario en %LOCALAPPDATA% sin pedir permisos de administrador.",
            "- También puedes usar Ajustes → Actualizaciones dentro de la app instalada: descarga, verifica el SHA-256 e instala al cerrar Xuper.",
        ])
    else:
        lineas.extend([
            "- Descarga el .apk y ábrelo. Android pedirá confirmación para instalar apps de esta fuente.",
            "- También puedes usar Ajustes → Actualizaciones dentro de la app: descarga, verifica la firma y abre el instalador.",
        ])
    lineas.extend([
        "",
        "## Detalles del release",
        "",
        f"- Versión: {version}",
        f"- Canal: {channel}",
        f"- Artefacto: xuper-{channel}-{version}{CHANNEL_EXTENSION[channel]}",
        f"- SHA-256: archivo xuper-{channel}-{version}{CHANNEL_EXTENSION[channel]}.sha256 adjunto.",
        "",
        "Este release pertenece únicamente a este canal. Las demás plataformas no reciben esta actualización salvo que sus archivos también hayan cambiado.",
        "",
    ])
    return "\n".join(lineas)


def release_tag(channel: str, version: str) -> str:
    return f"{CHANNEL_TAG_PREFIX[channel]}{version}"