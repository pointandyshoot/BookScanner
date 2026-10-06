# Version 0.4.3 verification

CI runs unit tests, lint, debug/release builds and Android emulator tests.

Regression coverage includes shelf-band coverage and orientation scheduling, vertical synthetic names on both shelves with source-coordinate checks and fast-detector size assertions, delayed OCR handoff and full OCR-to-tracker output, as well as four/five-letter surname clues, OCR errors, common-word rejection, short-title false positives, nearby line pairing, first-frame weak highlights, duplicate callbacks, optical-flow movement/loss and bundled OCR on angled and split-name synthetic scenes.

The earlier three-frame confirmation tests are removed because confirmation no longer controls highlighting. Synthetic OCR tests do not establish real-shelf recall or Pixel 10 latency.

## Private video replay

Host replay uses the bundled model weights, detector thresholds, CTC decoding, matching thresholds, crop scheduling and bounded recognition loop. A grayscale shelf sweep reproduced no target hints with the prior tile schedule. Rotating the same frames before detection yielded readable author fragments. Revised coverage yielded candidate hints that survived optical-flow replay and current-patch correlation in a host approximation of the tracker. Doubling host inference elapsed time explores delayed-result behaviour; it is not a Pixel performance measurement. Some late results still fail texture checks or arrive after their book leaves the frame.

User media and private replay outputs are excluded from the repository and CI. Public regression scenes are generated, not copied from user media. The host approximation does not test CameraX image planes, the actual Java/JNI handoff, or UI rendering; emulator tests cover the Java/native recognition and coordinate path on generated scenes.

## Latency investigation

On-device screenshots reported detection at 1.8–2.0 seconds and total passes at 2.7–3.4 seconds, confirming detection as a major bottleneck. Private sampled-frame tests compared 640, 768, 960 and 1280 detector caps; 640 lost a target that 768 retained. The chosen fast cap was about 2–2.5 times faster for detection on those host frames. Recognition still uses original-resolution crops. Fast-region prioritisation was evaluated in the moving-video replay, including delayed result attachment. Timing-scaled host runs are scenario tests, not device benchmarks or direct ARM FP16 validation.

The x86 emulator verifies the float32 fallback and the Java/JNI diagnostics path. ARM half-precision kernels are included in the phone build and guarded at runtime, but their Pixel accuracy and latency require a field trial.

## Phone trial

- Export your wanted list before replacing differently signed APKs.
- Enable diagnostics and compare fast-pass total/detection time with 0.4.2. Check detector dimensions and whether CPU FP16 is enabled; occasional detail passes are slower.
- A surname or partial title should highlight immediately after one useful OCR result.
- Boxes remain orange, including full-name readings; check books yourself.
- Sweep shelves slowly, including stacked names, rotated text and smaller lettering.
- Check that existing highlights do not prevent new books being found.
- Check tracking, no repeated vibration while a box remains visible, pause/resume and list edits.
- Assess useful hints, nuisance boxes, time to first hint and heat against 0.3.

## Shelf photos (0.5.0)

Automated checks cover section coverage and bounded source crops; generated still scenes verify both text orientations, original-photo hint coordinates, monotonic progress, cancellation and EXIF-aware bounded JPEG decoding. No private media is in the tests.

On Pixel: take a portrait and landscape shot; start a three-photo burst while panning gently; verify focus, clarity and spacing; import an existing shelf photo; check both meters, progressive hints and the eight-photo limit. Open Review while the queue runs, navigate, pinch/pan, double-tap reset and tap a discovery. Rotate during OCR and during capture. Stop/resume, Clear during inference/capture/import, retry a failed read, leave and return to live scanning. Verify old callbacks cannot restore cleared photos or stale hints, and that the live scanner releases the camera before capture mode binds it. Test denied permission and returning from background. Measure actual Pixel latency; host timing is not a phone benchmark.

## Photo highlights (0.5.1)

Verify Batch hints across several photos, including a first photo without matches. Review should prefer a matching photo. Tap a batch discovery to open its specific photo and focus its orange rectangle. Check outlines on light/dark spines, after zoom, and for small/split text. Automated rendered-pixel checks cover source-to-review scaling and focus; an activity test covers later-photo discovery selection.
