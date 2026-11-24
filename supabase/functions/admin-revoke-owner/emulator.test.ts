import { assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../_shared/firestore.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleAdminRevokeOwner } from "./handler.ts";

Deno.test("O2 emulator: replay after reapproval preserves owner role and does not enqueue twice", async () => {
  for (const key of ["FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"]) {
    if (!/^127\.0\.0\.1:\d+$/.test(Deno.env.get(key) ?? "")) throw new Error("Demo emulator required");
  }
  if (!fs.projectId().startsWith("demo-")) throw new Error("Demo project required");
  const adminUid = crypto.randomUUID();
  const uid = crypto.randomUUID();
  const requestId = crypto.randomUUID();
  const path = `users/${uid}`;
  const owner = { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" };
  await fs.commit([
    fs.createWrite(`users/${adminUid}`, { role: "ADMIN", accountStatus: "ACTIVE" }),
    fs.createWrite(path, owner),
  ]);
  const claims: unknown[] = [];
  const revoke = (reason: string) => handleAdminRevokeOwner(bearer(
    emulatorToken({ sub: adminUid, appRole: "ADMIN" }),
    { body: JSON.stringify({ uid, reason, requestId }) },
  ), { firestore: fs, mergeClaims: async (_uid, value) => { claims.push(value.appRole); } });
  const first = await revoke("Policy");
  assertEquals(first.status, 200);
  assertEquals((await first.json()).alreadyCommitted, false);
  assertEquals((await fs.get(path))?.role, "PLAYER");
  await fs.commit([fs.updateWrite(path, owner)]);
  const retry = await revoke("Policy");
  assertEquals(retry.status, 200);
  assertEquals((await retry.json()).alreadyCommitted, true);
  assertEquals((await fs.get(path))?.role, "OWNER");
  assertEquals(claims, ["PLAYER", "OWNER"]);
  const conflict = await revoke("Different reason");
  assertEquals(conflict.status, 409);
  await conflict.body?.cancel();
  assertEquals((await fs.query("notifications", [], { parent: path })).length, 1);
  assertEquals((await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }])).length, 1);
});
