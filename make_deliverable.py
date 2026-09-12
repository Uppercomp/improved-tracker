#!/usr/bin/env python
"""Build tracker-improved.jar and refresh the deliverable directory.

Runs build.py to produce the jar, then copies the jar and the modified sources
into deliverable/ together with launcher scripts.
"""
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
TRK = os.path.join(ROOT, "tracker")
DELIV = os.path.join(ROOT, "deliverable")
SOURCES = os.path.join(DELIV, "sources")
JAR = os.path.join(ROOT, "tracker-improved.jar")

# sources changed or added by this work
CHANGED = [
    "src/org/opensourcephysics/cabrillo/tracker/AutoTracker.java",
    "src/org/opensourcephysics/cabrillo/tracker/ExportDataDialog.java",
    "src/org/opensourcephysics/cabrillo/tracker/MainTView.java",
    "src/org/opensourcephysics/cabrillo/tracker/PlayerBar.java",
    "src/org/opensourcephysics/cabrillo/tracker/TFrame.java",
    "src/org/opensourcephysics/cabrillo/tracker/TMenuBar.java",
    "src/org/opensourcephysics/cabrillo/tracker/TrackPlottingPanel.java",
    "src/org/opensourcephysics/cabrillo/tracker/TrackView.java",
    "src/org/opensourcephysics/cabrillo/tracker/TableTrackView.java",
    "src/org/opensourcephysics/cabrillo/tracker/Tracker.java",
    "src/org/opensourcephysics/cabrillo/tracker/TrackerFits.java",
    "src/swingjs/api/JSUtilI.java",
]


def main():
    subprocess.run([sys.executable, os.path.join(ROOT, "build.py")], check=True, cwd=ROOT)
    os.makedirs(SOURCES, exist_ok=True)
    for rel in CHANGED:
        s = os.path.join(TRK, rel.replace("/", os.sep))
        if not os.path.exists(s):
            print("missing source (skipped):", rel)
            continue
        shutil.copy2(s, os.path.join(SOURCES, os.path.basename(rel)))
        print("copied", os.path.basename(rel))
    print("jar:", JAR, os.path.getsize(JAR), "bytes")


if __name__ == "__main__":
    main()
