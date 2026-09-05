# BookScanner

An offline Android wanted-book finder for op shops. Sweep the camera across a shelf; potential matches get a box in the live view. Built with Google ML Kit Text Recognition and CameraX, with the Pixel 10 as the first testing target.

**Initial implementation — real-shelf accuracy and Pixel 10 performance still need device testing.** A box is a prompt to check a book yourself, not a confirmed identification.

## Using the app

1. Open **Wanted → Add**. Enter a title and optionally its author, or leave the title blank for **any book by that author**.
2. Add alternative titles in the aliases field. Title patterns support `*` (any text) and `?` (one character). For example, `Matilda*` matches visible title text starting with “Matilda”. The app cannot infer series membership when a series name is absent from the spine.
3. Return to **Scan**, allow the camera and sweep slowly. Tap the camera view to focus; use the torch or zoom if needed.
4. **Amber** boxes are potential matches. **Green** means the same entry was read again nearby in a later frame, not that its identity has been verified. “Check title” means only the author was recognised for a specific wanted book.
5. Use **Pause** to pause recognition. The camera preview remains live; leaving the app or opening the wanted list releases the camera.

Keep text reasonably large in the view. Glare, ornate lettering, tightly stacked characters and fast movement can prevent recognition. Try a few books at a time. There is no vibration, saved photo, scan history or summary screen.

Wanted entries can be edited, disabled and deleted. **Import/Export** uses the Android document picker and the versioned JSON format in [the example list](docs/wanted-example.json). Import previews the entry count and asks before replacing the existing list. The example is not loaded automatically.

## Install and build

Requires **Android 15 or later**; intended first for Pixel 10. Targets Android API 36 and runs on newer Android versions as well.

### Android Studio

1. Choose **Get from VCS** (or **File → New → Project from Version Control**).
2. Clone `https://github.com/pointandyshoot/BookScanner.git` and open its root folder.
3. Use **JDK 17** for Gradle and install **Android SDK Platform 36** and **Build Tools 36.0.0** when prompted. Let Gradle sync; the first build needs internet to download dependencies.
4. Enable USB debugging on the Pixel, connect it, select it in the device list and press **Run**.
5. To get subsequent changes, use **Git → Pull** before rebuilding.

Or with JDK 17 and the Android SDK configured:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows: use `gradlew.bat`. The Gradle 8.13 wrapper is included; Android Gradle Plugin is pinned to 8.13.2. Release builds are unsigned; debug builds are installable and intended for testing.

### GitHub APK

[Android build](https://github.com/pointandyshoot/BookScanner/actions/workflows/android.yml) tests, lints and builds the app on pushes and pull requests. Open a successful run and download **BookScanner-debug** under Artifacts, unzip it, then install `app-debug.apk` on the phone. GitHub sign-in is needed to download workflow artifacts. Android may ask you to allow installation from the app opening the APK. Each clean CI runner uses a new debug signing key; uninstall an older differently signed build before installing (export your wanted list first), or build consistently with Android Studio on your own computer.

## Scanning choices

- Bundled **ML Kit Latin Text Recognition 16.0.1**: no first-run model download or cloud OCR.
- **CameraX 1.5.3**, rear camera, analysis resolution preference 1280 × 960, latest-frame backpressure and one OCR job at a time. Preview remains independent of analysis.
- Correct camera sensor rotation first. Then probe **90°, 180°, 270°, 0°** across analysed frames. A productive orientation gets two frames out of three; the third keeps exploring all right angles. No arbitrary-angle brute force.
- Direct luminance-plane conversion avoids YUV → JPEG → bitmap conversion. After four unmatched frames, alternate contrast-enhanced reads with raw reads. Mild percentile contrast stretch preserves grayscale detail; no aggressive thresholding.
- Optional **experimental spine crops**: a lightweight vertical-edge heuristic runs before OCR and proposes narrow strips only when there are 2–4 plausible strips covering less than 60% of the image. Full-view checks run every third frame. **Off by default:** no shelf benchmark yet proves a net accuracy or speed benefit. It can miss leaning or horizontal books.
- Matching uses local lines and compact text blocks, normalises punctuation/diacritics and tolerates modest OCR errors in longer titles. Very short titles need an exact whole-word read. Fuzzy author-only evidence is explicitly labelled as such for specific books.
- Repeated-frame evidence uses entry identity and overlapping geometry within 900 ms. No cross-frame text stitching, image registration or stored frames. Boxes come from the current analysed image and expire within 750 ms of analysis start; results taking over 700 ms are discarded.
- Adaptive processing intervals respond to measured workload and Android thermal status. No claimed Tensor G5/NPU acceleration: the public ML Kit recogniser does not expose a delegate selection API. These are conservative starting settings, not measured Pixel 10 benchmarks.

## Privacy and storage

Only the wanted list and the spine-cropping option persist locally. Camera frames and recognition results remain in memory and are discarded. No internet permission, analytics, accounts, microphone, GPS, media-library or vibration permission. Network permissions from dependency manifests are explicitly removed. Automatic Android backup is disabled; export a wanted-list backup before uninstalling. The document provider you choose for manual export may itself be cloud-backed.

## Documentation

- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Device testing checklist](docs/TESTING.md)
- [Wanted-list example](docs/wanted-example.json)
- [Third-party credits and licences](THIRD_PARTY_NOTICES.md)

## Credits

Thanks to [Sappelen/BookSpineScanner](https://github.com/Sappelen/BookSpineScanner), licensed CC0 1.0, for the spine-first cropping and mild histogram-stretch preprocessing ideas. This app uses an independent Java implementation; its OpenCV/Tesseract/web code is not copied or bundled. See the notices for the reviewed source revision and implementation differences.

Original BookScanner code is MIT licensed. ML Kit, AndroidX and the Gradle wrapper retain their own terms and licences.
