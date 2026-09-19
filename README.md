# BookScanner

An offline Android wanted-book finder for op shops. Sweep the camera across a shelf; potential matches get a box in the live view. Built with Google ML Kit Text Recognition, CameraX and OpenCV, with the Pixel 10 as the first testing target.

**Version 0.2 — real-shelf accuracy and Pixel 10 performance still need device testing.** A box is a prompt to check a book yourself, not a confirmed identification.

## Using the app

1. Open **Wanted → Add**. Enter a title and optionally its author, or leave the title blank for **any book by that author**.
2. Add alternative titles in the aliases field. Title patterns support `*` (any text) and `?` (one character). For example, `Matilda*` matches visible title text starting with “Matilda”. The app cannot infer series membership when a series name is absent from the spine.
3. Return to **Scan**, allow the camera and sweep slowly. Tap the camera view to focus; use the torch or zoom if needed.
4. **Amber** boxes are potential matches. **Green** means the same entry was read again nearby in a later frame, not that its identity has been verified. “Check title” means only the author was recognised for a specific wanted book.
5. A gentle tick announces a new detection; turn it off in **Options** if preferred. Use **Pause** to pause recognition. The camera preview remains live; leaving the app or opening the wanted list releases the camera.

Keep text reasonably large in the view. Glare, ornate lettering, tightly stacked characters and fast movement can prevent recognition. Try a few books at a time. There is no saved photo, scan history or summary screen. Outlines follow the detected text, not a guaranteed segmentation of the entire book.

Wanted entries can be edited, disabled and deleted. **Import/Export** uses the Android document picker and the versioned JSON format in [the example list](docs/wanted-example.json). Import previews the entry count and asks before replacing the existing list. The example is not loaded automatically.

## Upgrading from 0.1

**Export your wanted list before replacing the installed APK.** GitHub builds currently use per-run debug keys, so Android may require uninstalling the old app before installing the new one. Reimport your exported JSON afterwards. Version 0.2 keeps the same list format. Builds from the same Android Studio installation normally share its local debug key.

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

## Version 0.2: detail reads, tilted text and live tracking

- Analysis now requests **2560 × 1920**, using a supported nearby camera size when unavailable. The preview and analysis still share a viewport.
- Full-view OCR is followed by a **native-resolution overlapping detail crop**, enlarged for recognition. Four overlapping tiles are revisited with different orientations; existing tracks also receive targeted rereads. This retains more small-letter detail than the original 1280 × 960 path. It cannot recover detail that the camera never resolved.
- Common right angles remain in the search. OpenCV estimates **local text/spine tilt** and OCR reads a deskewed region at that arbitrary angle. When an angle estimate is unavailable, it explores ±15°, ±30° and ±45° offsets. Perspective distortion, curved spines and highly stylised lettering remain difficult.
- Camera tracking and OCR run on **separate executors**. Only one OCR job is active; new camera frames continue moving the boxes while recognition is busy.
- **Pyramidal Lucas–Kanade optical flow**, forward/backward checks, robust similarity transforms and a reference-texture check follow each detected region through translation, rotation and scale changes. A visually verified track can stay highlighted without repeated successful OCR. Tracks get up to one second of faded grace if the visual evidence briefly fails, then disappear. Boxes immediately outside the view are removed.
- Delayed OCR rectangles are mapped through a bounded three-second motion history and checked against the current image before display. The old rule discarding every OCR result over 700 ms is removed.
- A gentle **single haptic tick** signals a newly detected book region. Continuous tracking and repeated OCR do not retrigger it. After a region has been absent from the displayed tracks for 1.5 seconds, it can alert again. Simultaneous matches coalesce into a tick. Separate tracked books can alert independently even when they match the same author wildcard. **Options → Gentle vibration** disables it.
- Thermal throttling slows OCR without making recognition block tracking. No direct Tensor/NPU delegate is claimed.
- Experimental spine proposals remain optional. Full-view and overlapping-tile reads are the default.

This is a candidate for real-shelf testing. Generated-image emulator tests exercise tracking and recognition mechanics, not Pixel 10 accuracy, latency or battery life on actual books.

## Privacy and storage

Only the wanted list, scanner options and camera-permission prompt state persist locally. Camera frames, OCR crops, reference patches and a short motion-transform history remain in memory and are discarded. No internet permission, analytics, accounts, microphone, GPS or media-library permission. Camera and vibration permissions are used. Network permissions from dependency manifests are explicitly removed. Automatic Android backup is disabled; export a wanted-list backup before uninstalling. The document provider you choose for manual export may itself be cloud-backed.

## Documentation

- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Device testing checklist](docs/TESTING.md)
- [Wanted-list example](docs/wanted-example.json)
- [Third-party credits and licences](THIRD_PARTY_NOTICES.md)

## Credits

Thanks to [Sappelen/BookSpineScanner](https://github.com/Sappelen/BookSpineScanner), licensed CC0 1.0, for the spine-first cropping and mild histogram-stretch preprocessing ideas. This app uses an independent Java implementation; its application code is not copied. Version 0.2 bundles the official OpenCV Android library for independent tracking and deskew implementations. See the notices for the reviewed source revision and implementation differences.

Original BookScanner code is MIT licensed. ML Kit, AndroidX, OpenCV and the Gradle wrapper retain their own terms and licences.
