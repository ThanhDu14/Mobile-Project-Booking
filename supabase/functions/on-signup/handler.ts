// Logic của on-signup (contracts/edge-function-on-signup.md §1–§3). Nhận firestore, identity qua
// tham số để test thay được.
import { type AppRole, errorResponse, HttpError, toErrorResponse, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError } from "../_shared/firestore.ts";
import * as defaultIdentity from "../_shared/identity.ts";

export interface OnSignupDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
  identity: Pick<typeof defaultIdentity, "getCustomClaims" | "setCustomClaims">;
}

const NAME_LENGTH = { min: 2, max: 50 };
const MAX_CREATE_ATTEMPTS = 3;

function lockedError(): HttpError {
  return new HttpError(403, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa");
}

async function readBody(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    return body && typeof body === "object" && !Array.isArray(body) ? body : {};
  } catch {
    return {};
  }
}

/** Họ tên sau `trim`; trống thì lấy tên trong token. Sai độ dài thì `null`. */
function resolveDisplayName(input: unknown, tokenName: string | null): string | null {
  const typed = typeof input === "string" ? input.trim() : "";
  const name = typed || (tokenName ?? "").trim();
  const length = [...name].length;
  return length >= NAME_LENGTH.min && length <= NAME_LENGTH.max ? name : null;
}

function asAppRole(value: unknown): AppRole | undefined {
  return value === "PLAYER" || value === "OWNER" || value === "ADMIN" ? value : undefined;
}

export async function handleOnSignup(
  req: Request,
  { firestore, identity }: OnSignupDeps = { firestore: defaultFirestore, identity: defaultIdentity },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const token = await verifyIdToken(req);
    const path = `users/${token.uid}`;
    let user = await firestore.get(path);
    let status: "CREATED" | "ALREADY_INITIALIZED" = "ALREADY_INITIALIZED";

    if (!user) {
      // Chỉ đọc hai trường này từ body; email, phone, role… luôn lấy từ token hoặc giá trị mặc định.
      const body = await readBody(req);
      if (body.termsAccepted !== true) {
        throw new HttpError(400, "TERMS_REQUIRED", "Cần đồng ý điều khoản sử dụng và chính sách quyền riêng tư");
      }
      const displayName = resolveDisplayName(body.displayName, token.name);
      if (!displayName) throw new HttpError(400, "INVALID_DISPLAY_NAME", "Họ tên cần từ 2 đến 50 ký tự");
      if (!token.email && !token.phone) {
        throw new HttpError(400, "MISSING_CONTACT", "Tài khoản không có email hoặc số điện thoại");
      }
      const data = {
        displayName,
        email: token.email?.toLowerCase() ?? null,
        phone: token.phone,
        avatarPath: null,
        level: null,
        favoriteDistrictIds: [],
        role: "PLAYER",
        ownerStatus: "NONE",
        accountStatus: "ACTIVE",
        fcmTokens: [],
        notificationPrefs: {},
        walletBalance: 0,
      };
      // Tạo document trước, gán claim sau: claim chỉ có khi document đã chắc chắn tồn tại.
      for (let attempt = 1; !user; attempt++) {
        try {
          await firestore.commit([createWrite(path, data, ["termsAcceptedAt", "createdAt", "updatedAt"])]);
          status = "CREATED";
          user = data;
        } catch (e) {
          // ALREADY_EXISTS: lời gọi song song khác vừa tạo xong. ABORTED: tranh chấp ghi, đọc lại rồi thử lại.
          const retriable = e instanceof FirestoreError && (e.code === "ALREADY_EXISTS" || e.code === "ABORTED");
          if (!retriable || attempt >= MAX_CREATE_ATTEMPTS) throw e;
          user = await firestore.get(path);
        }
      }
    }
    if (user.accountStatus === "LOCKED") throw lockedError();

    const current = await identity.getCustomClaims(token.uid);
    // Không bao giờ hạ appRole: claim đã có (OWNER/ADMIN) được giữ nguyên.
    const appRole = asAppRole(current.appRole) ?? asAppRole(user.role) ?? "PLAYER";
    if (current.role !== "authenticated" || current.appRole !== appRole) {
      await identity.setCustomClaims(token.uid, { ...current, role: "authenticated", appRole });
    }
    return Response.json({ status, appRole });
  } catch (e) {
    return toErrorResponse(e);
  }
}
