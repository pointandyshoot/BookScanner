# Version 0.4 verification

CI runs unit tests, lint, debug/release builds and Android emulator tests.

Regression coverage includes four/five-letter surname clues, OCR errors, common-word rejection, short-title false positives, nearby line pairing, first-frame weak highlights, duplicate callbacks, optical-flow movement/loss and bundled OCR on angled and split-name synthetic scenes.

The earlier three-frame confirmation tests are removed because confirmation no longer controls highlighting. Synthetic OCR tests do not establish real-shelf recall or Pixel 10 latency.

## Phone trial

- Export your wanted list before replacing differently signed APKs.
- A surname or partial title should highlight immediately after one useful OCR result.
- Boxes remain orange, including full-name readings; check books yourself.
- Sweep shelves slowly, including stacked names, rotated text and smaller lettering.
- Check that existing highlights do not prevent new books being found.
- Check tracking, no repeated vibration while a box remains visible, pause/resume and list edits.
- Assess useful hints, nuisance boxes, time to first hint and heat against 0.3.
