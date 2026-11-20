// Test thuần cho `nameKeywords` (không cần Emulator): cd supabase/functions && deno test -A _shared/keywords.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { nameKeywords } from "./keywords.ts";

Deno.test("CLB Cầu Lông Q7 có đủ từ và tiền tố", () => {
  const k = nameKeywords("CLB Cầu Lông Q7");
  for (const w of ["cau", "long", "q7", "clb", "c", "ca", "cl", "l", "lo", "q"]) assert(k.includes(w), `thiếu ${w}`);
  assert(!k.includes("cầu"), "phải bỏ dấu");
});

Deno.test("Đà Nẵng: đ -> d, bỏ dấu", () => {
  const k = nameKeywords("Đà Nẵng");
  assertEquals(k.includes("da"), true);
  assertEquals(k.includes("nang"), true);
  assertEquals(k.includes("d"), true);
});

Deno.test("không trùng phần tử", () => {
  const k = nameKeywords("Cầu cầu CẦU, cau");
  assertEquals(k.length, new Set(k).size);
  assertEquals(k, ["c", "ca", "cau"]);
});

Deno.test("từ dài hơn 20 ký tự chỉ lấy tiền tố tới 20", () => {
  const k = nameKeywords("a".repeat(30));
  assertEquals(k.length, 20);
  assertEquals(Math.max(...k.map((s) => s.length)), 20);
});

Deno.test("chuỗi rỗng hoặc chỉ ký tự đặc biệt", () => {
  assertEquals(nameKeywords(""), []);
  assertEquals(nameKeywords(" -- !! "), []);
});
