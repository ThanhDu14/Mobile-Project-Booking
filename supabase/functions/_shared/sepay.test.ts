import { assertEquals, assertMatch } from "jsr:@std/assert@1.0.19";
import {
  findCodeInContent,
  generatePaymentCode,
  hmacSha256Hex,
  PAYMENT_CODE_PREFIX,
  verifySepaySignature,
  vietQrUrl,
} from "./sepay.ts";

const SECRET = "test-sepay-secret";
const NOW = 1_763_000_000;
const CODE_REGEX = /^SNP[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/;

Deno.test("sepay: tiền tố mã thanh toán là SNP", () => {
  assertEquals(PAYMENT_CODE_PREFIX, "SNP");
});

Deno.test("sepay: generatePaymentCode khớp SNP + 8 ký tự dễ đọc", () => {
  for (let i = 0; i < 200; i++) assertMatch(generatePaymentCode(), CODE_REGEX);
  // Mọi byte đều ra ký tự hợp lệ (kể cả 0 và 255).
  assertMatch(generatePaymentCode((n) => new Uint8Array(n).fill(0)), CODE_REGEX);
  assertMatch(generatePaymentCode((n) => new Uint8Array(n).fill(255)), CODE_REGEX);
});

Deno.test("sepay: findCodeInContent dò được mã có tiền tố/hậu tố ngân hàng và chữ thường", () => {
  const code = "SNPABCD2345";
  assertEquals(findCodeInContent(`MBVCB.123.${code}.CT tu 0123`), code);
  assertEquals(findCodeInContent(`IBFT ${code} FT2611`), code);
  assertEquals(findCodeInContent(`chuyen tien ${code.toLowerCase()} san cau long`), code);
  assertEquals(findCodeInContent("chuyen tien an trua"), null);
  // Mã cũ của nạp ví không còn được nhận.
  assertEquals(findCodeInContent("SNTOPABCD2345"), null);
});

Deno.test("sepay: verifySepaySignature trả OK/MISSING/EXPIRED/INVALID", async () => {
  const raw = '{"id":1}';
  const sig = "sha256=" + await hmacSha256Hex(SECRET, `${NOW}.${raw}`);
  assertEquals(await verifySepaySignature(SECRET, raw, sig, String(NOW), NOW), "OK");
  assertEquals(await verifySepaySignature(SECRET, raw, sig, String(NOW), NOW + 300), "OK");
  assertEquals(await verifySepaySignature(SECRET, raw, sig, String(NOW), NOW + 301), "EXPIRED");
  assertEquals(await verifySepaySignature(SECRET, raw, sig, String(NOW), NOW - 301), "EXPIRED");
  assertEquals(await verifySepaySignature(SECRET, raw, null, String(NOW), NOW), "MISSING");
  assertEquals(await verifySepaySignature(SECRET, raw, sig, null, NOW), "MISSING");
  assertEquals(await verifySepaySignature(SECRET, raw + " ", sig, String(NOW), NOW), "INVALID");
  assertEquals(await verifySepaySignature("wrong", raw, sig, String(NOW), NOW), "INVALID");
});

Deno.test("sepay: vietQrUrl đúng tham số acc, bank, amount, des", () => {
  const url = new URL(vietQrUrl("970436", "0123456789", 53000, "SNPABCD2345"));
  assertEquals(url.origin + url.pathname, "https://qr.sepay.vn/img");
  assertEquals(url.searchParams.get("acc"), "0123456789");
  assertEquals(url.searchParams.get("bank"), "970436");
  assertEquals(url.searchParams.get("amount"), "53000");
  assertEquals(url.searchParams.get("des"), "SNPABCD2345");
});
