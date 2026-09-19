# Validation

## Automated

Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`.

Core unit tests cover author wildcards, short-title false positives, fuzzy title reads, punctuation and accents, aliases, series globs, disabled entries, author-only labelling, invalid entries, inverse right-angle coordinates and geometric repeat evidence (including same-frame duplicates, adjacent spines, expiry and session reset). Lint checks Android API use and manifest configuration. Building both variants exercises Java compilation, resource merging, dependency manifests and release shrinking.

These tests do not exercise ML Kit recognition on a camera, screen layout or end-to-end alignment. A successful build is not evidence of shelf-reading accuracy or battery life.

## Pixel 10 acceptance checklist — 0.2 not yet executed

1. Install the APK, enable aeroplane mode **before the first launch**, add a title and verify the bundled recogniser works without a download.
2. Add specific titles, an author wildcard, aliases and a series pattern. Edit, disable, delete and re-enable entries. Restart and check persistence.
3. Export the list; import it back. Reject a malformed file, an oversized file, duplicate IDs and a wildcard-only entry without an author. Cancel an import replacement and verify the current list is preserved.
4. Aim at known wanted and unwanted spines; include very short titles (“Eon”), the same author with a different title, and authors sharing a surname. Check author-only labels.
5. Try upright, 90°, 180° and 270° text, mixed orientations and horizontal stacks. Stay still for at least a few analysis frames to allow exploration.
6. Verify boxes align at the centre and all four viewfinder edges, in portrait/landscape, at different zoom settings, and after background/foreground transitions. Check letterboxing and navigation bar insets.
7. Hold a matched book in view for 20 seconds, pan, tilt and zoom: its box should follow without repeated buzzing. Then sweep away from a match: the box should follow the book while tracking remains confident, and disappear after it leaves or tracking fails for a second. A repeated read turns green; returning after a pause must not inherit old evidence. A reappearance after an absence should buzz again. Brief OCR misses should not. Check the vibration-off option.
8. Pause/resume, open the wanted list during recognition, rotate the phone, lock/unlock and deny/revoke camera permission. Verify recovery without frozen previews or old results. The privacy camera indicator should disappear when backgrounded or on the list screen.
9. Test focus, torch and zoom under dim lighting and glossy covers. Scan for 10 minutes; measure latency and battery/thermal behaviour rather than assuming the initial settings are optimal.
10. Compare identical slow sweeps with experimental spine crops off/on. Record wanted hits, false boxes, missed titles and latency. Keep the default off until cropping shows a net benefit. Test leaning books, shelves with vertical dividers and books with weak spine boundaries.

A useful first benchmark is 30–50 known books, including 10 wanted titles and 5 wanted-author entries, with repeated sweeps in consistent lighting. Record manually; the app intentionally does not save frames or session summaries.


## Native/emulator regression checks (0.2)

Run `./gradlew connectedDebugAndroidTest` on an API 35+ arm64/x86_64 device or emulator. The `device-tests` Actions job runs these on an API 35 x86_64 emulator. Tests create generic artificial images entirely in memory:

- Track translation, rotation and scaling for 3.5 seconds without any repeated OCR.
- Map a delayed detection to the current image after motion.
- Remove tracks on a blank scene and reject obsolete/session-reset results.
- Round-trip arbitrary-angle crop coordinates.
- Run the real bundled ML Kit recogniser on 42-pixel author text in a 1920 × 1440 scene at several non-right-angle rotations, explicitly deskewed by the production reader.

The OCR test checks reading and geometry with known angles; it does **not** prove automatic angle estimation or real-shelf accuracy. The CPU-only tests also cover alert suppression/re-arming and angle-search scheduling. On-device testing must separately verify local angle estimates on multiple leaning spines, haptic strength, thermal behaviour and sustained throughput.
