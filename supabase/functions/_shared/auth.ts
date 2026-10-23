// Xác thực Firebase ID token cho Edge Function (A viết, dùng chung cả nhóm; research R3,
// contracts/edge-function-on-signup.md §4).
//
// Function thường: `const user = await requireUser(req)`, cần vai trò thì thêm `requireRole(user, "ADMIN")`.
// Lỗi ném `HttpError`; bắt bằng `toErrorResponse(e)` để trả đúng dạng `{ error: { code, message } }`.
import { createRemoteJWKSet, decodeJwt, type JWTPayload, jwtVerify } from "npm:jose@6.2.12";
import * as firestore from "./firestore.ts";

export type AppRole = "PLAYER" | "OWNER" | "ADMIN";

export interface VerifiedToken {
  uid: string;
  appRole: AppRole | null;
  email: string | null;
  emailVerified: boolean;
  phone: string | null;
  name: string | null;
  signInProvider: string;
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly extra?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "HttpError";
  }
}

export function errorResponse(
  status: number,
  code: string,
  message: string,
  extra?: Record<string, unknown>,
): Response {
  return new Response(JSON.stringify({ error: { code, message, ...extra } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** `HttpError` thành response tương ứng; lỗi khác thành `500 INTERNAL` và ghi log. */
export function toErrorResponse(e: unknown): Response {
  if (e instanceof HttpError) return errorResponse(e.status, e.code, e.message, e.extra);
  console.error(e);
  return errorResponse(500, "INTERNAL", "Lỗi máy chủ, vui lòng thử lại");
}

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

type KeyResolver = Parameters<typeof jwtVerify>[1];

// createRemoteJWKSet tự cache khóa theo Cache-Control của Google.
let remoteJwks: KeyResolver | undefined;
let testJwks: KeyResolver | null = null;

/** Chỉ dùng trong test: thay JWKS của Google bằng bộ khóa giả. `null` để trả lại. */
export function __setJwksForTesting(jwks: KeyResolver | null): void {
  testJwks = jwks;
}

function keyResolver(): KeyResolver {
  if (testJwks) return testJwks;
  remoteJwks ??= createRemoteJWKSet(new URL(JWKS_URL));
  return remoteJwks;
}

function unauthenticated(): HttpError {
  return new HttpError(401, "UNAUTHENTICATED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
}

function bearerToken(req: Request): string {
  const header = req.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match) throw unauthenticated();
  return match[1];
}

async function verifiedPayload(token: string): Promise<JWTPayload> {
  const projectId = firestore.projectId();
  if (Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")) {
    // Token của Auth Emulator không có chữ ký. Chỉ bật khi test.
    console.warn("[auth] FIREBASE_AUTH_EMULATOR_HOST đang bật: bỏ qua kiểm tra chữ ký token");
    try {
      const payload = decodeJwt(token);
      if (payload.aud !== projectId) throw unauthenticated();
      return payload;
    } catch {
      throw unauthenticated();
    }
  }
  try {
    const { payload } = await jwtVerify(token, keyResolver(), {
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
      algorithms: ["RS256"],
    });
    return payload;
  } catch {
    throw unauthenticated();
  }
}

function toAppRole(value: unknown): AppRole | null {
  return value === "PLAYER" || value === "OWNER" || value === "ADMIN" ? value : null;
}

const str = (v: unknown): string | null => (typeof v === "string" && v.length > 0 ? v : null);

/** Xác minh token, không yêu cầu `appRole`. Chỉ `on-signup` dùng trực tiếp. */
export async function verifyIdToken(req: Request): Promise<VerifiedToken> {
  const p = await verifiedPayload(bearerToken(req));
  const uid = str(p.sub);
  if (!uid) throw unauthenticated();
  const firebase = (p.firebase ?? {}) as { sign_in_provider?: string };
  return {
    uid,
    appRole: toAppRole(p.appRole),
    email: str(p.email),
    emailVerified: p.email_verified === true,
    phone: str(p.phone_number),
    name: str(p.name),
    signInProvider: firebase.sign_in_provider ?? "unknown",
  };
}

/**
 * Dùng cho mọi function trừ `on-signup`: token hợp lệ, đã có `appRole` (403 NOT_INITIALIZED),
 * và `users/{uid}.accountStatus` không phải `LOCKED` (403 ACCOUNT_LOCKED).
 */
export async function requireUser(req: Request): Promise<VerifiedToken & { appRole: AppRole }> {
  const token = await verifyIdToken(req);
  if (!token.appRole) throw new HttpError(403, "NOT_INITIALIZED", "Tài khoản chưa hoàn tất đăng ký");
  const user = await firestore.get(`users/${token.uid}`);
  if (user?.accountStatus === "LOCKED") {
    throw new HttpError(403, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa");
  }
  return { ...token, appRole: token.appRole };
}

/** Ném `403 FORBIDDEN` khi vai trò không nằm trong `roles`. */
export function requireRole(user: VerifiedToken, ...roles: AppRole[]): void {
  if (!user.appRole || !roles.includes(user.appRole)) {
    throw new HttpError(403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");
  }
}
