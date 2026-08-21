#!/usr/bin/env python3
"""Create a clean public copy of an Android project.

This tool is deliberately generic: pass private organisation/domain terms on the
command line instead of hard-coding them into the repository.
"""

from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path

IGNORE_NAMES = {
    ".git", ".gradle", ".idea", "build", "captures", ".cxx",
    ".externalNativeBuild", "node_modules", "dist"
}
IGNORE_FILES = {
    "local.properties", "keystore.properties", "signing.properties",
    ".env", ".DS_Store", "Thumbs.db"
}
IGNORE_SUFFIXES = {".jks", ".keystore", ".apk", ".aab", ".log"}

TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".md",
    ".txt", ".json", ".yml", ".yaml", ".toml", ".pro", ".cfg", ".ini",
    ".html", ".css", ".js", ".ts", ".tsx", ".jsx"
}

EMAIL_RE = re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I)
URL_RE = re.compile(r"https?://[^\s)>'\"]+", re.I)
PRIVATE_IP_RE = re.compile(
    r"\b(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b"
)
WINDOWS_PATH_RE = re.compile(r"\b[A-Za-z]:\\[^\r\n\"']+")
USER_PATH_RE = re.compile(r"(?:/Users/|/home/)[^/\s]+/[^\s\"']+")
SECRET_ASSIGNMENT_RE = re.compile(
    r"(?i)\b(api[_-]?key|access[_-]?token|auth[_-]?token|password|passwd|secret)\b\s*[:=]\s*[\"'][^\"']{8,}[\"']"
)


def ignored(path: Path) -> bool:
    if any(part in IGNORE_NAMES for part in path.parts):
        return True
    if path.name in IGNORE_FILES:
        return True
    if path.suffix.lower() in IGNORE_SUFFIXES:
        return True
    if path.name.startswith(".env."):
        return True
    return False


def copy_project(src: Path, dst: Path) -> None:
    for item in src.rglob("*"):
        rel = item.relative_to(src)
        if ignored(rel):
            continue
        target = dst / rel
        if item.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        elif item.is_file():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, target)


def looks_text(path: Path) -> bool:
    if path.suffix.lower() in TEXT_SUFFIXES:
        return True
    return path.name in {"gradlew", ".gitignore", ".editorconfig", "LICENSE", "NOTICE"}


def rewrite_text_files(dst: Path, remove_terms: list[str], replacement: str,
                       old_package: str | None, new_package: str) -> int:
    changed = 0
    for path in dst.rglob("*"):
        if not path.is_file() or not looks_text(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        original = text
        for term in remove_terms:
            text = re.sub(re.escape(term), replacement, text, flags=re.I)
        if old_package:
            text = text.replace(old_package, new_package)
        if text != original:
            path.write_text(text, encoding="utf-8")
            changed += 1
    return changed


def relocate_packages(dst: Path, old_package: str | None, new_package: str) -> list[str]:
    if not old_package or old_package == new_package:
        return []
    old_rel = Path(*old_package.split("."))
    new_rel = Path(*new_package.split("."))
    moved = []
    roots = [p for p in dst.rglob("*") if p.is_dir() and p.name in {"java", "kotlin"}]
    for root in roots:
        old_dir = root / old_rel
        if not old_dir.exists() or not old_dir.is_dir():
            continue
        new_dir = root / new_rel
        new_dir.parent.mkdir(parents=True, exist_ok=True)
        if new_dir.exists():
            for child in old_dir.rglob("*"):
                if child.is_file():
                    rel = child.relative_to(old_dir)
                    target = new_dir / rel
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.move(str(child), str(target))
            shutil.rmtree(old_dir)
        else:
            shutil.move(str(old_dir), str(new_dir))
        moved.append(f"{old_dir.relative_to(dst)} -> {new_dir.relative_to(dst)}")
    return moved


def scan(dst: Path, remove_terms: list[str], old_package: str | None) -> dict[str, list[str]]:
    findings: dict[str, list[str]] = {
        "forbidden_terms": [], "emails": [], "urls": [], "private_ips": [],
        "absolute_paths": [], "potential_secrets": []
    }
    forbidden = [t for t in remove_terms if t]
    if old_package:
        forbidden.append(old_package)

    for path in dst.rglob("*"):
        if not path.is_file() or not looks_text(path):
            continue
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except (UnicodeDecodeError, OSError):
            continue
        rel = path.relative_to(dst)
        for lineno, line in enumerate(lines, 1):
            where = f"{rel}:{lineno}"
            for term in forbidden:
                if term.lower() in line.lower():
                    findings["forbidden_terms"].append(f"{where}: {term}")
            for value in EMAIL_RE.findall(line):
                findings["emails"].append(f"{where}: {value}")
            for value in URL_RE.findall(line):
                findings["urls"].append(f"{where}: {value}")
            for value in PRIVATE_IP_RE.findall(line):
                findings["private_ips"].append(f"{where}: {value}")
            for value in WINDOWS_PATH_RE.findall(line):
                findings["absolute_paths"].append(f"{where}: {value}")
            for value in USER_PATH_RE.findall(line):
                findings["absolute_paths"].append(f"{where}: {value}")
            if SECRET_ASSIGNMENT_RE.search(line):
                findings["potential_secrets"].append(f"{where}: review manually")
    return findings


def write_report(dst: Path, changed: int, moved: list[str], findings: dict[str, list[str]]) -> None:
    out = [
        "PRAYR PUBLICISATION REPORT",
        "===========================",
        f"Text files rewritten: {changed}",
        f"Package directories moved: {len(moved)}",
        "",
    ]
    if moved:
        out.append("PACKAGE MOVES")
        out.extend(f"- {x}" for x in moved)
        out.append("")
    for section, values in findings.items():
        out.append(section.upper().replace("_", " "))
        if values:
            out.extend(f"- {x}" for x in values[:200])
            if len(values) > 200:
                out.append(f"- ... plus {len(values) - 200} more")
        else:
            out.append("- none found")
        out.append("")
    out.extend([
        "NEXT STEPS",
        "- Review every finding, especially URLs, emails, paths, and secret-like assignments.",
        "- Check AndroidManifest.xml and Gradle signing configuration manually.",
        "- Run Gradle lint, tests, and assembleDebug.",
        "- Search the final repository one more time before the first push.",
    ])
    (dst / "PUBLICISATION_REPORT.txt").write_text("\n".join(out), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a clean public copy of prayr")
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--remove-term", action="append", default=[],
                        help="Private organisation/domain/internal term to replace; repeat as needed")
    parser.add_argument("--replacement", default="blondothenerd")
    parser.add_argument("--old-package", help="Current Android package/namespace")
    parser.add_argument("--new-package", default="dev.blondothenerd.prayr")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    src = args.source.expanduser().resolve()
    dst = args.destination.expanduser().resolve()
    if not src.is_dir():
        raise SystemExit(f"Source directory does not exist: {src}")
    if dst.exists():
        if not args.force:
            raise SystemExit(f"Destination already exists: {dst} (use --force to replace it)")
        shutil.rmtree(dst)
    dst.mkdir(parents=True)

    copy_project(src, dst)
    changed = rewrite_text_files(dst, args.remove_term, args.replacement,
                                 args.old_package, args.new_package)
    moved = relocate_packages(dst, args.old_package, args.new_package)
    findings = scan(dst, args.remove_term, args.old_package)
    write_report(dst, changed, moved, findings)

    print(f"Public copy created: {dst}")
    print(f"Review: {dst / 'PUBLICISATION_REPORT.txt'}")


if __name__ == "__main__":
    main()
