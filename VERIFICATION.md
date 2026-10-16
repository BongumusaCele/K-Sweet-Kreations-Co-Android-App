# Android verification — 16 October 2026

- Debug and unsigned release APK builds: passed.
- Android lint, debug and release: zero errors (16 advisory warnings).
- Unit tests: 5 passed (collection/payment/order/contact rules and attachment validation).
- Android 15 emulator local suite: 3 tests passed, zero failures. The API test covers the complete Customer/Staff/Admin workflow against a disposable SQLite database. Native UI tests cover registration, navigation, password visibility, forms, private PDF rendering and error recovery.
- Android-to-Azure HTTPS smoke check: 1 passed, read-only public health/catalog/images and cross-origin media rejection.
- Final Azure-connected MainActivity: launched successfully; no AndroidRuntime crash output.
- APK signature: verified with APK Signature Scheme v2.
- Minimum Android: API 24 (Android 7); target API 37.

The delivered APK uses https://k-sweet-creations-co.azurewebsites.net/api/ and the existing website accounts. Tests did not create or modify live Azure business records. No additional Azure resources were provisioned.

APK: artifacts/K-Sweet-Kreations-Co-debug.apk
Size: 13386683 bytes
SHA-256: 6BCE24763B7966973E899CAABFE0FCC0B35B681D08F611CAD1665EDFE1B50A3F

This APK is debug-signed for installation and testing. The release APK builds unsigned; production distribution requires the owner's signing key. No physical phone was attached, so runtime verification used an Android 15 emulator. XML reports and screenshots are retained under artifacts/verification and artifacts/screenshots.
