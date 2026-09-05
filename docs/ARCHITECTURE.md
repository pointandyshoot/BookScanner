# Architecture

## Data flow

`PreviewView` and `ImageAnalysis` share a CameraX `UseCaseGroup` viewport. The preview uses `FIT_CENTER`. `ImagePrep` reads the analysis crop's Y plane respecting row/pixel strides, converts it to grayscale ARGB and corrects sensor rotation. A right-angle OCR pass returns rectangles, which `Geometry.unrotate` maps back into the upright crop. `MatchOverlay` applies the same centre-fit scale and letterbox offset as the preview. Device testing must verify this alignment in portrait and landscape.

The single analysis executor holds one `ImageProxy` until its ML Kit task completes and closes it in `finally`. CameraX drops queued frames with `KEEP_ONLY_LATEST`. The analyser never waits on the main/UI thread. A session generation prevents results from old sessions or old wanted lists appearing after navigation, pause or backgrounding. Shutdown queues recogniser disposal after outstanding analysis before shutting down the executor.

## Modules

| Class | Responsibility |
|---|---|
| `MainActivity` | Scanner/list screens, permission handling, camera lifecycle, focus/zoom/torch, document picker |
| `WantedStore` | Version 1 JSON, input limits, duplicate-ID validation, local persistence |
| `ScanEngine` | Scheduling, OCR, region grouping, match dispatch, stale-result rejection |
| `ImagePrep` | Luminance conversion, right-angle bitmap rotation, contrast retry, experimental spine proposals |
| `Matcher` | Title, alias, author and bounded glob matching; no Android dependency |
| `Geometry` | Inverse rotation and rectangle overlap; no Android dependency |
| `Evidence` | Short-lived geometric repeat evidence, separate from identification certainty |
| `MatchOverlay` | Current-frame rectangles and labels, automatic expiry |

## Decisions and limits

One primary orientation per analysed frame limits latency and avoids spending four OCR calls on every frame. The sequence starts with 90° then 180°, followed by 270° and upright 0°. This is **additional to camera sensor rotation**, not a substitute for it. Two out of three subsequent frames can reuse a productive orientation, while exploration continues. A fast sweep can still leave a book unread before its useful orientation is tried; move slowly.

Contrast retries begin only after four unmatched frames. An unmatched frame can contain readable but unwanted books; the heuristic does not claim to measure OCR confidence. Grayscale can lose colour-based contrast and enhancement can hurt some covers, so raw reads continue. No perspective rectification or arbitrary-angle deskew is implemented in this version.

Spine detection is a proposal heuristic based on persistent vertical luminance edges, not a detector trained on books. It avoids a large OpenCV or neural segmentation dependency. Its default is off because there is not yet evidence that it improves this live-sweep workflow. When enabled, budget/shape gates and periodic full-frame reads reduce (but cannot remove) the risk of misses. Cropping each of several spines may still cost more than one full-view OCR call.

Title/author evidence is matched in individual text lines and conservatively sized blocks. OCR blocks can still span neighbouring books; false positives are expected. No independent title/author observations are joined across frames. This prevents accidental assembly of a title from one spine and an author from another. Author-only hits on specific-title entries remain labelled “check title”.

Repeated-frame evidence updates colour only. It neither delays the first potential match nor promotes it to a confirmed book. Geometry overlap is a conservative association without optical flow; camera movement can reset repeat evidence. There is no attempt to make old boxes follow moving books between OCR frames.

Camera analysis is configured for 1280 × 960, with CameraX permitted to choose a supported nearby resolution. Sampling interval is approximately 140–550 ms according to recent OCR time, increased to 700 ms at Android `THERMAL_STATUS_SEVERE` or above. These values require measurement on Pixel 10. Native ML Kit runtime scheduling is left to Google; no undocumented Pixel APIs are used.

## Wanted-list format

A root object has `version: 1` and `books: []`. Each book has `id`, `title`, `author`, `aliases` (array) and `enabled`. Missing IDs receive UUIDs. Blank titles become `*`; a wildcard-only entry needs an author. Author matching is literal/fuzzy; title and alias fields support globs. IDs must be unique. Imports are all-or-nothing, maximum 1 MB and 1,000 entries; titles/aliases are capped at 240 characters, authors at 160, and aliases at 20 per entry. Import asks before replacement. Files are read/written off the UI thread through Android's Storage Access Framework.

Matching cost grows with wanted-list size and observed text length. Large lists may reduce throughput; the adaptive interval protects responsiveness but does not guarantee a frame rate. Glob matching uses dynamic programming rather than user-supplied regular expressions.

## Reference APIs

- [ML Kit Android text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze)
- [CameraX transformations](https://developer.android.com/media/camera/camerax/transform-output)
- [Android thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
