# Third-party notices

## Sappelen / Libiry BookSpineScanner

- Source: https://github.com/Sappelen/BookSpineScanner
- Reviewed revision: `0e64485f12a390324d9d8cc4e3e024c6ba4a4590`
- Relevant source: [`src/main.js`](https://github.com/Sappelen/BookSpineScanner/blob/0e64485f12a390324d9d8cc4e3e024c6ba4a4590/src/main.js), functions `preprocessImage`, `detectSpines` and `extractSpineRegion`.
- Licence: [CC0 1.0 Universal](https://github.com/Sappelen/BookSpineScanner/blob/0e64485f12a390324d9d8cc4e3e024c6ba4a4590/LICENSE).

Credit for the mild histogram-stretch approach, narrow-spine proposals, padded crops and right-angle OCR preparation. BookScanner independently implements these concepts in Java. It uses percentile endpoints on grayscale luminance rather than RGB min/max stretching, a lightweight vertical-boundary proposal rather than OpenCV Canny/dilation/contours, and bounded live-frame scheduling rather than photograph processing. No upstream source, OpenCV runtime, Tesseract runtime, icons or other assets are copied or bundled.

## Google ML Kit

Bundled `com.google.mlkit:text-recognition:16.0.1` and its transitive runtime/model dependencies are governed by [Google ML Kit terms](https://developers.google.com/ml-kit/terms), not this repository's MIT licence. See [ML Kit recognition documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Recognition is on-device; the app removes networking permissions from the merged manifest.

## AndroidX

CameraX, Activity and transitive AndroidX components: [Apache Licence 2.0](https://www.apache.org/licenses/LICENSE-2.0). Source and notices: https://android.googlesource.com/platform/frameworks/support/ . Their packaged notices and consumer rules remain with their dependencies.

## Gradle wrapper

Generated using Gradle 8.13. Copyright Gradle contributors; [Apache Licence 2.0](https://www.apache.org/licenses/LICENSE-2.0). Generated launchers retain the upstream copyright/licence headers. Gradle distribution: https://gradle.org/ .

## Test and build dependencies

JUnit 4.13.2: Eclipse Public Licence 1.0. Hamcrest: BSD 3-Clause. These are development dependencies and are not application code. Gradle, Android Gradle Plugin and GitHub Actions retain their own licences. Transitive dependencies retain their respective licences.
