# BookScanner

An offline Android wanted-book finder for op shops. Sweep the camera across a shelf; potential matches get a box in the live view. Built with PP-OCRv4 Mobile, NCNN, CameraX and OpenCV, with the Pixel 10 as the first testing target.

**Version 0.4 — real-shelf accuracy and Pixel 10 performance still need device testing.** A box is a prompt to check a book yourself, not a confirmed identification.

## Using the app

1. Open **Wanted → Add**. Enter a title and optionally its author, or leave the title blank for **any book by that author**.
2. Add alternative titles in the aliases field. Title patterns support `*` (any text) and `?` (one character). For example, `Matilda*` matches visible title text starting with “Matilda”. The app cannot infer series membership when a series name is absent from the spine.
3. Return to **Scan**, allow the camera and sweep slowly. Tap the camera view to focus; use the torch or zoom if needed.
4. **Orange** boxes are immediate hints: a partial title, surname or imperfect reading can highlight a book. There is no confirmation wait or green state. Check the physical book yourself; more false positives are intentional. “Check title” means an author clue for a specific wanted title.
5. A gentle tick announces a new detection; turn it off in **Options** if preferred. Use **Pause** to pause recognition. The camera preview remains live; leaving the app or opening the wanted list releases the camera.

Keep text reasonably large in the view. Glare, ornate lettering, tightly stacked characters and fast movement can prevent recognition. Try a few books at a time. There is no saved photo, scan history or summary screen. Outlines follow the detected text, not a guaranteed segmentation of the entire book.

Wanted entries can be edited, disabled and deleted. **Import/Export** uses the Android document picker and the versioned JSON format in [the example list](docs/wanted-example.json). Import previews the entry count and asks before replacing the existing list. The example is not loaded automatically.

## Upgrading from an earlier version

**Export your wanted list before replacing the installed APK.** GitHub builds currently use per-run debug keys, so Android may require uninstalling the old app before installing the new one. Reimport your exported JSON afterwards. Version 0.4 keeps the same list format. Builds from the same Android Studio installation normally share its local debug key.

## Install and build

Requires **Android 15 or later**; intended first for Pixel 10. Targets Android API 36 and runs on newer Android versions as well.

### Android Studio

1. Choose **Get from VCS** (or **File → New → Project from Version Control**).
2. Clone `https://github.com/pointandyshoot/BookScanner.git` and open its root folder. Run `git submodule update --init --recursive` to obtain the pinned NCNN source and OCR models.
3. Use **JDK 17** for Gradle and install **Android SDK Platform 36** and **Build Tools 36.0.0**, **NDK 28.2.13676358** and **CMake 3.22.1** when prompted. Let Gradle sync; the first build needs internet to download dependencies.
4. Enable USB debugging on the Pixel, connect it, select it in the device list and press **Run**.
5. To get subsequent changes, use **Git → Pull** before rebuilding.

Or with JDK 17 and the Android SDK configured:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Windows: use `gradlew.bat`. The Gradle 8.13 wrapper is included; Android Gradle Plugin is pinned to 8.13.2. Release builds are unsigned; debug builds are installable and intended for testing. ARM64 is for phones such as Pixel 10; x86_64 is for the emulator. A universal APK is also built. Native inference is optimised in debug builds.

### GitHub APK

[Android build](https://github.com/pointandyshoot/BookScanner/actions/workflows/android.yml) tests, lints and builds the app on pushes and pull requests. Open a successful run and download **BookScanner-debug** under Artifacts, unzip it, then install `app-arm64-v8a-debug.apk` on the phone. GitHub sign-in is needed to download workflow artifacts. Android may ask you to allow installation from the app opening the APK. Each clean CI runner uses a new debug signing key; uninstall an older differently signed build before installing (export your wanted list first), or build consistently with Android Studio on your own computer.

## Version 0.4: immediate shelf hints

- One orange highlight as soon as a useful clue is read. No multi-frame confirmation gate.
- Exact distinctive words of four or more letters, five-letter fuzzy fragments and more permissive matching. Short titles still need exact whole words; common publishing words are excluded from fragment matching.
- Nearby parallel lines can contribute combined text; their boxes stay anchored to actual lettering. This is geometric pairing, not guaranteed book segmentation.
- Three detail passes per full-view pass, using a 3 × 3 grid of overlapping 45%-size crops. Tracked books get only occasional rereads so they do not monopolise discovery.
- Lower detector and OCR confidence cut-offs. Very clear readings skip the reverse-direction inference. Work is checked against a 1.1-second soft budget between regions; individual inference can exceed this.
- Existing independent optical-flow tracking and once-per-appearance haptics retained.
- NCNN packed CPU layouts enabled; FP16 and GPU execution remain disabled.
- PP-OCRv4 remains offline. Pixel 10 shelf recall and speed are unmeasured until a phone trial.

## Privacy and storage

Only the wanted list, scanner options and camera-permission prompt state persist locally. Camera frames, OCR crops, reference patches and a short motion-transform history remain in memory and are discarded. No internet permission, analytics, accounts, microphone, GPS or media-library permission. Camera and vibration permissions are used. Network permissions from dependency manifests are explicitly removed. Automatic Android backup is disabled; export a wanted-list backup before uninstalling. The document provider you choose for manual export may itself be cloud-backed.

## Documentation

- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Device testing checklist](docs/TESTING.md)
- [Wanted-list example](docs/wanted-example.json)
- [Third-party credits and licences](THIRD_PARTY_NOTICES.md)

## Credits

Thanks to [Sappelen/BookSpineScanner](https://github.com/Sappelen/BookSpineScanner), licensed CC0 1.0, for the spine-first cropping and mild histogram-stretch preprocessing ideas. This app uses an independent Java implementation; its application code is not copied. Version 0.2 bundles the official OpenCV Android library for independent tracking and deskew implementations. See the notices for the reviewed source revision and implementation differences.

Original BookScanner code is MIT licensed. PaddleOCR, NCNN, AndroidX, OpenCV and the Gradle wrapper retain their own licences.
