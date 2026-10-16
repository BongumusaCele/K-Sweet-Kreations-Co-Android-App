# K Sweet Kreations Co — Android

Native Kotlin / Jetpack Compose customer and bakery app, using the existing website API and Azure database. It uses the website's plum/lilac palette, serif headings and cake photographs. The app does not contain a WebView or an independent customer/product database.

Source repository: https://github.com/BongumusaCele/K-Sweet-Kreations-Co-Android-App

## Run

Open this directory in Android Studio, let Gradle sync, then run `app` on an Android device or emulator (Android 7 / API 24 or newer). Internet access is required. The default API is:

`https://k-sweet-creations-co.azurewebsites.net/api/`

Use the same email and password as the website. Customer registration creates a real account in the shared backend. Admin and Staff accounts open the bakery workspace automatically; permissions are also enforced by the server. No Azure keys, SQL credentials or administrator passwords are embedded in the app.

Build on Windows with the Android Studio JDK and installed SDK:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

The installable development build is `app/build/outputs/apk/debug/app-debug.apk`. A copy is provided in `artifacts/K-Sweet-Kreations-Co-debug.apk`. Allow installation from your chosen file manager/browser when installing this APK on a phone. This is a debug-signed test build. For Play Store distribution, create and retain your own upload key in Android Studio's **Generate Signed App Bundle / APK** flow; this project deliberately has no production signing key or password.

## Features

| Area | Native functionality |
| --- | --- |
| Storefront | Home, category/search/price sort, product details, gallery enlargement, About, FAQs, contact enquiries, published reviews |
| Accounts | Registration, sign-in, encrypted session restoration, profile name/phone editing, password visibility, sign-out and session expiry |
| Shopping | Cart quantities/removal, current prices and availability, collection checkout, server-calculated total, duplicate-submission protection |
| Custom cakes | Size/flavour/filling, design notes, optional inspiration photograph, quotation/expiry, accept or decline |
| Orders | Collection approval, status/history, total/deposit/balance, cancellation requests, payment records |
| EFT | PDF/JPG/PNG/WebP upload up to 5 MB, payment reference, pending/rejected/verified states, private proof viewer, PDF pages, save through Android's document picker |
| Team | Overview, order/search/status filters, slot approval, sequential progress, cake requests and quotations, collections, enquiries and feedback |
| Admin | Add/edit/disable/delete products with photo upload, verify/reject EFT, cancellation approval, block/unblock dates, review moderation, customer directory and staff account creation |
| Reliability | Foreground/resume/navigation refresh and 30-second polling, retry errors, retained drafts on failed saves, real backend validation and role enforcement |

Photographs are selected using Android's system document picker (JPG/PNG/WebP, 2 MB). File extensions, byte signatures and size are validated before submission and again by the API. Product/inspiration files use the API's existing Azure Storage integration. EFT proofs remain private and are fetched using the signed-in session; cookies are never passed to another app. Saving a document is an explicit user action.

Collection rules match the website: standard orders from tomorrow, custom cakes at least seven days ahead, half-hour weekday/Saturday slots, closed Sundays and blocked dates. All business dates use Africa/Johannesburg. Monetary rules use decimal arithmetic. The service remains authoritative for prices, permissions, availability and order transitions.

## Structure

- `AppViewModel.kt`: session, shared records, navigation, cart and mutations.
- `data/`: typed API models, HTTP/multipart client and Android Keystore session storage.
- `domain/BakeryRules.kt`: collection, deposit, contact and order-progression rules.
- `ui/`: native customer/team screens, forms, navigation and private image/PDF viewer.
- `ui/theme/`: bakery colours and typography, including dark mode.

The session is AES-GCM encrypted with a non-exportable Android Keystore key. Session preferences are excluded from backup/device transfer. Sign-out clears private records, cart and form state. Private PDF temporary files are removed when their viewer closes. Release traffic requires HTTPS; debug HTTP is limited to emulator/loopback hosts. There are no broad file-storage permissions.

## Tests

Unit tests cover decimal deposits, payment/order progression, collection hours/lead times/blocked dates, contacts and attachment signatures/limits.

Android integration tests intentionally refuse to mutate the Azure service. Start the sibling API's disposable SQLite test server in a separate terminal:

```powershell
# From ../K-Sweet-Kreations-Co-Web-API
node tests/start-test-api.mjs
```

Start an Android emulator and then run from this app directory:

```powershell
./gradlew.bat connectedDebugAndroidTest testDebugUnitTest '-PapiBaseUrl=http://10.0.2.2:5089/api/' '-Pandroid.testInstrumentationRunnerArguments.notClass=com.example.ksweetkreationscoapp.ProductionConnectionTest'
```

These tests exercise Android HTTP cookies/session restoration, Customer/Admin/Staff permissions, new-account visibility before purchases, profile sync, image upload, checkout and replay protection, PDF proof privacy, rejection/replacement/verification, order progression, quotations and deposits, cancellation, enquiries, moderation, blocked dates, product edits/deletion and preserved order history. Native UI tests cover navigation, registration, password visibility, validation, admin forms, PDF rendering and retained drafts after server errors. Only disposable test accounts/data are created.

After a local test build, **rebuild without `-PapiBaseUrl`** before distributing an APK. The debug override is never applied to release builds. `assembleRelease` produces an unsigned release APK until a release signing configuration is supplied.

The separate `ProductionConnectionTest` runs only with the default Azure build. It checks HTTPS health, public catalog parsing, public images and cross-origin URL rejection. It performs no sign-in or mutations:

```powershell
./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.ksweetkreationscoapp.ProductionConnectionTest'
```

The app has the same service scope as the website: EFT and collection workflows. Email password recovery, online card payments and push notifications are not provided by the existing backend.
