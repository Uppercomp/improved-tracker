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
| **Auto-tracker NPE on a failed match** | `FrameData.matchWidthAndHeight` started as `null`, but `markCurrentFrame`, `getStatusCode` and the info pane all dereference it. A fresh frame that had not been searched threw `NullPointerException` (upstream has the same flaw). The field is now always a valid array, `clear()` keeps it non-null, and the two callers additionally null-guard. |
| **Degenerate template threw out of the matcher** | `FrameData.setTemplate` called `createMagnifiedImage` on a null/zero-size template image (empty mask, or a template larger than the frame), so the guard the caller had for a missing template was unreachable. `setTemplate` now returns early and lets the caller's guard work. |
| **Python export corrupted control characters** | `pyQuote` used `String.format("%cn%04x", ...)`, which turned a carriage return into a literal `\n000d` instead of an escape. Now emits a correct `\xNN`. |
| **Python export NPE on a deleted track** | The track selection is a `BitSet` of IDs, not pruned when a track is deleted while the dialog is open, so `TTrack.getTrack(k)` could return `null` and the export dereferenced it. Nulls are now skipped. |
| **Theme menu offered unusable entries** | FlatLaf themes were listed unconditionally even when the classes were absent (e.g. an IDE classpath without the FlatLaf jar), so choosing one silently did nothing. Unavailable themes are now filtered out. |
| **Theme lost its tuning after a restart** | `applyModernLookAndFeel` restored a saved theme without applying the FlatLaf cosmetic tweaks that a runtime switch applies, so the same theme looked different after a restart. Now applied. |
| **Failed theme switch recorded as success** | `setTheme` ignored the `false` returned by `OSPRuntime.setLookAndFeel` (which rolls back on failure) and still saved the choice. It now reports failure instead. |
| **Fit registration could report false success** | `TrackerFits.register()` set its `registered` flag *before* adding the fits and swallowed failures silently, so a partial failure left the fits missing while claiming success. The flag is now set last, the method is synchronized (it runs from both the startup thread and the EDT and mutates a shared static list), and failures are logged. |
| **The drag fits drew a flat / straight line** | `UserFunction.setExpression` was being called *before* `setParameters`. That order makes the expression never compile — `setExpression` returns **false** without throwing — so `evaluate()` returned 0 for every x. The fit therefore drew a flat line at zero, and a fitter handed such a function appears to produce a straight line. `setParameters` must come first: it registers the parameter names, and `setExpression` then substitutes them and compiles. The failure is now also checked and logged. |
| **The tanh fit's parameters were degenerate** | The original fit was `a + b·tanh(c·x + d)`, the generic four-parameter form. On v–t data in seconds the whole set spans the tanh argument 0…~1.5, where tanh is nearly linear, so (a) the curve looks like a straight line and (b) **a and b are not separately identified**: fitting real data converges to a ≈ −2175, b ≈ +2176 from one start and a ≈ −2239, b ≈ +2240 from another, both with RMS 0.06706. The value a student would read as the terminal velocity is meaningless. Replaced with the physical form `A·tanh((x − t0)/tau)`, which converges to the same answer from every starting guess. |

| **Export hardening** | Try-with-resources on writes, failures surfaced to the user instead of only the console, success verified by existence *and* non-zero length. |


### New features

- **Drag / terminal-velocity fit functions** (`TrackerFits`) in the curve
  fitter's built-in list. Both use the physical parameterisation so the fitted
  numbers are the quantities being measured:
  - `A*tanh((x - t0)/tau)` — quadratic drag; **A is the terminal velocity** in
    m/s, `tau = v_t/g` in s, so **g = A/tau**, and `t0` is the release time.
    Reached by `v(t) = v_t·tanh(g·t / v_t)`.
  - `A*(1 - exp(-(x - t0)/tau))` — linear (Stokes) drag; A is the terminal
    velocity and `tau = m/k`.
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

Verified on **Windows 11, JDK 21** (the only platform available while building):

- Compiles clean, 0 errors, ~1058 classes.
- Launches with no classpath and no `TRACKER_HOME`/`XUGGLE_HOME`, decodes video.
- Launches under a non-English locale (`-Duser.language=de`) without error.
- Theme switching exercised on a live window: every theme installs and the
  window survives each switch; the frame slider stays full-width (695 px).
- Drag fits confirmed present in `DatasetCurveFitter.defaultFits` at runtime
  (11 fits total, including both drag models).
- Packaged app image runs self-contained via `Tracker.exe` with its bundled JRE.
- Untracked positions cross-checked against an independent re-detection
  (median disagreement 1.0 px) and against a ground-truth simulation, which
  recovers a known `v_t` to within 1 %.

### macOS

**Not run on macOS.** No Mac was available, so macOS support here rests on code
inspection rather than a live test. What was done:

- Every platform branch the new code touches was reviewed, and nothing
  Windows-specific was added: no hard-coded drive letters or backslashes, no
  `os.name` checks, no Windows-only APIs in the new code.
- The default-look-&-feel change on macOS was checked for equivalence with
  upstream behaviour.
- `make_installer.sh` builds a `.dmg` (and generates an `.icns` from the bundled
  PNG). It has been written but **not executed**, because jpackage can only
  build for its host OS.

If you are on a Mac, please run `./make_installer.sh` and report anything that
misbehaves.

### Known limitations

- The Java sources here are an **overlay**: `tracker/src` holds only the classes
  this project patches, and the rest comes from the base jar. This is not a
  complete standalone fork of the Tracker codebase.
- `java -jar tracker.jar relative\path\video.mp4` mishandles a relative path
  (it collapses to `file:/video.mp4`). This is pre-existing upstream behaviour,
  reproduced identically on the unmodified stock jar, and was left alone;
  absolute paths work.
- The Windows build has no `.msi`, only the portable app-image ZIP, because WiX
  is not installed on the build machine. `make_installer.py` emits an `.msi`
  automatically when WiX is present.

---

## Credits and licence

Tracker is Copyright (c) Douglas Brown, Wolfgang Christian and Robert M. Hanson,
and is distributed under the **GNU General Public License v3**. This build is a
derivative work and is licensed the same way — see [tracker/LICENSE](tracker/LICENSE).
Bundled third-party components keep their own licences:

- **Xuggle** / FFmpeg — LGPL (video engine)
- **FlatLaf** — Apache 2.0 (look & feel)
- **SLF4J** — MIT
