# CampusBite

Native Android campus food ordering app built from the capstone proposal and the existing Kotlin starter. Android 8.0 (API 26) or later is required.

## Run the app

Open this folder in Android Studio, sync Gradle, and run `app`. Choose **Explore the demo** on the welcome screen. No credentials or network connection are needed for the demo.

The installable debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. This is a development build, not a Play Store release.

```powershell
.\gradlew.bat :app:assembleDebug
```

Use Android Studio's bundled JDK as the Gradle JDK. The wrapper pins Gradle 9.6.0; the project uses Android SDK 37 and AGP 9.4.0. Set your own SDK path in `local.properties`; do not copy someone else's machine path.

## What works

- Welcome, registration, login and password reset screens; Firebase handles real email/password accounts when configured. Demo access is explicitly separate.
- Three sample stalls and twelve Vietnamese dishes, category filters, search by dish or stall, price/rating sorting, budget filter and favourites.
- Dish details, ingredients/allergens, quantity, optional stall notes, reviews with create/edit/delete, and pre-order actions.
- Room-backed cart, stall grouping, quantity controls, swipe removal with Undo, exact integer VND totals and a live navigation badge.
- Pickup windows with remaining places. Checkout creates one order per stall and clears the cart in a single Room transaction. A failure preserves the whole cart.
- History, status filters, order item/price snapshots, queued-order cancellation, reorder using current menu prices, and removal of synced cancelled orders.
- Profile editing, favourite stall, notification preference, account deletion and persistent dark mode. The notification preference is stored; push notifications are not part of this implementation.
- Explicit demo-only controls to advance orders through Confirmed, Preparing, Ready and Completed. Demo orders never contact a stall or charge money.
- Configurable Retrofit catalogue and Firebase Auth/Firestore integration, Room caching, WorkManager retry, transactional slot reservation and live order/review listeners.

The interface is English with Vietnamese dish names and VND prices. Bundled vector food illustrations work offline; live menu image URLs are rendered and disk-cached with Glide.

## Connect real services

1. Create a Firebase project and register Android package `com.campusbite`. Enable Email/Password Authentication and create a Firestore database.
2. Download that project's `google-services.json` to `app/google-services.json`. It is ignored by Git. The Google Services plugin only activates when the file is present.
3. Create two MockAPI resources, `stalls` and `menus`. Example records are in `backend/sample-data/`. Use the actual stall IDs assigned by your API in each menu's `stallId`. Keep prices as integer VND.
4. Add `MOCKAPI_BASE_URL=https://YOUR_PROJECT.mockapi.io/api/v1/` to `local.properties`. The URL must end with `/`.
5. Install the Firebase CLI, sign into your own project, and deploy the supplied rules: `firebase deploy --only firestore:rules --project YOUR_PROJECT_ID`.
6. In `backend/functions`, run `npm install`. Configure Google Application Default Credentials for your own Firebase project, then run `node seed-slots.cjs YOUR_PROJECT_ID YYYY-MM-DD STALL_ID [STALL_ID ...]`. Slots are based on Vietnam time. Re-running this script preserves existing reservations. Seed future dates as needed.
7. Deploy the account-deletion cleanup function: `firebase deploy --only functions --project YOUR_PROJECT_ID`. Firebase Cloud Functions deployment may require a billing-enabled project. Deploy this before offering real account deletion.
8. Rebuild the app, create an account, refresh the catalogue and test checkout. Change an order's `status` in the Firebase console to `CONFIRMED`, `PREPARING`, `READY` or `COMPLETED` to demonstrate vendor updates.

The project does not include a separate vendor application; the proposal's Firebase-console workflow supplies vendor status changes. Payment is at pickup; there is no online payment processor.

### Remote document contracts

`slots/{id}`: `stallId` (string), `label` (string), `startsAt` (epoch milliseconds), `capacity` (integer), `bookedCount` (integer). The client transaction also writes `lastOrderId` to let Security Rules verify the reservation against its order.

`orders/{client UUID}`: `userId`, `stallId`, `slotId`, `pickupWindow`, `itemsJson`, `totalAmount` (integer VND), `status`, `createdAt`, `updatedAt` (epoch milliseconds).

`reviews/{userId}-{dishId}`: `userId`, `dishId`, `authorName`, `rating` (1–5), `comment`, `isDeleted`, `createdAt`, `updatedAt`. Deletion uses a tombstone so connected devices can observe it.

Stall and dish IDs must not contain `/`. Keep the `demo-` prefix reserved for the built-in demo. Demo records and live records are isolated in the UI and live checkout rejects demo slots.

### Offline and synchronization behavior

Room is the source for screen data. Browsing, favourites, cart changes and cached profile editing work without a connection. Live checkout requires previously cached future pickup slots. The local order shows **Waiting to sync** until WorkManager reserves capacity on the server. Never treat a pending order as a confirmed pickup.

The worker uses one client-generated UUID per order, Firestore transactions and exponential backoff. Repeated submission cannot reserve the same order twice. A full or expired slot marks the order unavailable; cancel and reorder to choose another time. Only queued orders can be cancelled. If the stall confirms an order before cancellation reaches the server, the server's status wins. Pending work remains associated with its owning account; sign into that account to resume syncing.

Security Rules enforce ownership, review validation, capacity bounds, atomic booking/cancellation and prohibit students from setting vendor statuses. They do **not** validate the catalogue price inside JSON item snapshots against MockAPI. This capstone uses pay-at-pickup; a commercial deployment needs trusted server-side pricing/stock validation and an appropriate vendor backend before accepting financial commitments.

## Architecture

`ui/` contains lifecycle-aware Fragments, ViewBinding, RecyclerView/DiffUtil and an activity-scoped `CampusViewModel` with StateFlow. `data/CampusRepository.kt` owns catalogue, cart, checkout and reviews. `AuthRepository.kt` owns Firebase sessions and cached profiles. `CloudSync.kt` owns listeners and the WorkManager job. `data/local/` contains Room entities and DAOs; `data/remote/` contains Retrofit contracts. The version 1→2 Room migration preserves the original six tables and adds pickup slots.

## Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The second command needs a running emulator or test phone. It runs database, migration/persistence and UI journey tests; use a dedicated test device because the journey resets the demo account's cart, orders and reviews.

```powershell
cd backend
npm ci
npm test
```

The backend tests use the local Firestore emulator and a `demo-` project ID. Java must be on PATH. They do not deploy rules or write to a real Firebase project.

See `docs/VERIFICATION.md` for the actual checks performed on the delivered build and remaining live-service verification.
