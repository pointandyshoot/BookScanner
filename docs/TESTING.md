# Version 0.3 verification

Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`, with NCNN submodules initialised. GitHub Actions additionally runs `connectedDebugAndroidTest` on an Android 35 x86_64 emulator.

Unit coverage includes partial/fuzzy matching, short-title false positives, author-only clues, three-frame confirmation, duplicate callbacks, weak evidence and expiry. Device tests exercise the bundled PP-OCRv4 on generated angled text without manually undoing rotation, and optical-flow tracking through motion, delayed OCR, loss and reacquisition.

## Pixel 10 trial

- Fresh install in aeroplane mode: models must work without downloads.
- Partial title or surname: orange; repeated weak reads must stay orange.
- Complete title/full author wildcard: initially orange, green after three distinct strong frames.
- Closely competing wanted entries must remain tentative.
- Slow shelf sweep, pause/resume, rotation and wanted-list edits: no stale boxes.
- Track a book through angle/scale changes and briefly lose it; boxes should follow and fade away on genuine loss. Haptics should not repeat continuously.
- Compare the same shelf to 0.2 for distance, glare, small letters, false positives, time to green and heat. No real-shelf accuracy or Pixel 10 latency claim is made yet.
- Export the wanted list before uninstalling or changing signing keys.
