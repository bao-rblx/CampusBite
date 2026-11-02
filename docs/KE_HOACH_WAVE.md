# CampusBite — Kế hoạch chia Wave (4 thành viên)

Nguyên tắc: **vertical slice** (mỗi người làm trọn View → ViewModel → Repository → Data cho tính năng của mình), nhưng chia thành các **wave nhỏ** để cả 4 người cùng push song song, mỗi wave kết thúc bằng 1 lần merge vào `develop` chạy được.

| Slice | Người | Tính năng |
|---|---|---|
| 1 | Lưu Gia Bảo (Leader) | Auth, Profile, Stall list, nền tảng chung |
| 2 | Nguyễn Trung Đức | Home, Menu, Dish Detail, Search/Filter, Favourites |
| 3 | Trần Quang Minh | Cart, Slot Picker, Checkout, OrderSyncWorker |
| 4 | Nguyễn Dũng | Order History/Detail, Reviews, QA |

---

## Wave 0 — Nền tảng (ĐÃ XONG, Bảo push lên repo)

Đã có trong skeleton (build `assembleDebug` thành công):
- Gradle (AGP 9.4, Kotlin 2.4.21, KSP), version catalog `gradle/libs.versions.toml`
- Room: `AppDatabase`, toàn bộ Entity (`Entities.kt`), DAO (`Daos.kt`), `Converters`
- Retrofit `ApiClient` (logging + retry), `CampusBiteApi` (`/stalls`, `/menus`), DTO + mapper
- `ServiceLocator` (DI thủ công), `MainActivity` + Bottom Navigation + `nav_graph.xml`, placeholder cho 5 tab
- Theme Material 3, strings tiếng Việt

Việc Bảo cần làm ngay để mọi người bắt đầu:
- [ ] Push skeleton lên `main`, tạo nhánh `develop`, bật branch protection (PR + review)
- [ ] Tạo Firebase project → bật Email/Password Auth + Firestore → gửi `google-services.json` cho nhóm (KHÔNG commit)
- [ ] Tạo MockAPI với 2 resource `stalls`, `menus` (giá là số nguyên VND, có `stallId`) → gửi URL
- [ ] Seed Firestore `slots` (vd 6 slot 11:30–13:00, capacity 20)

Mỗi thành viên: clone repo → copy `google-services.json` vào `app/` → thêm `MOCKAPI_BASE_URL=https://xxxx.mockapi.io/api/v1/` vào `local.properties` → Run.

**Quy tắc file chung** (tránh conflict): `Entities.kt`, `AppDatabase.kt`, `nav_graph.xml`, `ServiceLocator.kt` — sửa nhỏ, PR riêng, báo nhóm. Đổi schema Room ⇒ tăng `version` + migration (Bảo duyệt).

---

## Wave 1 — Data layer + Repository (song song, ~1 tuần)

Mục tiêu: mỗi slice có Repository chạy được, đọc từ Room, có unit test đơn giản. Chưa cần UI đẹp.

| Người | Nhánh | Việc |
|---|---|---|
| Bảo | `feature/01-auth` | `AuthRepository` (Firebase Auth sign up/in/out/delete), lưu session bằng EncryptedSharedPreferences, `StallRepository.refresh()` GET /stalls → `@Upsert` Room |
| Đức | `feature/02-menu` | `MenuRepository`: observe DishDao Flow, `refresh()` GET /menus → upsert **giữ isFavourite**, `toggleFavourite`, categories `SELECT DISTINCT`, VND formatter (vi-VN) |
| Minh | `feature/03-cart` | `CartRepository`: add (trùng thì tăng quantity), update qty, remove, total = Σ `unitPrice × quantity` (Long). `CheckoutRepository.placeOrder()` = 1 Room transaction (insert Order PENDING + clear cart) |
| Dũng | `feature/04-orders` | `OrderTrackingRepository`: observe orders theo userId, Firestore snapshot listener → upsert Room (last-write-wins theo `updatedAt`). `ReviewRepository`: CRUD vào Room (PENDING) |

Phụ thuộc: Minh/Dũng cần dish/stall trong Room ⇒ dùng tạm dữ liệu seed hoặc gọi `MenuRepository.refresh()` của Đức (merge Đức trước nếu cần).

✅ Done khi: build pass, test repository pass, PR merge vào `develop`.

---

## Wave 2 — UI + ViewModel (song song, ~1–1.5 tuần)

| Người | Màn hình |
|---|---|
| Bảo | Login, Register (validate inline), Profile (xem/sửa/xoá tài khoản, chọn stall yêu thích), StallList; auto-login khi mở lại app |
| Đức | Home (banner, Today's Picks, chip category), MenuList (Grid + DiffUtil + pull-to-refresh), DishDetail (Glide, giá VND, số lượng, nút **Thêm vào giỏ**) |
| Minh | CartFragment (nhóm theo stall, +/-, swipe xoá + Undo, tổng tiền), SlotPickerBottomSheet (hiển thị chỗ còn lại), Checkout |
| Dũng | OrderHistory (chip trạng thái), OrderDetail (timeline, Huỷ khi QUEUED, Đặt lại), ReviewList + ReviewFormDialog (1–5 sao) |

Quy tắc chung: ViewModel expose `StateFlow<UiState>`, Fragment collect bằng `viewLifecycleOwner.repeatOnLifecycle`, `_binding = null` trong `onDestroyView()`. Thay `PlaceholderFragment` của tab mình bằng Fragment thật.

✅ Done khi: tính năng của mình demo được độc lập trên emulator.

---

## Wave 3 — Ghép nối giữa các slice (~0.5 tuần)

Các điểm nối (người được ghi tên chịu trách nhiệm, người kia review):
- [ ] DishDetail → "Thêm vào giỏ" gọi `CartRepository` (Đức ↔ Minh)
- [ ] Badge số lượng giỏ hàng trên Bottom Nav (Minh)
- [ ] Checkout xong → điều hướng sang OrderDetail (Minh ↔ Dũng)
- [ ] "Đặt lại" → copy items vào giỏ (Dũng ↔ Minh)
- [ ] Review nhúng trong DishDetail (Dũng ↔ Đức)
- [ ] Chưa đăng nhập → chuyển về Login; logout xoá cart/session (Bảo)

✅ Done khi: chạy trọn luồng Login → Home → Dish → Cart → Checkout → Order History.

---

## Wave 4 — Offline-first, đồng bộ & real-time (~1 tuần)

| Người | Việc |
|---|---|
| Minh | `OrderSyncWorker` (WorkManager unique work, constraint có mạng, exponential backoff); Firestore transaction tạo `orders/{orderId}` + tăng `bookedCount`; slot đầy ⇒ order FAILED + thông báo rõ |
| Dũng | Sync review PENDING/xoá mềm (`isDeleted`) lên Firestore; Firestore Security Rules (chỉ đọc/ghi document của mình); listener gắn lifecycle |
| Đức | Search/filter/sort theo giá, rating; empty/error state; offline browse |
| Bảo | Profile cache offline; xử lý lỗi mạng chung (Snackbar); dark mode toggle |

✅ Done khi: tắt Wi-Fi → vẫn duyệt menu, thêm giỏ, checkout ("Đang chờ đồng bộ") → bật Wi-Fi → order lên Firestore.

---

## Wave 5 — QA, polish, demo (~1 tuần)

- [ ] Dũng (QA lead): LeakCanary, Profiler, checklist regression toàn bộ luồng
- [ ] Mỗi người: sửa bug slice mình, viết kịch bản demo (theo mục "Demo" trong proposal)
- [ ] Bảo: merge `develop` → `main`, build APK release, chuẩn bị slide

---

## Quy trình Git (mỗi wave)

```bash
git checkout develop && git pull
git checkout -b feature/02-menu-wave1     # nhánh theo slice + wave
# ... code ...
git commit -m "feat(menu): add MenuRepository with Room cache"
git push -u origin feature/02-menu-wave1
# Mở PR vào develop → Bảo review → merge
```

- Commit theo Conventional Commits: `feat:`, `fix:`, `chore:`, `test:`
- PR nhỏ (1 wave / slice), rebase `develop` thường xuyên
- Checklist PR: build không lỗi, không hardcode key, ViewBinding lifecycle đúng, migration Room đã test
