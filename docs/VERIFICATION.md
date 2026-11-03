# CampusBite verification

Delivery date: 2 November 2026 (Asia/Ho_Chi_Minh).

## Build and environment

- Native Kotlin Android application, version 0.1.0, package `com.campusbite`.
- Debug APK built with Android Studio's bundled JDK, Gradle 9.6.0, AGP 9.4.0 and SDK 37.
- Device checks use the Small_Phone Android 17 / API 37 emulator at 720 × 1280. Android 8.0 / API 26 is the declared minimum; other versions and physical devices have not been tested.
- The delivered APK is a development build with local demo data. It is not a signed Play Store release.

## Automated checks

| Check | Coverage |
| --- | --- |
| 8 JVM unit tests | Integer VND calculations, valid quantities/prices and overflow rejection |
| 8 device repository tests | Concurrent cart updates, multi-stall checkout, transactional rollback, full slots, capacity release, price snapshots/reorder, favourite preservation and review edits/deletes |
| 2 device persistence tests | Version 1 to 2 Room migration and cart/favourite survival after reopening the database |
| 1 device UI journey | Demo entry, search, dish details, cart, pickup selection, order status progression, reorder, cancellation, history removal, review creation and dark mode |
| 7 local Firestore emulator tests | Atomic reservation, ownership isolation, cancellation, vendor-status restrictions, capacity bounds, review validation and unauthenticated access denial |

All 26 tests listed above passed. The final Android run completed successfully with 8 JVM tests and 11 instrumented device tests, zero failures and zero errors. The 7 Firestore emulator tests passed earlier in this implementation session; Android UI changes did not modify the rules afterward.

Android lint completed with **0 errors and 35 warnings**. Remaining warnings include hardcoded interface text, dependency updates, deprecated APIs and vector/resource guidance. This build has not undergone a full accessibility or localization audit.

Delivered APK SHA-256: `DC384D9A237E1548E592CFA270CC237BB3A4841F43FDA2DE03220C7B6F668878`.

## Visual checks

Screenshots from the small-screen emulator were inspected for home, dish details, cart, pickup selection, order status, saved review and dark profile. Pickup choices now wrap so the complete date, time range and remaining capacity are visible. Bottom navigation returns from details to its selected tab, and transient messages sit above the navigation controls.

## Reproduce

From the project folder:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Use a dedicated emulator or test device: the UI journey resets the demo account's cart, orders and reviews. Gradle may uninstall the tested application after instrumentation completes; install the delivered APK again to explore it manually.

For security rules, with Java and Node.js on PATH:

```powershell
cd backend
npm ci
npm test
```

The rules tests use the local Firestore emulator on port 18080 and the `demo-campusbite` project. No deployment or writes to a real Firebase project are involved.

## Live-service verification still required

No Firebase project configuration or real MockAPI URL was supplied. Consequently, the following have not been verified against live services:

- Firebase email registration, login, password reset and account deletion.
- Actual MockAPI catalogue responses and remote image loading.
- Android-to-Firestore reconnection/retry, cross-device order/review listeners and simultaneous reservations by separate devices.
- Deployment and execution of the account-deletion cleanup function.

Follow the README to configure those services, seed future pickup slots, deploy the rules and cleanup function, then test with two real accounts and network loss/reconnection.

Payments are at pickup. No payment processor or push notification delivery is implemented; the notification preference is stored only. Vendor status updates use the Firebase console. Rules validate ownership and capacity, but commercial use also requires trusted server-side validation of menu prices and stock.
