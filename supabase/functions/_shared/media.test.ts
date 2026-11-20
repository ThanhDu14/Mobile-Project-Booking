// Test thuần (không cần Emulator) cho phần ảnh dùng chung `_shared/media.ts` (T005).
import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { HttpError } from "./auth.ts";
import { chatMediaPath, MAX_IMAGE_BYTES, parseChatMediaPath, validateImage } from "./media.ts";

Deno.test("validateImage: chỉ jpeg/png/webp và 1 byte đến 2 MB", () => {
  assertEquals(validateImage("image/jpeg", 1), "jpg");
  assertEquals(validateImage("image/png", MAX_IMAGE_BYTES), "png");
  assertEquals(validateImage("image/webp", 100), "webp");
  for (const [ct, size] of [["image/gif", 100], ["image/jpeg", MAX_IMAGE_BYTES + 1], ["image/jpeg", 0], [null, 5]]) {
    const e = assertThrows(() => validateImage(ct, size), HttpError);
    assertEquals(e.code, "INVALID_ARGUMENT");
  }
});

Deno.test("chatMediaPath và parseChatMediaPath khớp nhau; path sai dạng thì null", () => {
  assertEquals(chatMediaPath("a_b", "m1", "webp"), "a_b/m1.webp");
  assertEquals(parseChatMediaPath("a_b/m1.webp"), { chatId: "a_b", messageId: "m1" });
  for (const p of ["a_b/m1.gif", "a_b/c/m1.jpg", "m1.jpg", "../m1.jpg", "a_b/.jpg"]) assertEquals(parseChatMediaPath(p), null, p);
});
