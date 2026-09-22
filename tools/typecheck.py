#!/usr/bin/env python3
"""Type-check the app's Kotlin against the real Android, AndroidX and Compose classes.

Why this exists
---------------
Gradle cannot run in the environment this project is written in: the JVM's `Selector.open()`
fails there, which takes the Gradle daemon with it. So the code is written in one place and
built in another. This catches what would otherwise surface only in Android Studio: wrong
types, bad overrides, missing library methods, nullability slips, misused Compose APIs.

It does not build an APK. It runs the Kotlin compiler directly, in-process, with:

  * android.jar from the installed platform,
  * every dependency's classes, from the Gradle cache plus build/typecheck/maven (filled by
    tools/fetch_deps.py for libraries this machine has never synced),
  * the Compose compiler plugin, at the same Kotlin version the real build uses,
  * generated stand-ins for `R`, `BuildConfig` and the view-binding classes, derived from the
    same resources AGP reads, so a reference to a missing string or view id fails here too.

Usage:  python tools/fetch_deps.py   (once, or after changing dependencies)
        python tools/typecheck.py
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(APP, "res")
SRC = os.path.join(APP, "kotlin")
PACKAGE = "rocks.ndot.widgets"

HOME = os.path.expanduser("~")
CACHE = os.path.join(HOME, ".gradle", "caches", "modules-2", "files-2.1")
BUILD = os.path.join(ROOT, "build", "typecheck")
MAVEN = os.path.join(BUILD, "maven")
PLUGINS = os.path.join(BUILD, "plugins")

# The classic kotlin-android plugin in build.gradle.kts is 2.4.0, so that is the compiler the
# real build runs. The Compose compiler plugin must match it exactly.
KOTLIN = "2.4.0"
COMPOSE_PLUGIN = f"kotlin-compose-compiler-plugin-embeddable-{KOTLIN}.jar"

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# Kept in step with app/build.gradle.kts by check_dependencies_match().
DEPENDENCIES = [
    ("androidx.core", "core-ktx", "1.18.0"),
    ("androidx.appcompat", "appcompat", "1.7.0"),
    ("com.google.android.material", "material", "1.13.0"),
    ("androidx.constraintlayout", "constraintlayout", "2.2.1"),
    ("androidx.work", "work-runtime-ktx", "2.10.0"),
    ("com.google.android.gms", "play-services-location", "21.3.0"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.9.0"),
    ("androidx.activity", "activity-compose", "1.12.4"),
    ("androidx.compose.ui", "ui", "1.11.4"),
    ("androidx.compose.foundation", "foundation", "1.11.4"),
    ("androidx.compose.animation", "animation", "1.11.4"),
    ("androidx.lifecycle", "lifecycle-runtime-compose", "2.10.0"),
]


def fail(message: str) -> None:
    print(f"typecheck: {message}", file=sys.stderr)
    sys.exit(2)


# ---- locating the tools ----

def find_java() -> str:
    candidates = [
        os.environ.get("JAVA_HOME", "") and
        os.path.join(os.environ["JAVA_HOME"], "bin", "java.exe"),
        r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe",
        shutil.which("java"),
    ]
    for candidate in candidates:
        if candidate and os.path.exists(candidate):
            return candidate
    fail("no JDK found; set JAVA_HOME")


def find_android_jar() -> str:
    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.path.join(HOME, "AppData", "Local", "Android", "Sdk"),
        os.path.join(HOME, "Library", "Android", "sdk"),
        os.path.join(HOME, "Android", "Sdk"),
    ]
    for root in roots:
        platforms = root and os.path.join(root, "platforms")
        if not platforms or not os.path.isdir(platforms):
            continue
        # compileSdk is 36; prefer exactly that platform so newer APIs don't sneak in.
        for name in ["android-36"] + sorted(os.listdir(platforms), reverse=True):
            jar = os.path.join(platforms, name, "android.jar")
            if os.path.exists(jar):
                return jar
    fail("no android.jar found; set ANDROID_HOME")


# The Gradle cache holds whatever any project on this machine ever fetched, often newer than this
# build resolves. Those versions must not win: core 1.19 / Lifecycle 2.11 need a newer compileSdk
# than the app uses, and a Kotlin library newer than the compiler can't be read by it.
PINS = {
    ("org.jetbrains.kotlin", None): KOTLIN,
    ("androidx.lifecycle", None): "2.10.0",
    ("androidx.core", "core"): "1.18.0",
    ("androidx.core", "core-ktx"): "1.18.0",
    ("androidx.compose.ui", None): "1.11.4",
    ("androidx.compose.foundation", None): "1.11.4",
    ("androidx.compose.animation", None): "1.11.4",
    ("androidx.compose.runtime", None): "1.11.4",
}


def above_pin(group: str, artifact: str, version: str) -> bool:
    limit = PINS.get((group, artifact)) or PINS.get((group, None))
    return limit is not None and version_key(version) > version_key(limit)


def version_key(version: str) -> tuple:
    """Orders versions near enough as Gradle does: numerically, and a release after its own
    alpha, beta or release candidate."""
    numbers, _, qualifier = version.partition("-")
    return (tuple(int(p) if p.isdigit() else 0 for p in numbers.split(".")),
            qualifier == "", qualifier)


def cached_jar(group: str, artifact: str, version: str | None) -> str | None:
    """A jar from the Gradle cache or build/typecheck/plugins: the given version, or the
    newest when version is None."""
    for tree in (CACHE, PLUGINS):
        where = os.path.join(tree, group, artifact)
        if not os.path.isdir(where):
            continue
        versions = sorted(os.listdir(where), key=version_key)
        if version is not None:
            versions = [v for v in versions if v == version]
        for chosen in reversed(versions):
            for base, _, files in os.walk(os.path.join(where, chosen)):
                for name in files:
                    if name == f"{artifact}-{chosen}.jar":
                        return os.path.join(base, name)
    return None


def compiler_classpath() -> list[str]:
    parts = []
    for group, artifact, version in [
        ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", KOTLIN),
        ("org.jetbrains.kotlin", "kotlin-stdlib", KOTLIN),
        # The compiler reflects over its own argument classes to parse the command line.
        ("org.jetbrains.kotlin", "kotlin-reflect", KOTLIN),
        ("org.jetbrains.kotlin", "kotlin-script-runtime", KOTLIN),
        ("org.jetbrains.kotlin", "kotlin-daemon-embeddable", KOTLIN),
        ("org.jetbrains.intellij.deps", "trove4j", None),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", None),
        # Needed by the bytecode backend, which writes @NotNull onto generated members.
        ("org.jetbrains", "annotations", None),
    ]:
        jar = cached_jar(group, artifact, version)
        if not jar:
            if artifact in ("kotlin-script-runtime", "kotlin-daemon-embeddable", "trove4j"):
                continue
            fail(f"{group}:{artifact}:{version or 'any'} is not in the Gradle cache; "
                 f"run python tools/fetch_deps.py")
        parts.append(jar)
    return parts


# ---- dependency classes ----

def check_dependencies_match() -> None:
    """Guards against this file quietly falling behind the real build."""
    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    declared = set(re.findall(r'\bimplementation\("([^:"]+):([^:"]+):([^"]+)"\)', gradle))
    if declared != set(DEPENDENCIES):
        missing = declared - set(DEPENDENCIES)
        extra = set(DEPENDENCIES) - declared
        lines = ["DEPENDENCIES in tools/typecheck.py is out of step with build.gradle.kts"]
        lines += [f"  only in build.gradle.kts: {d}" for d in sorted(missing)]
        lines += [f"  only in typecheck.py:     {d}" for d in sorted(extra)]
        fail("\n".join(lines))


def dependency_jars() -> list[str]:
    """Unpacks each library's classes from its AAR, and collects plain jars as they are.

    Everything of the right name in the Gradle cache and build/typecheck/maven is included,
    which is coarser than a real resolution but errs towards having a class available rather
    than missing. Only the newest version of each artifact is kept, as Gradle's resolution
    would: with two versions on the classpath the compiler takes whichever comes first.
    """
    out = os.path.join(BUILD, "libs")
    os.makedirs(out, exist_ok=True)

    newest: dict[tuple[str, str, str], tuple[tuple, str]] = {}
    for tree in (CACHE, MAVEN):
        if not os.path.isdir(tree):
            continue
        for base, _, files in os.walk(tree):
            # Laid out group/artifact/version/<dir>/file.
            version_dir = os.path.dirname(base)
            version = os.path.basename(version_dir)
            artifact_dir = os.path.dirname(version_dir)
            owner = (os.path.basename(os.path.dirname(artifact_dir)), os.path.basename(artifact_dir))
            if owner[0] == "org.jetbrains.kotlin" and not owner[1].startswith(("kotlin-stdlib", "kotlin-parcelize-runtime", "kotlin-android-extensions-runtime")):
                continue
            if owner[0].startswith(("com.android.tools", "org.jetbrains.kotlin.", "org.gradle")):
                continue
            if above_pin(owner[0], owner[1], version):
                continue
            for name in files:
                if name.endswith(("-sources.jar", "-javadoc.jar")) or not name.endswith((".jar", ".aar")):
                    continue
                key = owner + (name.replace(version, "", 1),)
                candidate = (version_key(version), os.path.join(base, name))
                if key not in newest or candidate[0] > newest[key][0]:
                    newest[key] = candidate

    jars = []
    for _, path in sorted(newest.values(), key=lambda entry: entry[1]):
        name = os.path.basename(path)
        if name.endswith(".jar"):
            jars.append(path)
            continue
        extracted = os.path.join(out, name[:-4] + ".jar")
        if not os.path.exists(extracted):
            try:
                with zipfile.ZipFile(path) as archive:
                    with archive.open("classes.jar") as source:
                        with open(extracted, "wb") as target:
                            shutil.copyfileobj(source, target)
            except (KeyError, zipfile.BadZipFile):
                continue
        jars.append(extracted)
    return jars


# ---- generated sources: R, BuildConfig, view bindings ----

def resource_names() -> dict[str, set[str]]:
    """Every resource the R class would carry, by kind."""
    names: dict[str, set[str]] = {
        k: set() for k in
        ("id", "layout", "string", "drawable", "mipmap", "color", "style", "menu", "font",
         "array", "xml", "attr", "dimen", "bool", "integer", "plurals", "anim", "raw")
    }
    for base, _, files in os.walk(RES):
        folder = os.path.basename(base).split("-")[0]
        for name in files:
            path = os.path.join(base, name)
            stem = os.path.splitext(name)[0]
            if folder in ("layout", "drawable", "mipmap", "menu", "xml", "anim", "animator",
                          "raw", "color", "font"):
                names.setdefault(folder, set()).add(stem)
            if not name.endswith(".xml"):
                continue
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as error:
                fail(f"{path}: {error}")
            for element in root.iter():
                for value in element.attrib.values():
                    match = re.fullmatch(r"@\+id/([A-Za-z0-9_]+)", value or "")
                    if match:
                        names["id"].add(match.group(1))
            if folder == "values":
                for child in root:
                    kind = child.tag
                    if kind == "item":
                        kind = child.get("type", "")
                    if kind == "declare-styleable":
                        continue
                    key = {"string-array": "array", "integer-array": "array"}.get(kind, kind)
                    if key in names and child.get("name"):
                        names[key].add(child.get("name").replace(".", "_"))
    return names


def write_generated(target: str, names: dict[str, set[str]]) -> None:
    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    version_name = (re.search(r'versionName\s*=\s*"([^"]+)"', gradle) or [None, "0"])[1]
    version_code = (re.search(r"versionCode\s*=\s*(\d+)", gradle) or [None, "1"])[1]

    lines = [f"package {PACKAGE}", "", "object R {"]
    for kind in sorted(names):
        if not names[kind]:
            continue
        lines.append(f"    object {kind} {{")
        for name in sorted(names[kind]):
            lines.append(f"        const val {name}: Int = 0")
        lines.append("    }")
    lines.append("}")
    lines += [
        "",
        "object BuildConfig {",
        "    const val DEBUG: Boolean = true",
        f'    const val APPLICATION_ID: String = "{PACKAGE}"',
        f'    const val VERSION_NAME: String = "{version_name}"',
        f"    const val VERSION_CODE: Int = {version_code}",
        "}",
    ]
    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


def camel(name: str) -> str:
    head, *rest = name.split("_")
    return head + "".join(part.capitalize() for part in rest)


def binding_name(layout: str) -> str:
    return "".join(part.capitalize() for part in layout.split("_")) + "Binding"


_VIEW_PACKAGE = {
    "View": "android.view.View",
    "ViewGroup": "android.view.ViewGroup",
    "ViewStub": "android.view.ViewStub",
    "SurfaceView": "android.view.SurfaceView",
    "TextureView": "android.view.TextureView",
    "WebView": "android.webkit.WebView",
}


def view_type(tag: str) -> str:
    """The fully qualified type view binding would give a view of this tag."""
    if "." in tag:
        return tag
    if tag in ("view", "merge", "include", "fragment"):
        return "android.view.View"
    return _VIEW_PACKAGE.get(tag, f"android.widget.{tag}")


def write_bindings(target: str) -> None:
    """One class per layout, with a field per id, typed by the XML tag - so that
    `binding.somethingWrong` is a compile error here rather than a surprise in Android Studio."""
    layout_dir = os.path.join(RES, "layout")
    lines = ["package " + PACKAGE + ".databinding", ""]

    for name in sorted(os.listdir(layout_dir)):
        if not name.endswith(".xml"):
            continue
        layout = name[:-4]
        root = ET.parse(os.path.join(layout_dir, name)).getroot()

        fields: dict[str, str] = {}
        for element in root.iter():
            raw = element.get(f"{{{ANDROID_NS}}}id")
            match = re.fullmatch(r"@\+?id/([A-Za-z0-9_]+)", raw or "")
            if not match:
                continue
            if element.tag == "include":
                included = (element.get("layout") or "").split("/")[-1]
                fields[camel(match.group(1))] = binding_name(included)
            else:
                fields[camel(match.group(1))] = view_type(element.tag)

        lines.append(f"class {binding_name(layout)} {{")
        lines.append(f"    val root: {view_type(root.tag)} = null!!")
        for field, kind in sorted(fields.items()):
            if field != "root":
                lines.append(f"    val {field}: {kind} = null!!")
        lines.append("    companion object {")
        lines.append("        @JvmStatic fun inflate(inflater: android.view.LayoutInflater): "
                     f"{binding_name(layout)} = null!!")
        lines.append("        @JvmStatic fun inflate(inflater: android.view.LayoutInflater, "
                     "parent: android.view.ViewGroup?, attach: Boolean): "
                     f"{binding_name(layout)} = null!!")
        lines.append("    }")
        lines.append("}")
        lines.append("")

    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


# ---- running the compiler ----

def quote(value: str) -> str:
    # The compiler reads argfiles with Java string rules; Windows backslashes must be escaped.
    return '"' + value.replace("\\", "\\\\") + '"'


def compile_sources(sources: list[str], classpath: list[str], destination: str, label: str) -> list[str]:
    plugin = next((os.path.join(base, name) for base, _, files in os.walk(PLUGINS)
                   for name in files if name == COMPOSE_PLUGIN), None)
    if not plugin:
        fail(f"{COMPOSE_PLUGIN} missing; run python tools/fetch_deps.py")
    separator = ";" if os.name == "nt" else ":"
    os.makedirs(destination, exist_ok=True)
    argfile = os.path.join(BUILD, f"args-{label}.txt")
    with open(argfile, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("-classpath " + quote(separator.join(classpath)) + "\n")
        handle.write("-d " + quote(destination) + "\n")
        handle.write("-jvm-target 17\n")
        handle.write("-Xplugin=" + quote(plugin) + "\n")
        for source in sources:
            handle.write(quote(source) + "\n")

    command = [
        find_java(), "-Xss8m", "-Xmx3g", "-cp", separator.join(compiler_classpath()),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "@" + argfile,
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    output = result.stdout + result.stderr
    show_warnings = "--warnings" in sys.argv
    return [
        line for line in output.splitlines()
        if line.strip()
        and "unable to find" not in line
        and not line.startswith("info:")
        and (show_warnings or not line.startswith("warning:"))
        and "/generated/" not in line.replace("\\", "/")
    ]


def main() -> int:
    check_dependencies_match()
    android_jar = find_android_jar()

    generated = os.path.join(BUILD, "generated")
    classes = os.path.join(BUILD, "classes")
    shutil.rmtree(generated, ignore_errors=True)
    shutil.rmtree(classes, ignore_errors=True)
    os.makedirs(generated, exist_ok=True)
    write_generated(os.path.join(generated, "R.kt"), resource_names())
    write_bindings(os.path.join(generated, "Bindings.kt"))

    classpath = [android_jar] + dependency_jars()
    print(f"typecheck: Kotlin {KOTLIN} + Compose plugin, {len(classpath)} jars, android.jar from "
          f"{os.path.basename(os.path.dirname(android_jar))}")
    interesting = compile_sources([SRC, generated], classpath, classes, "main")

    errors = sum(1 for l in interesting if l.startswith("error:") or ": error:" in l or l.startswith("e: "))
    if not interesting:
        print("typecheck: no errors")
        return 0
    print("\n".join(interesting))
    print(f"\ntypecheck: {errors} error line(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
