#!/usr/bin/env python3
"""Downloads the libraries tools/typecheck.py needs that the Gradle cache doesn't have.

Why this exists
---------------
Gradle can't run where this project is written (see typecheck.py), so it never populates its
cache with new dependencies. This resolves them the way Gradle would, well enough for type
checking: Gradle module metadata first (which handles Kotlin multiplatform "available-at"
redirects such as ui -> ui-android), POM files as a fallback, highest version wins.

Everything lands in build/typecheck/maven, laid out like the Gradle cache
(group/artifact/version/<dir>/file) so typecheck.py reads both the same way.

Usage:  python tools/fetch_deps.py
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build", "typecheck", "maven")
PLUGINS = os.path.join(ROOT, "build", "typecheck", "plugins")
REPOS = ["https://dl.google.com/android/maven2", "https://repo1.maven.org/maven2"]

# What app/build.gradle.kts adds on top of what the Gradle cache already holds.
# Compose 1.12 and Lifecycle 2.11 declare minCompileSdk 37; these are the newest that build
# against compileSdk 36.
ROOTS = [
    ("androidx.compose.runtime", "runtime", "1.11.4"),
    ("androidx.compose.ui", "ui", "1.11.4"),
    ("androidx.compose.foundation", "foundation", "1.11.4"),
    ("androidx.compose.animation", "animation", "1.11.4"),
    ("androidx.activity", "activity-compose", "1.12.4"),
    ("androidx.lifecycle", "lifecycle-runtime-compose", "2.10.0"),
]

# The compiler plugin must match the Kotlin compiler version exactly.
COMPILER_PLUGINS = [
    ("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", "2.4.0"),
]

# The in-process compiler needs kotlin-reflect at its own version; the Gradle cache on this
# machine has the 2.4.0 compiler and stdlib but not reflect.
COMPILER_JARS = [
    ("org.jetbrains.kotlin", "kotlin-reflect", "2.4.0"),
    ("org.jetbrains.kotlin", "kotlin-script-runtime", "2.4.0"),
]

CACHE = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1")


def version_key(version: str) -> tuple:
    numbers, _, qualifier = version.partition("-")
    return (tuple(int(p) if p.isdigit() else 0 for p in numbers.split(".")), qualifier == "", qualifier)


def fetch(path: str) -> bytes | None:
    for repo in REPOS:
        try:
            with urllib.request.urlopen(f"{repo}/{path}", timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code != 404:
                print(f"  {error.code} for {repo}/{path}", file=sys.stderr)
        except urllib.error.URLError as error:
            print(f"  network error for {path}: {error}", file=sys.stderr)
    return None


def base_path(group: str, artifact: str, version: str) -> str:
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}"


def in_gradle_cache(group: str, artifact: str, version: str) -> bool:
    directory = os.path.join(CACHE, group, artifact)
    if not os.path.isdir(directory):
        return False
    return any(version_key(v) >= version_key(version) for v in os.listdir(directory))


def choose_variant(variants: list[dict]) -> dict | None:
    best, best_score = None, -1
    for variant in variants:
        attributes = variant.get("attributes", {})
        category = attributes.get("org.gradle.category", "library")
        usage = attributes.get("org.gradle.usage", "")
        if category not in ("library",) or "docs" in attributes.get("org.gradle.docstype", "docs-none"):
            if category != "library":
                continue
        platform = attributes.get("org.jetbrains.kotlin.platform.type", "")
        score = 0
        if platform == "androidJvm":
            score += 20
        elif platform == "jvm":
            score += 10
        elif platform in ("common", "native", "js", "wasm"):
            continue
        if usage == "java-api":
            score += 4
        elif usage == "java-runtime":
            score += 2
        elif usage.startswith("kotlin-"):
            continue
        if "sources" in variant.get("name", "").lower():
            continue
        if score > best_score:
            best, best_score = variant, score
    return best


def resolve() -> dict[tuple[str, str], tuple[str, list[str]]]:
    """Returns {(group, artifact): (version, [file names to download])}."""
    best: dict[tuple[str, str], str] = {}
    files: dict[tuple[str, str, str], list[str]] = {}
    queue = list(ROOTS)
    seen: set[tuple[str, str, str]] = set()

    while queue:
        group, artifact, version = queue.pop(0)
        key = (group, artifact)
        if (group, artifact, version) in seen:
            continue
        if key in best and version_key(best[key]) >= version_key(version):
            continue
        seen.add((group, artifact, version))
        best[key] = version

        base = base_path(group, artifact, version)
        module = fetch(base + ".module")
        if module:
            data = json.loads(module)
            variant = choose_variant(data.get("variants", []))
            if variant is None:
                files[(group, artifact, version)] = []
                continue
            redirect = variant.get("available-at")
            if redirect:
                queue.append((redirect["group"], redirect["module"], redirect["version"]))
                files[(group, artifact, version)] = []
                continue
            # "name" is only a label (often "ui.aar"); "url" is the real file next to the metadata.
            files[(group, artifact, version)] = [
                f["url"] for f in variant.get("files", []) if f["url"].endswith((".aar", ".jar"))
            ]
            for dependency in variant.get("dependencies", []):
                requested = dependency.get("version", {})
                wanted = requested.get("strictly") or requested.get("requires") or requested.get("prefers")
                if wanted:
                    queue.append((dependency["group"], dependency["module"], wanted))
            continue

        pom = fetch(base + ".pom")
        if not pom:
            print(f"  not found: {group}:{artifact}:{version}", file=sys.stderr)
            continue
        tree = ET.fromstring(pom)
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        packaging = (tree.findtext("m:packaging", default="jar", namespaces=ns) or "jar").strip()
        extension = "aar" if packaging == "aar" else "jar"
        files[(group, artifact, version)] = [] if packaging == "pom" else [f"{artifact}-{version}.{extension}"]
        for dependency in tree.findall("m:dependencies/m:dependency", ns):
            scope = dependency.findtext("m:scope", default="compile", namespaces=ns)
            optional = dependency.findtext("m:optional", default="false", namespaces=ns)
            wanted = dependency.findtext("m:version", default="", namespaces=ns)
            if scope in ("test", "provided", "system") or optional == "true" or not wanted or "$" in wanted:
                continue
            wanted = wanted.strip("[]()").split(",")[0]
            queue.append((dependency.findtext("m:groupId", namespaces=ns),
                          dependency.findtext("m:artifactId", namespaces=ns), wanted))

    return {key: (version, files.get((key[0], key[1], version), [])) for key, version in best.items()}


def download(group: str, artifact: str, version: str, name: str, target_root: str) -> str | None:
    directory = os.path.join(target_root, group, artifact, version, "dl")
    target = os.path.join(directory, name)
    if os.path.exists(target):
        return target
    data = fetch(f"{group.replace('.', '/')}/{artifact}/{version}/{name}")
    if data is None:
        print(f"  missing file {name}", file=sys.stderr)
        return None
    os.makedirs(directory, exist_ok=True)
    with open(target, "wb") as handle:
        handle.write(data)
    return target


def main() -> int:
    resolved = resolve()
    downloaded, skipped, too_new = 0, 0, []
    for (group, artifact), (version, names) in sorted(resolved.items()):
        if in_gradle_cache(group, artifact, version):
            skipped += 1
            continue
        for name in names:
            path = download(group, artifact, version, name, OUT)
            if not path:
                continue
            downloaded += 1
            if path.endswith(".aar"):
                with zipfile.ZipFile(path) as archive:
                    try:
                        meta = archive.read("META-INF/com/android/build/gradle/aar-metadata.properties").decode()
                    except KeyError:
                        meta = ""
                for line in meta.splitlines():
                    if line.startswith("minCompileSdk=") and int(line.split("=")[1]) > 36:
                        too_new.append(f"{group}:{artifact}:{version} needs compileSdk {line.split('=')[1]}")

    for group, artifact, version in COMPILER_PLUGINS:
        download(group, artifact, version, f"{artifact}-{version}.jar", PLUGINS)
    for group, artifact, version in COMPILER_JARS:
        download(group, artifact, version, f"{artifact}-{version}.jar", PLUGINS)

    print(f"fetch_deps: {len(resolved)} modules resolved, {downloaded} files downloaded, "
          f"{skipped} already in the Gradle cache")
    for (group, artifact), (version, _) in sorted(resolved.items()):
        if group.startswith(("org.jetbrains.kotlin", "androidx.lifecycle", "androidx.core", "androidx.activity")):
            print(f"  {group}:{artifact}:{version}")
    for line in too_new:
        print(f"  WARNING {line}")
    return 1 if too_new else 0


if __name__ == "__main__":
    sys.exit(main())
