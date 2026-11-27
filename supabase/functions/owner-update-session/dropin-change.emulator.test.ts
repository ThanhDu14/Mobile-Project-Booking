import { assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../_shared/firestore.ts";
import { prepareDropInChanged, processDropInChangeFanouts } from "../_shared/dropin-change-notification.ts";

Deno.test("D4 emulator: oversized writes use snapshot pages, concurrent ticks materialize each recipient once", async () => {
  if (!/^127\.0\.0\.1:\d+$/.test(Deno.env.get("FIRESTORE_EMULATOR_HOST") ?? "") ||
    !fs.projectId().startsWith("demo-")) throw new Error("Demo Firestore emulator required");
  const run = crypto.randomUUID();
  const now = new Date();
  const sessionId = `session-${run}`;
  const actorUid = `owner-${run}`;
  const uids = Array.from({ length: 101 }, (_, i) => `p-${run}-${i}`);
  const path = `dropInSessions/${sessionId}`;
  const registrations = [...uids, actorUid, "ignored-waiter"].map(id => ({
    id, path: `${path}/registrations/${id}`,
    data: { status: id === "ignored-waiter" ? "WAITLISTED" : "REGISTERED" },
  }));
  await fs.commit(uids.slice(0, -1).map(uid => fs.createWrite(`users/${uid}`, { accountStatus: "ACTIVE" })));
  await fs.runTransaction(async tx => {
    const source = Array.from({ length: 200 }, (_, i) => fs.createWrite(`${path}/testSourceWrites/${i}`, { i }));
    const writes = await prepareDropInChanged(tx, source, registrations, { sessionId, requestId: run, actorUid, cancelled: false, now });
    assertEquals(writes.length, 3);
    for (const write of [...source, ...writes]) tx.write(write);
  });
  const jobs = await fs.query("notificationFanouts", [{ field: "state", op: "EQUAL", value: "PENDING" }]);
  const event = jobs.find(job => (job.data.content as { data?: { sessionId?: string } }).data?.sessionId === sessionId)!;
  assertEquals(event.data.pageCount, 2);
  const deliveries = async () => (await fs.query("notificationDeliveries", []))
    .map(job => String(job.data.recipientUid)).filter(uid => uids.includes(uid)).sort();
  assertEquals(await deliveries(), []);
  await Promise.all([processDropInChangeFanouts(fs, now), processDropInChangeFanouts(fs, now)]);
  await processDropInChangeFanouts(fs, now);
  assertEquals(await deliveries(), uids.slice(0, -1).sort());
  assertEquals((await fs.get(event.path))?.state, "DONE");
  await processDropInChangeFanouts(fs, new Date(now.getTime() + 8 * 86_400_000));
  assertEquals(await fs.get(event.path), null);
  assertEquals(await fs.query("recipients", [], { parent: event.path }), []);
});
