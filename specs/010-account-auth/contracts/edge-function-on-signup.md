# Contract: Edge Function `on-signup` và code dùng chung `_shared/auth.ts`, `_shared/identity.ts`

**Vị trí mã:**

| Thành phần | File |
|---|---|
| Function | `supabase/functions/on-signup/index.ts` |
| Logic thuần (để test) | `supabase/functions/on-signup/handler.ts` |
| Xác thực token (A, dùng chung) | `supabase/functions/_shared/auth.ts` |
| Claim và trạng thái tài khoản Firebase Auth (A, dùng chung) | `supabase/functions/_shared/identity.ts` |
| Truy cập Firestore | `supabase/functions/_shared/firestore.ts` (B). Spec 010 chỉ cần `get` và `commit` có điều kiện `exists=false`. Nếu B chưa có thì A viết bản tối thiểu và báo B |

**Cấu hình:** trong `supabase/config.toml`, function này đặt `verify_jwt = false`, vì token ngay sau đăng ký chưa có claim `role` ([research R3](../research.md#r3-xác-thực-token-trong-edge-function)). Function tự xác minh token.

**Secrets:**
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SERVICE_ACCOUNT` (JSON, đã có cho `_shared/firestore.ts`)
- (chỉ khi test) `FIREBASE_AUTH_EMULATOR_HOST`, `FIRESTORE_EMULATOR_HOST`

**Gọi từ app:** `supabase.functions.invoke("on-signup", body)`, kèm `Authorization: Bearer <Firebase ID token>` lấy bằng `getIdToken(false)`. Phương thức `POST`, kiểu `application/json`, timeout phía app 15 giây.

---

## 1. Request

```json
{
  "displayName": "Lê Hoàng Nam",
  "termsAccepted": true
}
```

| Trường | Bắt buộc | Kiểm tra |
|---|---|---|
| `displayName` | Khi document chưa có | Sau khi `trim` dài 2–50 ký tự. Bỏ trống thì dùng `token.name`; vẫn không hợp lệ thì trả `400 INVALID_DISPLAY_NAME` |
| `termsAccepted` | Khi document chưa có | Phải là `true`. Nếu không thì trả `400 TERMS_REQUIRED` |

Không nhận `email`, `phone`, `role` hay bất kỳ trường 🔒 nào từ body. Các trường lạ bị bỏ qua.

## 2. Xử lý

```text
1. claims ← verifyIdToken(req)                      // 401 UNAUTHENTICATED nếu sai
2. user  ← firestore.get("users/{uid}")
3. nếu user tồn tại:
     nếu user.accountStatus == LOCKED → 403 ACCOUNT_LOCKED
   ngược lại:
     kiểm tra termsAccepted, displayName
     nếu token không có email lẫn phone_number → 400 MISSING_CONTACT
     commit create users/{uid} (precondition exists=false), dữ liệu theo data-model.md mục 2
       ALREADY_EXISTS → coi như đã có, đọc lại user
4. current ← identity.getCustomClaims(uid)          // accounts:lookup
   next    ← { ...current, role: "authenticated",
               appRole: current.appRole ?? user.role ?? "PLAYER" }
   nếu next ≠ current → identity.setCustomClaims(uid, next)   // accounts:update
5. 200 { status, appRole: next.appRole }
```

## 3. Response

**`200`**

```json
{ "status": "CREATED", "appRole": "PLAYER" }
```

`status`:
- `CREATED`: vừa tạo document.
- `ALREADY_INITIALIZED`: document đã có, claim đã đủ hoặc vừa được bổ sung.

Sau khi nhận `200`, app PHẢI gọi `getIdToken(true)`, rồi mới coi người dùng là đã đăng nhập đầy đủ (FR-006).

**Lỗi** (cùng dạng `{ "error": { "code", "message" } }` như các function khác):

| HTTP | `code` | Khi nào | App hiển thị |
|---|---|---|---|
| 400 | `TERMS_REQUIRED` | Document chưa có và `termsAccepted != true` | Quay lại bước đồng ý |
| 400 | `INVALID_DISPLAY_NAME` | Tên không hợp lệ | Lỗi dưới ô họ tên |
| 400 | `MISSING_CONTACT` | Token không có email lẫn số điện thoại | "Đã có lỗi, vui lòng thử lại" |
| 401 | `UNAUTHENTICATED` | Thiếu token, sai chữ ký, hết hạn, sai `aud` hoặc `iss` | Lấy lại token, thử lại 1 lần; vẫn lỗi thì đăng xuất |
| 403 | `ACCOUNT_LOCKED` | `accountStatus == LOCKED` | Đăng xuất, thông báo khóa |
| 405 | `METHOD_NOT_ALLOWED` | Không phải `POST` | — |
| 500 | `INTERNAL` | Lỗi Firestore hoặc Identity Toolkit | Màn hình chờ, nút "Thử lại" |

## 4. `_shared/auth.ts` (dùng chung cả nhóm)

```ts
export interface VerifiedToken {
  uid: string; appRole: "PLAYER" | "OWNER" | "ADMIN" | null;
  email: string | null; emailVerified: boolean; phone: string | null;
  name: string | null; signInProvider: string;
}
export async function verifyIdToken(req: Request): Promise<VerifiedToken>;          // chỉ on-signup dùng trực tiếp
export async function requireUser(req: Request): Promise<VerifiedToken & { appRole: AppRole }>;
// requireUser: verifyIdToken + appRole != null (nếu thiếu → 403 NOT_INITIALIZED)
//              + users/{uid}.accountStatus != LOCKED (nếu khóa → 403 ACCOUNT_LOCKED)
export function requireRole(user: VerifiedToken, ...roles: AppRole[]): void;        // 403 FORBIDDEN
export function errorResponse(status: number, code: string, message: string, extra?: object): Response;
```

- JWKS được cache theo header `Cache-Control` của Google. `jose.createRemoteJWKSet` tự làm việc này.
- Chế độ Emulator chỉ bật khi có `FIREBASE_AUTH_EMULATOR_HOST`. Ở chế độ này hàm decode token không kiểm chữ ký, và PHẢI ghi log cảnh báo.

## 5. `_shared/identity.ts` (dùng chung: A, D)

```ts
export async function getCustomClaims(uid: string): Promise<Record<string, unknown>>;
export async function setCustomClaims(uid: string, claims: Record<string, unknown>): Promise<void>; // ghi đè, nên gọi qua mergeClaims
export async function mergeClaims(uid: string, patch: Record<string, unknown>): Promise<void>;     // đọc, gộp, ghi
export async function setUserDisabled(uid: string, disabled: boolean): Promise<void>;
```

- Gọi Identity Toolkit REST (`accounts:lookup`, `accounts:update` với `customAttributes` / `disableUser`) bằng access token của service account. Scope là `https://www.googleapis.com/auth/identitytoolkit` và `https://www.googleapis.com/auth/cloud-platform`. Dùng chung hàm lấy access token với `_shared/firestore.ts`.
- Có `FIREBASE_AUTH_EMULATOR_HOST` thì gọi `http://<host>/identitytoolkit.googleapis.com/v1/projects/<id>/accounts:*` với header `Authorization: Bearer owner`.
- Claim JSON phải nhỏ hơn 1000 byte (giới hạn của Firebase).

**D dùng:**
- `review-owner-application`: `mergeClaims(uid, { appRole: "OWNER" })`, sau đó mới ghi `users.role`.
- Thu hồi quyền chủ sân: `mergeClaims(uid, { appRole: "PLAYER" })`.
- `set-account-status`: `setUserDisabled` cùng với ghi `accountStatus`.

## 6. Test (`deno test`, Firebase Emulator Auth + Firestore)

| # | Kịch bản | Kỳ vọng |
|---|---|---|
| S1 | Người dùng email mới, `termsAccepted: true` | `200 CREATED`; document đủ trường theo data-model; claim `{role, appRole: PLAYER}` |
| S2 | Gọi lại S1 lần nữa | `200 ALREADY_INITIALIZED`; document không đổi (`createdAt` giữ nguyên) |
| S3 | Hai lời gọi song song cho cùng `uid` | Cả hai `200`; đúng 1 document |
| S4 | Document đã có, claim chưa có (giả lập lỗi ở bước 4) | `200`; claim được bổ sung; không cần `termsAccepted` |
| S5 | Người dùng mới, `termsAccepted: false` | `400 TERMS_REQUIRED`; không có document, không có claim |
| S6 | `displayName` 1 ký tự hoặc 51 ký tự | `400 INVALID_DISPLAY_NAME` |
| S7 | Đã có claim `appRole: OWNER` hoặc `ADMIN` | Giữ nguyên `appRole`, chỉ thêm `role` |
| S8 | Token giả, hết hạn, sai `aud` (chế độ thật, khóa giả) | `401` |
| S9 | `accountStatus = LOCKED` | `403 ACCOUNT_LOCKED`; claim không đổi |
| S10 | Body có `role: "ADMIN"`, `walletBalance: 1e9` | Bị bỏ qua; document có `PLAYER`, `0` |
| S11 | Đăng nhập số điện thoại (token có `phone_number`, không có email) | `email = null`, `phone = "+84…"` |
| S12 | Body có email khác với email trong token | Document dùng email của token, đã `lowercase` |
