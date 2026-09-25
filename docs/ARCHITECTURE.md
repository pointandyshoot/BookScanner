# Architecture (0.3)

## Concurrent pipeline

`CameraX ImageAnalysis` uses `KEEP_ONLY_LATEST` at a requested 2560 × 1920, with a supported-size fallback and higher-resolution preference. It shares a viewport with `PreviewView` in `FIT_CENTER` mode. Sensor rotation and crop are applied to both analysis representations.

The analysis executor samples a maximum 640-pixel-long-edge grayscale tracking image directly from the Y plane, respecting row/pixel stride. It aims for a 65 ms interval (120 ms when warm). It closes each camera proxy after copying, without waiting for OCR. `LiveTracker` owns its native Mats exclusively on this thread.

When the OCR executor is idle, the analyser copies an upright full-detail image and gives ownership to that worker. Jobs alternate full-view detection and detail-region detection. Every pass can publish results immediately through a single replaceable pending batch. There is no unbounded image/job queue. Session generations reject callbacks after pause, navigation, list changes, recreation or shutdown. Bitmap ownership is released in `finally`, and the recogniser is closed after the OCR worker drains.

## Small and angled text

`ReadingImage` combines crop, optional enlargement, arbitrary rotation and translation into one Android matrix, with a white border. Its inverse maps OCR corner polygons back into the upright full-detail image. The overlay draws these polygons rather than an axis-aligned rectangle that loses text orientation.

`OcrReader` owns PP-OCRv4 Mobile through JNI. NCNN is built from pinned source. BGR detector input uses ImageNet normalisation and dimensions divisible by 32; recognition strips are 48 pixels high and normalised to [-1,1]. CTC decoding uses the exact 6,625-class dictionary. Loading/output shapes are checked; no ML Kit fallback is retained.

DB probability-map contours are scored, fitted to rotated rectangles and expanded by area × 1.5 / perimeter. Crops are rectified from the higher-resolution source. Both reading directions are checked; OCR confidence selects orientation independently of wanted entries. This handles arbitrary rotation, not all curved-spine/perspective distortion. Unrelated text regions are never stitched together.

Four overlapping 65%-size tiles retain native detail. Tentative tracks are eligible for rereads after 600 ms, confirmed tracks after 2.5 seconds. Full-view jobs continue between detail jobs. A job processes up to 24 regions and checks a 1.8-second soft budget between them. Individual inference cannot be interrupted. Starting regions rotate to avoid starving later text.

Orange indicates fuzzy/partial matching. A distinctive token can suggest a longer title/author, but remains weak. Short titles require exact whole words. Author-only clues for a specific title remain weak. Similarly scored competing entries also remain tentative.

Each visual track owns `Confirmation`: three strong observations from distinct frames within five seconds are needed for green. Strong means title similarity >= 0.88 or author-wildcard similarity >= 0.90, plus OCR confidence >= 0.80 and no close competitor. Crops/callbacks from one frame count once; weak readings and optical flow do not count. Green persists while the same texture remains tracked. Reacquisition starts confirmation anew.

## Tracking and late results

Each displayed track retains its wanted-entry identity, oriented polygon and a 96 × 32 reference patch. New frames provide global image corners plus corners within active tracks. Pyramidal LK flow runs forward and backward; inconsistent/error-prone points are rejected. RANSAC estimates a similarity transform for local book points and a global camera-motion fallback. Reference-patch correlation verifies the moved region to reduce drifting to a neighbouring book. Implausible scale/jumps and largely out-of-view regions are rejected. At most 12 simultaneous tracks are retained.

A track has no fixed lifetime while visual checks succeed. Failed checks allow up to one second of faded grace; later failure removes it. This is texture tracking, not semantic proof the object is a book. Blank or changed scenes must not keep a box alive. A heartbeat watchdog clears the overlay if analysis itself stalls for 600 ms; this is independent of OCR latency.

A bounded three-second / 60-step camera-motion history maps late OCR polygons forward. Results must have a continuous reliable transform path and match their capture-time reference texture in the current frame. Results too old, from a reset session, or from an untraceable movement are rejected. This avoids displaying stale coordinates while permitting OCR calls longer than the former 700 ms cutoff.

## Appearance signalling

`AppearanceGate` observes stable visual-track IDs. It emits on first appearance and after at least 1.5 seconds without visibility. The one-second tracking grace prevents a single bad frame from removing an entry. Overlapping rereads and aliases associate with the existing track. Distinct visual regions remain independent even when they match the same author wildcard. Recently lost tracks can recover their identity for 1.5 seconds to suppress a buzz from brief tracking failure; confirmed out-of-view tracks do not retain that identity. Identity is visual, not an ISBN or guaranteed whole-book identity. UI pulses coalesce within 500 ms and use Android's gentle `EFFECT_TICK`, respecting touch-vibration attributes. Options persist the user's choice.

## Lifecycle, privacy and limits

Pausing or opening the list invalidates the session and clears tracker state on its owning executor. An already-running OCR task is allowed to finish, but cannot publish stale results. All camera content, reference patches and motion history are transient memory. Only lists/options persist. The manifest removes dependency networking permissions and enables normal CAMERA/VIBRATE permissions; no network OCR or telemetry.

Tracking can still fail under fast movement, blur, glare, occlusion, identical neighbouring textures or strong perspective change. In those cases the app fades/removes a track and reacquires it by OCR. No claim is made of direct Pixel Tensor acceleration or measured shelf-reading accuracy. The native OpenCV runtime increases APK size.

## Wanted data

The version 1 JSON format is unchanged: `version: 1`, `books: []`, with each entry containing `id`, `title`, `author`, `aliases`, `enabled`. Blank titles mean any book by the supplied author. Imports remain bounded to 1 MB / 1,000 entries and ask before replacement. No user lists are included in source control.

## References

- [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR/tree/v2.7.0)
- [NCNN](https://github.com/Tencent/ncnn)
- [OpenCV Android setup](https://docs.opencv.org/4.x/d5/df8/tutorial_dev_with_OCV_on_Android.html)
- [OpenCV pyramidal optical flow](https://docs.opencv.org/4.x/javadoc/org/opencv/video/Video.html)
- [OpenCV robust affine estimation](https://docs.opencv.org/4.x/javadoc/org/opencv/calib3d/Calib3d.html)
- [CameraX output transforms](https://developer.android.com/media/camera/camerax/transform-output)
