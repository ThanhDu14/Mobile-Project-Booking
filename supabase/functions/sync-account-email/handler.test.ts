// T092: E1–E3 của contracts/edge-functions-account.md §5, chạy trên Auth + Firestore Emulator.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken } from "../_shared/testing.ts";
import { handleSyncAccountEmail } from "./handler.ts";

const UID = "u-email";

async function seedUser(email: string) {
  await clearEmulators();
  await commit([createWrite(`users/${UID}`, { email, role: "PLAYER", accountStatus: "ACTIVE" })]);
}

function call(claims: Record<string, unknown>, body: unknown = {}) {
  const token = emulatorToken({ sub: UID, user_id: UID, appRole: "PLAYER", ...claims });
  return handleSyncAccountEmail(bearer(token, { body: JSON.stringify(body) }));
}

Deno.test("E1: token có email mới đã xác minh → users.email = email mới chữ thường, updated: true", async () => {
  await seedUser("cu@example.com");
  const res = await call({ email: "Moi.Email@Example.com", email_verified: true });
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { updated: true });
  const user = await get(`users/${UID}`);
  assertEquals(user?.email, "moi.email@example.com");
  assertEquals(user?.updatedAt instanceof Date, true);
});

Deno.test("E2: gọi lại khi email đã đồng bộ → updated: false", async () => {
  await seedUser("moi@example.com");
  const res = await call({ email: "moi@example.com", email_verified: true });
  assertEquals(await res.json(), { updated: false });
});

Deno.test("E3: email chưa xác minh → không ghi, updated: false", async () => {
  await seedUser("cu@example.com");
  const res = await call({ email: "moi@example.com", email_verified: false });
  assertEquals(await res.json(), { updated: false });
  assertEquals((await get(`users/${UID}`))?.email, "cu@example.com");
});

Deno.test("chống giả: trường email trong body bị bỏ qua", async () => {
  await seedUser("cu@example.com");
  const res = await call({ email: "cu@example.com", email_verified: true }, { email: "hacker@example.com" });
  assertEquals(await res.json(), { updated: false });
  assertEquals((await get(`users/${UID}`))?.email, "cu@example.com");
});

Deno.test("token không hợp lệ → 401 UNAUTHENTICATED", async () => {
  const res = await handleSyncAccountEmail(new Request("http://localhost/", { method: "POST" }));
  assertEquals(res.status, 401);
  assertEquals((await res.json()).error.code, "UNAUTHENTICATED");
});
