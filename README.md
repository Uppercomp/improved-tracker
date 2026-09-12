# Tracker — improved build

An improved build of **Tracker**, the video analysis and modeling tool from the
[Open Source Physics](https://www.compadre.org/osp/) project, by Douglas Brown
and Wolfgang Christian.

This repository contains the modified sources, the build scripts, a
self-contained jar, and a Windows installer. Tracker is GPLv3 software; see
[LICENSE](tracker/LICENSE).

---

## Quick start

**Windows — installer / portable**

1. Download `Tracker-<version>-windows.zip` from the
   [Releases](../../releases) page.
2. Unzip it anywhere and run `Tracker\Tracker.exe`.

That is the whole installation. **A Java runtime is included**, and the video
engine (Xuggle/ffmpeg) plus the look & feel are bundled inside the jar, so
nothing else has to be installed.

**Any platform — the jar alone**

```
java -jar tracker-improved.jar
```

Or double-click `tracker-improved.jar`. Video works out of the box; no
classpath, no `Xuggle/` folder, no extra downloads.

---

## What this build changes

### Bug fixes

| Fix | Detail |
|---|---|
| **Video failed when double-clicking the jar** | `java -jar` ignores sibling jars, so the video engine was never on the classpath and every video was rejected as an unsupported format. The engine (Xuggle + its native ffmpeg) is now bundled inside the jar. |
| **The video type was never registered** | `XuggleMovieVideoType.register()` is only reached from `XuggleVideo`'s static initialiser, which nothing loaded early — so whether video worked depended on incidental class-loading order. `Tracker.registerVideoEngine()` now loads it explicitly at startup. |
| **`TrackView.getName()` NPE** | Installing a look & feel calls `Component.getName()` while UI defaults are being built, before the view's track is resolved. This threw `NullPointerException` and cascaded `SynthTableUI.paintCell` failures that could blank the table. `getName()`/`getIcon()` now null-guard. |
| **Plots not shown for a tracked mass** | `TFrame.initialize()` forced the main split divider to a collapsed `1.0` sentinel, hiding the plot/table pane. It now opens by default (`TFrame.showRightViewsOnStartup`). |
| **Frame slider confined to a small area** | The player cached the widest layout it had ever seen as its preferred width, so it was laid out ~1630 px wide inside a 930 px bar and the slider was clipped. The LAF also sized the slider from its own preferred width. New `PlayerBar` sizes the player to the bar and gives the slider all the leftover width. |
| **Cramped startup window** | The window was capped at `min(screenW*0.9, 1024 + extra*800)`, collapsing to 1024×768 on any modern display. It now uses ~90 % of the available screen at 4:3. |
| **Export hardening** | Try-with-resources on writes, failures surfaced to the user instead of only the console, success verified by existence *and* non-zero length. |
| **Auto-tracking edge cases** | Null-template and null-match guards in `AutoTracker.findMatchTarget`. |

### New features

- **Drag / terminal-velocity fit functions** (`TrackerFits`) in the curve
  fitter's built-in list, using Tracker's usual `a, b, c, d` parameters:
  - `y = a + b·tanh(c·x + d)` — quadratic drag, `v(t) = v_t·tanh(g t / v_t)`
  - `y = a + b·(1 − exp(−c·x + d))` — linear (Stokes) drag
- **`View → Theme`** submenu switching look & feel at runtime, remembered in
  the preferences. FlatLaf (Light/Dark/IntelliJ), Nimbus, Classic, and on macOS
  the native Aqua.
- **Temporal-consistency gate** in `AutoTracker` (opt-in, default off) via
  `-Dtracker.maxJumpFactor=<n>`: rejects a match that jumps an implausible
  distance from the previous point.
- **Python (`.py`) export** in the Data Export dialog: a NumPy-ready file with
  `t`, per-track columns and a `tracks` mapping.

### macOS specifics

- The native Aqua look & feel is kept **by default** on macOS; the bundled
  themes remain available from `View → Theme`. Forcing FlatLaf there would
  override Aqua for every widget while the OS still drew native window
  decorations and menus.
- **Native (Aqua)** is offered as a theme entry so a user who switches away can
  get back. Two class names are probed (`com.apple.laf.AquaLookAndFeel` on
  JDK 9+, `javax.swing.plaf.mac.MacLookAndFeel` on JDK 8) and installed through
  `OSPRuntime` so Apple integration stays wired up.
- `make_installer.sh` builds a `.dmg` on macOS (jpackage only builds for its
  host OS).

---

## Building

Requirements: **JDK 17+** (JDK 21 tested) and Python 3.

```bash
python build.py                       # -> tracker-improved.jar
python make_installer.py --zip        # -> installer/ (app image + portable zip)
```

On macOS or Linux use `./make_installer.sh` instead, which produces a `.dmg` or
`.deb`.

### How the build works

`tracker/src` holds only the classes this project patches. Everything else — the
Open Source Physics core, the video engine, all resource bundles and the look &
feel — comes from a base jar:

| Layout | base jar used |
|---|---|
| working tree | `tracker/distribution/tracker.jar` |
| this repository | `dist/tracker-improved.jar` |

`build.py` compiles the ~153 sources against that base, unpacks it, overlays the
fresh classes, merges FlatLaf and the video engine **only if the base lacks
them**, appends new `tracker.properties` keys, and re-jars with `Tracker` as the
main class. Running it on `dist/tracker-improved.jar` reproduces a jar of
identical size, so the published jar and the published sources are in sync.

---

## Repository layout

```
build.py                 compile + overlay + package the jar
make_installer.py        jpackage wrapper (Windows)
make_installer.sh        jpackage wrapper (macOS / Linux)
dist/                    self-contained jar (also the incremental base jar)
tracker/                 modified sources
  src/                     the patched Java sources
  libraries/               flatlaf, xuggle, slf4j
  srcfiles.txt             the exact compile list
```

---

## Verification performed on this build

- Compiles clean, 0 errors, ~1058 classes.
- Launches with no classpath and no `TRACKER_HOME`/`XUGGLE_HOME`, decodes video.
- Launches under a non-English locale (`-Duser.language=de`) without error.
- Theme switching exercised on a live window: all five themes install and the
  window survives each switch; the frame slider stays full-width (695 px).
- Packaged app image runs self-contained via `Tracker.exe` with its bundled JRE.
- Tracked positions cross-checked against an independent re-detection
  (median disagreement 1.0 px) and against a ground-truth simulation, which
  recovers a known `v_t` to within 1 %.

---

## Credits and licence

Tracker is Copyright (c) Douglas Brown, Wolfgang Christian and Robert M. Hanson,
and is distributed under the **GNU General Public License v3**. This build is a
derivative work and is licensed the same way — see [tracker/LICENSE](tracker/LICENSE).
Bundled third-party components keep their own licences:

- **Xuggle** / FFmpeg — LGPL (video engine)
- **FlatLaf** — Apache 2.0 (look & feel)
- **SLF4J** — MIT
