# Third-party notices

## Sappelen / Libiry BookSpineScanner

- Source: https://github.com/Sappelen/BookSpineScanner
- Reviewed revision: `0e64485f12a390324d9d8cc4e3e024c6ba4a4590`
- Relevant source: [`src/main.js`](https://github.com/Sappelen/BookSpineScanner/blob/0e64485f12a390324d9d8cc4e3e024c6ba4a4590/src/main.js), functions `preprocessImage`, `detectSpines` and `extractSpineRegion`.
- Licence: [CC0 1.0 Universal](https://github.com/Sappelen/BookSpineScanner/blob/0e64485f12a390324d9d8cc4e3e024c6ba4a4590/LICENSE).

Credit for the mild histogram-stretch approach, narrow-spine proposals, padded crops and right-angle OCR preparation. BookScanner independently implements these concepts in Java. It uses percentile endpoints on grayscale luminance rather than RGB min/max stretching, a lightweight vertical-boundary proposal rather than OpenCV Canny/dilation/contours, and bounded live-frame scheduling rather than photograph processing. No upstream application source, Tesseract runtime, icons or other assets are copied or bundled. The official OpenCV Android runtime is independently added in version 0.2 as described below.

## PaddleOCR PP-OCRv4 Mobile (0.3)

Upstream: https://github.com/PaddlePaddle/PaddleOCR/tree/v2.7.0 — Apache Licence 2.0, included in `assets/licences/PaddleOCR.txt`.

Converted mobile detector and recogniser: https://github.com/FeiGeChuanShu/ncnn_ppstructure/tree/dbe10814b5cb6bbefd5f1460489db15f2489b8ee/models/ppocrv4 . Only these model assets are used; application code is independently implemented. The converter identifies them as PP-OCRv4 lite models. The conversion repository does not supply a separate licence or a complete reproducible conversion recipe. A conversion audit remains necessary before claiming F-Droid readiness.

Dictionary: PaddleOCR `v2.7.0`, `ppocr/utils/ppocr_keys_v1.txt`: 6,623 entries plus CTC blank and space. Sources and SHA-256 hashes are recorded in `assets/ppocrv4/provenance.json`; Gradle verifies them before building. All models are bundled for offline use.

## NCNN (0.3)

https://github.com/Tencent/ncnn — BSD 3-Clause, included in `assets/licences/ncnn.txt`. Built from the source submodule at `305837fd4a722ebc47c5d72e72d8ec9ae970e932` (20250503). CPU only, two threads, no Vulkan or FP16 arithmetic/storage. No downloaded native binaries. Existing NCNN notices remain in the source submodule.

Google ML Kit and its transitive runtime/model dependencies were removed in 0.3.

## AndroidX

CameraX, Activity and transitive AndroidX components: [Apache Licence 2.0](https://www.apache.org/licenses/LICENSE-2.0). Source and notices: https://android.googlesource.com/platform/frameworks/support/ . Their packaged notices and consumer rules remain with their dependencies.

## Gradle wrapper

Generated using Gradle 8.13. Copyright Gradle contributors; [Apache Licence 2.0](https://www.apache.org/licenses/LICENSE-2.0). Generated launchers retain the upstream copyright/licence headers. Gradle distribution: https://gradle.org/ .

## Test and build dependencies

JUnit 4.13.2: Eclipse Public Licence 1.0. Hamcrest: BSD 3-Clause. These are development dependencies and are not application code. Gradle, Android Gradle Plugin and GitHub Actions retain their own licences. Transitive dependencies retain their respective licences.

## OpenCV Android (added in 0.2)

Official Maven artefact `org.opencv:opencv:4.12.0`, including native libraries, is bundled for arm64-v8a and x86_64. Copyright OpenCV contributors; Apache Licence 2.0, with additional third-party notices in its distribution. Source/licence: https://github.com/opencv/opencv/tree/4.12.0 . Used for pyramidal optical flow, robust similarity estimation, image transforms and local tilt estimation. No BookSpineScanner application code is copied.
