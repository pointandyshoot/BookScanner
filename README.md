# BookScanner

An offline Android wanted-book finder for op shops. Sweep the camera across a shelf; potential matches get a box in the live view. Built with PP-OCRv4 Mobile, NCNN, CameraX and OpenCV, with the Pixel 10 as the first testing target.

**Version 0.3 — real-shelf accuracy and Pixel 10 performance still need device testing.** A box is a prompt to check a book yourself, not a confirmed identification.

## Using the app

1. Open **Wanted → Add**. Enter a title and optionally its author, or leave the title blank for **any book by that author**.
2. Add alternative titles in the aliases field. Title patterns support `*` (any text) and `?` (one character). For example, `Matilda*` matches visible title text starting with “Matilda”. The app cannot infer series membership when a series name is absent from the spine.
3. Return to **Scan**, allow the camera and sweep slowly. Tap the camera view to focus; use the torch or zoom if needed.
4. **Orange** boxes show possible matches, including partial titles/authors and imperfect OCR. **Green** requires three strong readings from separate camera frames within five seconds, on the same tracked region. Repeated weak matches remain orange. “Check title” means the author is a clue but the specific wanted title has not been confirmed. A green box is still a prompt to verify the physical book.
5. A gentle tick announces a new detection; turn it off in **Options** if preferred. Use **Pause** to pause recognition. The camera preview remains live; leaving the app or opening the wanted list releases the camera.

Keep text reasonably large in the view. Glare, ornate lettering, tightly stacked characters and fast movement can prevent recognition. Try a few books at a time. There is no saved photo, scan history or summary screen. Outlines follow the detected text, not a guaranteed segmentation of the entire book.

Wanted entries can be edited, disabled and deleted. **Import/Export** uses the Android document picker and the versioned JSON format in [the example list](docs/wanted-example.json). Import previews the entry count and asks before replacing the existing list. The example is not loaded automatically.

## Upgrading from an earlier version

**Export your wanted list before replacing the installed APK.** GitHub builds currently use per-run debug keys, so Android may require uninstalling the old app before installing the new one. Reimport your exported JSON afterwards. Version 0.3 keeps the same list format. Builds from the same Android Studio installation normally share its local debug key.

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

## Version 0.3: PP-OCRv4 and tentative matching

- Bundled **PP-OCRv4 Mobile detector and recogniser**, running through source-built **NCNN on CPU**. ML Kit is removed. No model downloads or network access at runtime.
- Detects rotated text regions, expands their rectangles, straightens each into a horizontal 48-pixel-high strip, and recognises both reading directions. Orientation is selected by OCR confidence, independently of the wanted list. Rotated rectangles do not model every perspective distortion or curved spine.
- Alternates full-view detection and overlapping native-resolution detail tiles. Tentative tracks get priority for rereads. Detector input is capped at 1280 pixels on the long edge; recogniser crops come from the higher-resolution source.
- Limits work to 24 regions, checking a 1.8-second soft budget between regions. Starting regions rotate to avoid starving crowded shelves. This is not a measured latency guarantee; one native inference may take longer.
- Orange accepts substantial word fragments and fuzzy matches; common fragments and short fuzzy words are filtered. Author-only clues for a specific title cannot confirm it. Similarly scored competing entries cannot supply green evidence.
- Green needs three strong readings from distinct captured frames within five seconds, with OCR confidence at least 0.80. Multiple crops/callbacks from one frame count once; optical-flow updates never count. Confirmation persists while the physical region remains visually tracked.
- Independent optical-flow tracking follows translation, rotation and scale; brief loss gets one second of faded grace. Delayed OCR is mapped forward and texture-checked before display.
- The gentle tick remains once per new visual track, including orange candidates. Continuous tracking and promotion to green do not buzz again. **Options → Gentle vibration** disables it.
- Thermal throttling slows OCR. No Tensor/NPU acceleration or accuracy/speed advantage over ML Kit is claimed without real-shelf Pixel 10 testing.

Model checksums, sources and licences are bundled. This removes the ML Kit dependency, but **does not claim F-Droid acceptance**; reproducibility of the third-party model conversion still needs review.

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
