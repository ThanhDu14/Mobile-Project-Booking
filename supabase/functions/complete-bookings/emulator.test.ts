import { assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../_shared/firestore.ts";
import { handleCompleteBookings } from "./handler.ts";

Deno.test("RR1 emulator: competing ticks complete and enqueue exactly once", async () => {
  if (!/^127\.0\.0\.1:\d+$/.test(Deno.env.get("FIRESTORE_EMULATOR_HOST") ?? "") ||
    !fs.projectId().startsWith("demo-")) throw new Error("Demo emulator required");
  const uid = crypto.randomUUID();
  const id = crypto.randomUUID();
  const venueId = crypto.randomUUID();
  const now = new Date();
  await fs.commit([
    fs.createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" }),
    fs.createWrite(`venues/${venueId}`, { ownerId: "other-owner" }),
    fs.createWrite(`bookings/${id}`, { status: "CONFIRMED", userId: uid, venueId, endAt: now, checkedInAt: now }),
  ]);
  const tick = () => handleCompleteBookings(new Request("http://localhost", { method: "POST" }), {
    now: () => now, cronSecret: "",
  });
  const results = await Promise.all([tick(), tick()]);
  assertEquals(results.map(r => r.status), [200, 200]);
  const bodies = await Promise.all(results.map(r => r.json()));
  assertEquals(bodies.reduce((sum, item) => sum + item.completed, 0), 1);
  assertEquals((await fs.get(`bookings/${id}`))?.status, "COMPLETED");
  const inbox = await fs.query("notifications", [], { parent: `users/${uid}` });
  const jobs = await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]);
  assertEquals(inbox.length, 1);
  assertEquals(inbox[0].data.data, { bookingId: id });
  assertEquals(jobs.map(job => job.data.notificationId), [inbox[0].id]);
});
