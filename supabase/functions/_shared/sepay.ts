// SePay Test mode (sandbox) cho thanh toán chuyển khoản trực tiếp cho chủ sân (spec 050). Không bao giờ dùng Live.

/** Tiền tố mã thanh toán, phải trùng "Cấu trúc mã thanh toán" trên my.sepay.vn. */
export const PAYMENT_CODE_PREFIX = "SNP";
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // bỏ 0/O, 1/I để dễ đọc
const CODE_SUFFIX_LENGTH = 8;

/** Lệch giờ tối đa giữa `X-SePay-Timestamp` và giờ server (chống replay, theo mẫu của SePay). */
export const MAX_CLOCK_SKEW_SECONDS = 300;

export function generatePaymentCode(random: (n: number) => Uint8Array = randomBytes): string {
  const bytes = random(CODE_SUFFIX_LENGTH);
  let suffix = "";
  for (const b of bytes) suffix += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return PAYMENT_CODE_PREFIX + suffix;
}

function randomBytes(n: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(n));
}

function toHex(buffer: ArrayBuffer): string {
  return [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return toHex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message)));
}

function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export type SignatureCheck = "OK" | "MISSING" | "EXPIRED" | "INVALID";

/**
 * Kiểm chữ ký HMAC-SHA256 của SePay: `X-SePay-Signature = "sha256=" + hex(HMAC(secret, "{timestamp}.{rawBody}"))`.
 * Phải dùng body thô, không `JSON.stringify` lại.
 */
export async function verifySepaySignature(
  secret: string,
  rawBody: string,
  signatureHeader: string | null,
  timestampHeader: string | null,
  nowSeconds: number,
): Promise<SignatureCheck> {
  if (!signatureHeader || !timestampHeader) return "MISSING";
  const timestamp = Number(timestampHeader);
  if (!Number.isInteger(timestamp)) return "INVALID";
  if (Math.abs(nowSeconds - timestamp) > MAX_CLOCK_SKEW_SECONDS) return "EXPIRED";
  const expected = "sha256=" + await hmacSha256Hex(secret, `${timestampHeader}.${rawBody}`);
  return constantTimeEquals(expected, signatureHeader.trim().toLowerCase()) ? "OK" : "INVALID";
}

/** Các trường của payload webhook SePay mà thanh toán cần. */
export interface SepayTransaction {
  id: number;
  transferType: "in" | "out";
  transferAmount: number;
  code: string | null;
  content: string;
  referenceCode: string;
  transactionDate: string;
}

/** `null` khi payload thiếu trường bắt buộc. Mã lấy từ `code`, không có thì dò trong `content`. */
export function parseSepayTransaction(body: unknown): SepayTransaction | null {
  if (!body || typeof body !== "object") return null;
  const b = body as Record<string, unknown>;
  const id = Number(b.id);
  const amount = Number(b.transferAmount);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  if (!Number.isSafeInteger(amount) || amount <= 0) return null;
  const transferType = b.transferType === "out" ? "out" : b.transferType === "in" ? "in" : null;
  if (!transferType) return null;
  const content = typeof b.content === "string" ? b.content : "";
  return {
    id,
    transferType,
    transferAmount: amount,
    code: normalizeCode(typeof b.code === "string" ? b.code : null) ?? findCodeInContent(content),
    content,
    referenceCode: typeof b.referenceCode === "string" ? b.referenceCode : "",
    transactionDate: typeof b.transactionDate === "string" ? b.transactionDate : "",
  };
}

const CODE_PATTERN = new RegExp(`${PAYMENT_CODE_PREFIX}[${CODE_ALPHABET}]{${CODE_SUFFIX_LENGTH}}`);

function normalizeCode(code: string | null): string | null {
  if (!code) return null;
  const match = code.toUpperCase().replace(/\s+/g, "").match(CODE_PATTERN);
  return match ? match[0] : null;
}

/** Ngân hàng có thể chèn tiền tố/hậu tố vào nội dung chuyển khoản, nên dò mã ở bất kỳ vị trí nào. */
export function findCodeInContent(content: string): string | null {
  return normalizeCode(content);
}

/**
 * Ảnh VietQR do SePay sinh, trỏ tới `venues.payoutAccount` của chủ sân. Trong sandbox chỉ để hiển thị,
 * tiền đi bằng "Mô phỏng giao dịch". `bankBin` là mã BIN NAPAS 6 số.
 */
export function vietQrUrl(bankBin: string, accountNumber: string, amount: number, code: string): string {
  const params = new URLSearchParams({ acc: accountNumber, bank: bankBin, amount: String(amount), des: code });
  return `https://qr.sepay.vn/img?${params}`;
}
