#!/usr/bin/env python
"""Build the improved Tracker jar from tracker/src and overlay it on the base jar.

Usage: python build.py [--out path/to/tracker.jar]

Steps
  1. compile every .java listed in tracker/srcfiles.txt (excluding src/test)
     against the base jar
  2. unpack the base jar and copy the freshly compiled classes over it
  3. merge the FlatLaf look & feel (if it is not already inside the base jar)
  4. re-jar with Tracker as the Main-Class

The base jar supplies the Open Source Physics core classes, the video engine, all
resources and the look & feel; only the classes compiled here are replaced. It is
looked for in two places so the layout works both in the working tree
(tracker/distribution/tracker.jar) and in the published repository
(dist/tracker-improved.jar).
"""
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
TRK = os.path.join(ROOT, "tracker")
LIB = os.path.join(TRK, "libraries")
SRC = os.path.join(TRK, "src")
SRCFILES = os.path.join(TRK, "srcfiles.txt")
BUILD = os.path.join(ROOT, "build")
CLASSES = os.path.join(BUILD, "classes")
STAGE = os.path.join(BUILD, "stage")
MAIN = "org.opensourcephysics.cabrillo.tracker.Tracker"

# base jar candidates, in priority order
_BASE_CANDIDATES = [
    os.path.join(TRK, "distribution", "tracker.jar"),
    os.path.join(ROOT, "dist", "tracker-improved.jar"),
]


def find_base_jar():
    for p in _BASE_CANDIDATES:
        if os.path.exists(p):
            return p
    return _BASE_CANDIDATES[0]


BASE_JAR = find_base_jar()
XUGGLE = os.path.join(LIB, "xuggle-xuggler-server-all.jar")
SLF4J = os.path.join(LIB, "slf4j-api.jar")
# optional modern look & feel, merged only if the base jar lacks it
FLATLAF = os.path.join(LIB, "flatlaf-3.5.4.jar")


def run(cmd, **kw):
    print("+", " ".join(cmd), flush=True)
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        raise SystemExit("command failed: " + " ".join(cmd))


def merge_library_jar(jar):
    """Extract a library jar into the staging tree, skipping its manifest.

    Overwriting META-INF/MANIFEST.MF would destroy the Main-Class entry that
    makes the finished jar double-clickable, so it is always excluded. The
    library's own classes and resources (including Xuggle's native ffmpeg
    library) are copied in.
    """
    tmp = os.path.join(BUILD, "lib_tmp")
    if os.path.exists(tmp):
        shutil.rmtree(tmp)
    os.makedirs(tmp)
    run(["jar", "xf", jar], cwd=tmp)
    skipped = 0
    for root, _dirs, names in os.walk(tmp):
        rel = os.path.relpath(root, tmp)
        for name in names:
            src_file = os.path.join(root, name)
            rel_path = name if rel == "." else os.path.join(rel, name)
            if rel_path.replace("\\", "/") == "META-INF/MANIFEST.MF":
                skipped += 1
                continue
            dst_file = os.path.join(STAGE, rel_path)
            os.makedirs(os.path.dirname(dst_file), exist_ok=True)
            shutil.copy2(src_file, dst_file)
    shutil.rmtree(tmp)
    print(f"  merged {os.path.basename(jar)} (skipped {skipped} manifest)", flush=True)


def merge_resources():
    """Add any tracker.properties keys from tracker/src that the base jar lacks.

    The base jar supplies the shipped resource bundles; new keys added by this
    build (for example new menu labels) are appended so nothing is lost.
    """
    src = os.path.join(SRC, "org", "opensourcephysics", "cabrillo", "tracker",
                       "resources", "tracker.properties")
    dst = os.path.join(STAGE, "org", "opensourcephysics", "cabrillo", "tracker",
                       "resources", "tracker.properties")
    if not os.path.exists(src) or not os.path.exists(dst):
        return

    def keys_of(text):
        found = set()
        for line in text.splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            found.add(stripped.split("=", 1)[0].strip())
        return found

    with open(dst, encoding="utf-8", errors="replace") as f:
        existing_text = f.read()
    existing = keys_of(existing_text)

    extra = []
    with open(src, encoding="utf-8", errors="replace") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            key = stripped.split("=", 1)[0].strip()
            if key not in existing:
                existing.add(key)
                extra.append(line.rstrip("\n"))

    if extra:
        with open(dst, "a", encoding="utf-8") as f:
            f.write("\n# --- added by the improved Tracker build ---\n")
            f.write("\n".join(extra))
            f.write("\n")
        print(f"tracker.properties: added {len(extra)} new keys", flush=True)


def main():
    out = os.path.join(ROOT, "tracker-improved.jar")
    args = sys.argv[1:]
    if "--out" in args:
        out = args[args.index("--out") + 1]

    if not os.path.exists(BASE_JAR):
        raise SystemExit("base jar not found: " + BASE_JAR)

    for d in (CLASSES, STAGE):
        if os.path.exists(d):
            shutil.rmtree(d)
        os.makedirs(d)

    with open(SRCFILES, encoding="utf-8") as f:
        entries = [ln.strip() for ln in f if ln.strip() and not ln.startswith("src/test/")]
    files = [os.path.join(SRC, e[4:].replace("/", os.sep)) for e in entries]
    missing = [f for f in files if not os.path.exists(f)]
    if missing:
        raise SystemExit("missing sources:\n  " + "\n  ".join(missing))
    print(f"compiling {len(files)} sources", flush=True)

    cp = os.pathsep.join([BASE_JAR, XUGGLE, SLF4J])
    t0 = time.time()
    # @argfile keeps the command line short. javac argfiles treat backslash as an
    # escape character, so all paths are written with forward slashes.
    def fs(p):
        return p.replace("\\", "/")

    argfile = os.path.join(BUILD, "javac.args")
    with open(argfile, "w", encoding="utf-8") as f:
        f.write("-nowarn\n")
        f.write("-encoding\nUTF-8\n")
        f.write("-source\n11\n-target\n11\n")
        f.write("-cp\n" + fs(cp) + "\n")
        f.write("-d\n" + fs(CLASSES) + "\n")
        for p in files:
            f.write(fs(p) + "\n")
    run(["javac", "@" + fs(argfile)])
    print(f"compiled in {time.time() - t0:.1f}s", flush=True)

    print("unpacking base jar", flush=True)
    run(["jar", "xf", BASE_JAR], cwd=STAGE)
    print("overlaying compiled classes", flush=True)
    shutil.copytree(CLASSES, STAGE, dirs_exist_ok=True)
    merge_resources()

    # The published base jar already contains FlatLaf and the video engine, so
    # only merge each library when the base lacks it. This keeps a rebuild from
    # the published repo working without the 44 MB Xuggle jar.
    def base_has(entry):
        import zipfile
        try:
            with zipfile.ZipFile(BASE_JAR) as z:
                return entry in z.namelist()
        except Exception:
            return False

    if base_has("com/formdev/flatlaf/FlatLightLaf.class"):
        print("FlatLaf already present in the base jar - skipping merge", flush=True)
    elif os.path.exists(FLATLAF):
        print("merging FlatLaf look & feel", flush=True)
        run(["jar", "xf", FLATLAF], cwd=STAGE)
    else:
        print("FlatLaf not found at " + FLATLAF + " - falling back to Nimbus", flush=True)

    if base_has("com/xuggle/xuggler/IContainer.class"):
        print("video engine already present in the base jar - skipping merge", flush=True)
    else:
        for label, jar in (("Xuggle video engine", XUGGLE), ("slf4j", SLF4J)):
            if not os.path.exists(jar):
                print(label + " not found at " + jar + " - skipping", flush=True)
                continue
            print("merging " + label, flush=True)
            merge_library_jar(jar)

    # Merge the video engine (Xuggle + slf4j) when the base jar does not already
    # carry it. `java -jar` ignores sibling jars on disk, so without the engine
    # inside the jar every video fails to load when the jar is double-clicked.
    # Xuggle's native ffmpeg library ships inside its jar, so bundling the classes
    # is enough.
    if os.path.exists(out):
        os.remove(out)
    print("packaging " + out, flush=True)
    run(["jar", "cfe", out, MAIN, "."], cwd=STAGE)
    print(f"built {out} ({os.path.getsize(out)} bytes)", flush=True)


if __name__ == "__main__":
    main()
