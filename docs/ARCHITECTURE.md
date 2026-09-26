# Architecture (0.4)

## Discovery and recognition

CameraX supplies a requested 2560 × 1920 analysis frame with a supported-size fallback. Preview and analysis share a viewport. Analysis samples a grayscale tracking image up to 640 pixels on its long edge; the full-detail upright luminance image is copied only when the separate OCR executor is idle. One OCR job runs at a time, and session generations reject obsolete results.

The PP-OCRv4 Mobile detector and recogniser run offline using pinned NCNN source and verified bundled weights. NCNN uses two CPU threads and packed SIMD layouts; FP16 and Vulkan remain disabled. Detector input is capped at 1280 pixels on its long edge. Its probability-map threshold is 0.20 and contour confidence cut-off is 0.40. Rotated rectangles are expanded and rectified from the source into 48-pixel-high recognition strips. This handles rotation, not all perspective distortion or curved spines.

Three detail passes alternate with one full-view pass. Detail discovery cycles through nine overlapping 45%-size tiles. A tracked region may receive a reread once per twelve jobs, if its last reading is at least four seconds old. Existing hints therefore do not monopolise the detail lane.

Each job visits up to 24 regions, rotating the start index. A 1.1-second soft budget is checked between regions; an individual inference cannot be interrupted. Recognition tries the opposite reading direction unless the first reading has confidence at least 0.93. Direction is chosen by OCR confidence independently of wanted text. Nonblank readings with confidence at least 0.35 enter matching.

## Immediate hints

One orange box means a possible match for the user to inspect. There is no multi-frame confirmation state. Exact distinctive four-letter words, fuzzy five-letter-or-longer fragments and whole-name/title similarity can create hints. Common publishing words are excluded from fragment matching and short titles require exact whole words. Author clues for a specific title are labelled “check title”. Internal strong evidence only prefers a more complete label; it never controls visibility or colour.

Nearby similarly oriented lines can be paired within one OCR pass. Geometric checks limit centre displacement, angle and height mismatch. Paired evidence stays anchored to the current text region rather than inventing a whole-book boundary. It is a heuristic and may join neighbouring spines; the user has chosen higher recall with more false positives.

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
