# Architecture (0.2)

## Concurrent pipeline

`CameraX ImageAnalysis` uses `KEEP_ONLY_LATEST` at a requested 2560 × 1920, with a supported-size fallback and higher-resolution preference. It shares a viewport with `PreviewView` in `FIT_CENTER` mode. Sensor rotation and crop are applied to both analysis representations.

The analysis executor samples a maximum 640-pixel-long-edge grayscale tracking image directly from the Y plane, respecting row/pixel stride. It aims for a 65 ms interval (120 ms when warm). It closes each camera proxy after copying, without waiting for OCR. `LiveTracker` owns its native Mats exclusively on this thread.

When the OCR executor is idle, the analyser copies an upright full-detail image and gives ownership to that worker. One job performs a full-view read plus one detail-region read. Every pass can publish results immediately through a single replaceable pending batch. There is no unbounded image/job queue. Session generations reject callbacks after pause, navigation, list changes, recreation or shutdown. Bitmap ownership is released in `finally`, and the recogniser is closed after the OCR worker drains.

## Small and angled text

`ReadingImage` combines crop, optional enlargement, arbitrary rotation and translation into one Android matrix, with a white border. Its inverse maps OCR corner polygons back into the upright full-detail image. The overlay draws these polygons rather than an axis-aligned rectangle that loses text orientation.

`ReadingSchedule` preserves right-angle discovery (90°, 180°, 270°, 0°) and exploration even after a success. Each job also uses a local tilt estimate from Canny/Hough line segments, modulo 90°. Estimates are continuous angles. A detail region can have a different tilt from the full shelf. With no reliable estimate, fixed ±15°, ±30°, ±45° offsets supplement the coarse angles. This is a bounded search, not exhaustive perspective rectification.

Four 65%-size overlapping tiles retain native image detail; their schedule is offset from the orientation schedule so a tile is not locked to one quadrant. A previously detected region can receive a targeted reread after 2.5 seconds. Optional spine strips replace some detail tiles; they never disable full-view discovery. OCR retains the bundled ML Kit Latin recogniser and the existing local fuzzy title/author/alias matcher. No cloud model or book database is queried. Enlargement does not create missing source detail.

## Tracking and late results

Each displayed track retains its wanted-entry identity, oriented polygon and a 96 × 32 reference patch. New frames provide global image corners plus corners within active tracks. Pyramidal LK flow runs forward and backward; inconsistent/error-prone points are rejected. RANSAC estimates a similarity transform for local book points and a global camera-motion fallback. Reference-patch correlation verifies the moved region to reduce drifting to a neighbouring book. Implausible scale/jumps and largely out-of-view regions are rejected. At most 12 simultaneous tracks are retained.

A track has no fixed lifetime while visual checks succeed. Failed checks allow up to one second of faded grace; later failure removes it. This is texture tracking, not semantic proof the object is a book. Blank or changed scenes must not keep a box alive. A heartbeat watchdog clears the overlay if analysis itself stalls for 600 ms; this is independent of OCR latency.

A bounded three-second / 60-step camera-motion history maps late OCR polygons forward. Results must have a continuous reliable transform path and match their capture-time reference texture in the current frame. Results too old, from a reset session, or from an untraceable movement are rejected. This avoids displaying stale coordinates while permitting OCR calls longer than the former 700 ms cutoff.

## Appearance signalling

`AppearanceGate` observes currently displayed wanted-entry IDs. It emits on first appearance and after at least 1.5 seconds without visibility. The one-second tracking grace prevents a single bad frame from removing an entry. Rereads, aliases and multiple regions matching one author wildcard share the same entry identity. Thus a wildcard alerts once while any corresponding track remains visible, rather than once per physical copy. Different wanted entries remain independent. UI pulses coalesce within 500 ms and use Android's gentle `EFFECT_TICK`, respecting touch-vibration attributes. Options persist the user's choice.

## Lifecycle, privacy and limits

Pausing or opening the list invalidates the session and clears tracker state on its owning executor. An already-running OCR task is allowed to finish, but cannot publish stale results. All camera content, reference patches and motion history are transient memory. Only lists/options persist. The manifest removes dependency networking permissions and enables normal CAMERA/VIBRATE permissions; no network OCR or telemetry.

Tracking can still fail under fast movement, blur, glare, occlusion, identical neighbouring textures or strong perspective change. In those cases the app fades/removes a track and reacquires it by OCR. No claim is made of direct Pixel Tensor acceleration or measured shelf-reading accuracy. The native OpenCV runtime increases APK size.

## Wanted data

The version 1 JSON format is unchanged: `version: 1`, `books: []`, with each entry containing `id`, `title`, `author`, `aliases`, `enabled`. Blank titles mean any book by the supplied author. Imports remain bounded to 1 MB / 1,000 entries and ask before replacement. No user lists are included in source control.

## References

- [ML Kit input image guidelines](https://developers.google.com/ml-kit/vision/text-recognition/v2/android#input-image-guidelines): recognition depends on the number of pixels per character, focus and image quality.
- [OpenCV Android setup](https://docs.opencv.org/4.x/d5/df8/tutorial_dev_with_OCV_on_Android.html)
- [OpenCV pyramidal optical flow](https://docs.opencv.org/4.x/javadoc/org/opencv/video/Video.html)
- [OpenCV robust affine estimation](https://docs.opencv.org/4.x/javadoc/org/opencv/calib3d/Calib3d.html)
- [CameraX output transforms](https://developer.android.com/media/camera/camerax/transform-output)
