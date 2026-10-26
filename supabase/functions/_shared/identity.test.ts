// T022: chạy trên Auth Emulator (quickstart §2).
import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { getCustomClaims, mergeClaims, setCustomClaims, setUserDisabled } from "./identity.ts";
import { clearEmulators, signInEmail, signUpEmail } from "./testing.ts";

Deno.test("getCustomClaims: người dùng mới chưa có claim → {}", async () => {
  await clearEmulators();
  const { uid } = await signUpEmail("moi@example.com");
  assertEquals(await getCustomClaims(uid), {});
});

Deno.test("setCustomClaims rồi getCustomClaims đọc lại đúng", async () => {
  await clearEmulators();
  const { uid } = await signUpEmail("claims@example.com");
  await setCustomClaims(uid, { role: "authenticated", appRole: "PLAYER" });
  assertEquals(await getCustomClaims(uid), { role: "authenticated", appRole: "PLAYER" });
});

Deno.test("mergeClaims giữ nguyên role đã có", async () => {
  await clearEmulators();
  const { uid } = await signUpEmail("merge@example.com");
  await setCustomClaims(uid, { role: "authenticated", appRole: "PLAYER" });
  await mergeClaims(uid, { appRole: "OWNER" });
  assertEquals(await getCustomClaims(uid), { role: "authenticated", appRole: "OWNER" });
});

Deno.test("claim JSON từ 1000 byte trở lên bị từ chối, claim cũ giữ nguyên", async () => {
  await clearEmulators();
  const { uid } = await signUpEmail("big@example.com");
  await setCustomClaims(uid, { appRole: "PLAYER" });
  await assertRejects(() => setCustomClaims(uid, { appRole: "PLAYER", pad: "x".repeat(1000) }), RangeError);
  assertEquals(await getCustomClaims(uid), { appRole: "PLAYER" });
});

Deno.test("setUserDisabled: true thì đăng nhập trả USER_DISABLED, false thì đăng nhập lại được", async () => {
  await clearEmulators();
  const { uid } = await signUpEmail("lock@example.com");
  await setUserDisabled(uid, true);
  const locked = await signInEmail("lock@example.com");
  assertEquals(locked.status, 400);
  assertEquals((await locked.json()).error.message, "USER_DISABLED");

  await setUserDisabled(uid, false);
  const ok = await signInEmail("lock@example.com");
  assertEquals(ok.status, 200);
  await ok.body?.cancel();
});
