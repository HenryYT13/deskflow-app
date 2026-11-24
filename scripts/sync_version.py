#!/usr/bin/env python3
"""Synchronize the canonical VERSION file across all DeskFlow clients & firmware."""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def parse_version(raw: str) -> tuple[str, int, int, int, int]:
    ver = raw.strip().lstrip("v")
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", ver)
    if not m:
        raise ValueError(f"Expected X.Y.Z semver, got: {raw!r}")
    major, minor, patch = map(int, m.groups())
    code = major * 10000 + minor * 100 + patch
    return ver, major, minor, patch, code


def replace_regex(path: pathlib.Path, pattern: str, repl: str, flags: int = 0) -> bool:
    if not path.exists():
        return False
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, repl, text, flags=flags)
    if count and updated != text:
        path.write_text(updated, encoding="utf-8")
        return True
    return False


def main() -> int:
    ver_arg = sys.argv[1] if len(sys.argv) > 1 else (ROOT / "VERSION").read_text(encoding="utf-8")
    ver, _maj, _min, _pat, code = parse_version(ver_arg)
    (ROOT / "VERSION").write_text(f"{ver}\n", encoding="utf-8")

    targets = [
        (
            ROOT / "linux/src/deskflow/__init__.py",
            r'__version__\s*=\s*"[^"]+"',
            f'__version__ = "{ver}"',
            0,
        ),
        (
            ROOT / "linux/src/deskflow/api_client.py",
            r'DeskFlow-Linux-GTK4/[^"]+',
            f"DeskFlow-Linux-GTK4/{ver}",
            0,
        ),
        (
            ROOT / "linux/src/deskflow/api_client.py",
            r'"client_version":\s*"[^"]+"',
            f'"client_version": "{ver}"',
            0,
        ),
        (
            ROOT / "linux/packaging/build_packages.sh",
            r'VERSION="\$\{1:-\$\{VERSION:-[^}]+\}\}"',
            f'VERSION="${{1:-${{VERSION:-{ver}}}}}"',
            0,
        ),
        (
            ROOT / "win/DeskFlow/DeskFlow.csproj",
            r"<Version>[^<]+</Version>",
            f"<Version>{ver}</Version>",
            0,
        ),
        (
            ROOT / "win/DeskFlow/DeskFlow.csproj",
            r"<(AssemblyVersion|FileVersion)>[^<]+</\1>",
            rf"<\1>{ver}.0</\1>",
            0,
        ),
        (
            ROOT / "win/DeskFlow/Package.appxmanifest",
            r'Version="[0-9]+\.[0-9]+\.[0-9]+\.0"',
            f'Version="{ver}.0"',
            0,
        ),
        (
            ROOT / "win/DeskFlow/app.manifest",
            r'assemblyIdentity version="[0-9]+\.[0-9]+\.[0-9]+\.0"',
            f'assemblyIdentity version="{ver}.0"',
            0,
        ),
        (
            ROOT / "win/DeskFlow/Program.cs",
            r"DeskFlow Station Windows \(C# \+ WinUI 3\) v[0-9.]+",
            f"DeskFlow Station Windows (C# + WinUI 3) v{ver}",
            0,
        ),
        (
            ROOT / "win/DeskFlow/Services/DeskFlowApiClient.cs",
            r"DeskFlow-Windows-WinUI3/[0-9.]+",
            f"DeskFlow-Windows-WinUI3/{ver}",
            0,
        ),
        (
            ROOT / "win/DeskFlow/Services/DeskFlowApiClient.cs",
            r'client_version\s*=\s*"[^"]+"',
            f'client_version = "{ver}"',
            0,
        ),
        (
            ROOT / "macos/DeskFlow/DeskFlowApp.swift",
            r"Deskflow macOS \(Swift \+ SwiftUI\) v[0-9.]+",
            f"Deskflow macOS (Swift + SwiftUI) v{ver}",
            0,
        ),
        (
            ROOT / "macos/DeskFlow/Services/DeskFlowApiClient.swift",
            r"DeskFlow-macOS-SwiftUI/[0-9.]+",
            f"DeskFlow-macOS-SwiftUI/{ver}",
            0,
        ),
        (
            ROOT / "macos/DeskFlow/Services/DeskFlowApiClient.swift",
            r'"client_version":\s*"[^"]+"',
            f'"client_version": "{ver}"',
            0,
        ),
        (
            ROOT / "firmware-esp32/khkt/khkt.ino",
            r'#define DESKFLOW_FW_VERSION\s+"[^"]+"',
            f'#define DESKFLOW_FW_VERSION "{ver}"',
            0,
        ),
    ]

    changed = []
    for path, pat, rep, flags in targets:
        if replace_regex(path, pat, rep, flags):
            changed.append(str(path.relative_to(ROOT)))

    print(f"Synced DeskFlow version -> {ver} (versionCode={code}); updated {len(changed)} files.")
    for item in changed:
        print(f"  - {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
