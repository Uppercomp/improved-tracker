#!/usr/bin/env python
"""Build a self-contained, double-clickable Tracker installer.

Windows  : produces an installable .exe/.msi if WiX is available, and always a
           portable app-image folder + ZIP as a fallback.
macOS    : run on a Mac to produce a .dmg (jpackage only builds for its host OS).
Linux    : run on Linux to produce a .deb/.rpm.

The app is fully self-contained: tracker.jar already bundles the video engine
(Xuggle + its native ffmpeg), the FlatLaf look & feel and all resources, so the
only extra ingredient is a Java runtime, which jpackage bundles in.

Usage:
  python make_installer.py [--app-version 1.0.0] [--full-runtime] [--zip]
"""
import argparse
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
TRK = os.path.join(ROOT, "tracker")
JAR = os.path.join(ROOT, "tracker-improved.jar")
LIB = os.path.join(TRK, "libraries")
ICON = os.path.join(TRK, "src", "org", "opensourcephysics", "cabrillo",
                    "tracker", "resources", "images", "tracker_icon_256.png")
OUT = os.path.join(ROOT, "installer")
MAIN = "org.opensourcephysics.cabrillo.tracker.Tracker"
APP_NAME = "Tracker"

# Modules a Swing desktop app with image/video I/O needs. java.base,
# java.desktop and java.logging are the essentials; the rest cover XML,
# preferences, networking and scripting used by Tracker and OSP.
MODULES = [
    "java.base", "java.desktop", "java.logging", "java.prefs",
    "java.xml", "java.net.http", "java.scripting", "java.sql",
    "java.naming", "java.management", "jdk.unsupported",
]


def rmtree_retry(path, tries=8):
    """Remove a tree, retrying briefly: on Windows a just-killed launcher can
    still hold Tracker.exe open for a moment."""
    import time
    for attempt in range(tries):
        if not os.path.exists(path):
            return
        try:
            shutil.rmtree(path)
            return
        except PermissionError:
            if attempt == tries - 1:
                raise
            print(f"  (retry {attempt + 1}: {os.path.basename(path)} is locked)")
            time.sleep(1.5)


def run(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd), flush=True)
    r = subprocess.run(cmd, **kw)
    return r.returncode


def host_os():
    if sys.platform.startswith("win"):
        return "windows"
    if sys.platform == "darwin":
        return "mac"
    return "linux"


def have(tool):
    return shutil.which(tool) is not None


def build_runtime(dest, full=False):
    """Create a trimmed Java runtime with jlink, or copy the whole JDK runtime."""
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        java_home = os.path.dirname(os.path.dirname(shutil.which("java") or ""))
    if full or not have("jlink"):
        src = os.path.join(java_home, "jmods")
        print(f"using the full runtime from {java_home}")
        # jpackage can take the JDK's runtime via --runtime-image; a plain copy works
        shutil.copytree(java_home, dest, symlinks=True,
                        ignore=shutil.ignore_patterns("jmods", "include", "lib", "bin"))
        return None
    cmd = ["jlink", "--add-modules", ",".join(MODULES),
           "--output", dest, "--strip-debug", "--no-header-files",
           "--no-man-pages", "--compress", "zip-6"]
    if run(cmd) != 0:
        print("jlink failed; falling back to the full runtime")
        return build_runtime(dest, full=True)
    return dest


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--app-version", default="1.0.0")
    ap.add_argument("--full-runtime", action="store_true",
                    help="bundle the whole JRE instead of a jlink-trimmed one")
    ap.add_argument("--zip", action="store_true",
                    help="also produce a portable ZIP of the app image")
    ap.add_argument("--name", default=APP_NAME)
    args = ap.parse_args()

    if not os.path.exists(JAR):
        sys.exit("tracker-improved.jar not found - run build.py first")
    if not have("jpackage"):
        sys.exit("jpackage not found - a JDK 17+ is required")

    os.makedirs(OUT, exist_ok=True)
    os_name = host_os()
    # jpackage names the app-image directory after --name
    image_dir = os.path.join(OUT, args.name)
    rmtree_retry(image_dir)

    # --- dependencies alongside the jar in an input dir -----------------------
    input_dir = os.path.join(OUT, "input")
    rmtree_retry(input_dir)
    os.makedirs(input_dir)
    shutil.copy2(JAR, input_dir)

    # --- bundled runtime ------------------------------------------------------
    runtime = None
    rt_dir = os.path.join(OUT, "runtime")
    rmtree_retry(rt_dir)
    runtime = build_runtime(rt_dir, full=args.full_runtime)

    # --- icon -----------------------------------------------------------------
    icon_arg = []
    if os.path.exists(ICON):
        ico = os.path.join(OUT, "tracker.ico")
        try:
            from PIL import Image
            im = Image.open(ICON).convert("RGBA")
            im.save(ico, sizes=[(16, 16), (32, 32), (48, 48), (64, 64),
                                (128, 128), (256, 256)])
            icon_arg = ["--icon", ico]
            print("icon:", ico)
        except Exception as e:
            print("could not build an .ico (%s); using the default icon" % e)

    # --- app image ------------------------------------------------------------
    cmd = ["jpackage",
           "--type", "app-image",
           "--name", args.name,
           "--app-version", args.app_version,
           "--input", input_dir,
           "--main-jar", os.path.basename(JAR),
           "--main-class", MAIN,
           "--dest", OUT,
           "--vendor", "Tracker (Open Source Physics)",
           "--description", "Tracker video analysis and modeling tool",
           "--java-options", "-Xmx1024m",
           "--verbose"]
    if runtime:
        cmd += ["--runtime-image", runtime]
    cmd += icon_arg
    print("\n=== building app image ===")
    if run(cmd) != 0:
        sys.exit("jpackage app-image failed")

    print("\napp image:", image_dir)

    # --- optional native installer -------------------------------------------
    pkg_type = None
    if os_name == "windows":
        if have("candle") and have("light") or have("wix"):
            pkg_type = "msi"
        else:
            print("\nWiX not found, so no .msi/.exe installer can be built.")
            print("Install WiX 3.x and re-run to get an installable package.")
    elif os_name == "mac":
        pkg_type = "dmg"
    else:
        pkg_type = "deb" if have("dpkg-deb") else None

    if pkg_type:
        cmd = ["jpackage",
               "--type", pkg_type,
               "--name", args.name,
               "--app-version", args.app_version,
               "--input", input_dir,
               "--main-jar", os.path.basename(JAR),
               "--main-class", MAIN,
               "--dest", OUT,
               "--vendor", "Tracker (Open Source Physics)",
               "--description", "Tracker video analysis and modeling tool",
               "--java-options", "-Xmx1024m"]
        if runtime:
            cmd += ["--runtime-image", runtime]
        if pkg_type in ("msi", "exe"):
            cmd += ["--win-menu", "--win-shortcut", "--win-dir-chooser"]
        cmd += icon_arg
        print(f"\n=== building {pkg_type} installer ===")
        if run(cmd) != 0:
            print(f"{pkg_type} build failed - the app image above is still usable")

    # --- portable zip ---------------------------------------------------------
    if args.zip:
        print("\n=== zipping the app image ===")
        base = os.path.join(OUT, f"{args.name}-{args.app_version}-{os_name}")
        if os.path.exists(base + ".zip"):
            os.remove(base + ".zip")
        shutil.make_archive(base, "zip", OUT, os.path.basename(image_dir))
        print("wrote", base + ".zip")

    print("\ndone. contents of", OUT)
    for n in sorted(os.listdir(OUT)):
        p = os.path.join(OUT, n)
        size = os.path.getsize(p) if os.path.isfile(p) else 0
        print(f"   {n}{'' if os.path.isfile(p) else '/'}")


if __name__ == "__main__":
    main()

