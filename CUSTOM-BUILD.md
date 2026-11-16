# Custom source builds — no upstream release updater

This fork retains upstream attribution/licensing, but builds from local Java/resources/assets. It does not need a GitHub release APK or a live Git remote to build once the local SDK/JDK prerequisites are installed.

## Runtime update policy

- `AppUpdater` now reads only the installed package's version name. There is no HTTP client, release URL, worker thread, release cache reader, update prompt, APK download, LocalAdb install call or PackageInstaller code in this class.
- `MainActivity` no longer starts an update check.
- Settings → Updates is informational: installed version and **Custom source build**. No automatic-update switch, check button, upstream version or install action remains.
- The updater's install-status receiver is removed from the manifest.
- Existing `updates` preferences are not cleared or changed; nothing reads them now. Old upstream release strings in the translation catalog are inert, not execution paths.
- Internet permission remains for existing weather/artwork/other launcher features. Other app-install utilities and associated permissions remain unchanged; this change removes upstream **self-update** actions, not unrelated tools.
- An already-installed older APK is unchanged until a separately approved in-place installation. These source changes alone do not stop prompts from that older APK.

## Build from local source

Prerequisites: Python 3, JDK 17+ (`java`, `javac` on PATH), locally installed Android SDK platform android-34 and build-tools 34.0.0, and the original signing key. No Maven/Gradle dependency downloads or GitHub release requests are used by this path.

Use the safe entry point, **not the legacy `build.sh`**. The legacy script recursively clears `build/`; the new builder never deletes old outputs, never generates/replaces keys and never contacts a device.

```sh
cd /path/to/tvOS-Launcher
python3 -B -m unittest discover -s tests -p 'test_*.py'

# Inspect first; this creates no build output.
ANDROID_HOME=/opt/android-sdk python3 -B tools/build_local.py \
  --version-code 30 --version-name 2.1.6-jaggu.14-source-only \
  --output-root /absolute/path/to/build-artifacts --dry-run

# Compile/sign into a NEW child directory each time; no cleanup or installation.
ANDROID_HOME=/opt/android-sdk python3 -B tools/build_local.py \
  --version-code 30 --version-name 2.1.6-jaggu.14-source-only \
  --output-root /absolute/path/to/build-artifacts
```

Use a version code greater than the target device's installed code for a future deployment; `30` here identifies this source-only candidate, not an automatically discovered universal code. The builder prints its new APK path and records source hashes, version, output path and APK SHA-256 in `build-manifest.json`. It stages the current working files, including any unrelated uncommitted files present: a successful build does not certify those changes or make this a reviewed home redesign.

Default signing reads `debug.keystore` from the source root. If absent, the build fails rather than making another key. On a different development machine, transfer the original key privately and safely; never include it in a distributable backup or Git. For an existing release-key workflow, set `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASS` and optionally `RELEASE_KEY_ALIAS`. Password is passed to apksigner through its environment, not printed on the command line.

The build helper does not fetch source. Optional upstream changes must be reviewed/merged deliberately into this fork before building. A fork is not independent authorship: retain LICENSE and third-party notices.

## Verification scope

`tests/test_custom_updates.py` checks the actual source/manifest for removal of startup/settings update paths and the updater receiver. `tests/test_local_build.py` exercises dry-run preflight and asserts that its temporary output root remains empty. Real full compilation and signature verification are also required for candidate evidence.

These source-level regression checks and an APK build are **not** Android runtime/UI tests. No TV, emulator startup or real network-traffic capture is implied. Runtime weather and other network features are not disabled by this patch.

## Installation boundary and preservation

This document deliberately does not execute or automate deployment. Back up the target's current settings, APK and assets first; ensure matching package ID and signing certificate. Use an approved in-place replacement only. Never uninstall, clear launcher data, replace the signing key or change default-home/package-enable states to bypass an installation error.

Control Center rendering, labels/actions and the home layout are outside this update-removal change. The Golden Summit home is a separate unfinished native implementation, not included in this candidate.
