import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../_shared/firestore.ts";
import { call, createBody, deps, done, inbox, seed, sent } from "./testing.ts";
import { runDeliveryWorker } from "../_shared/notification-worker.ts";

Deno.test("R1: delete/recreate has a fresh generation; create replay and reply edits never duplicate", async () => {
  await seed({ b: done(1) });
  assertEquals((await call("player1", createBody("b"))).status, 201);
  const first = (await fs.get("reviews/b"))!.notificationGeneration;
  assertEquals((await call("player1", createBody("b"))).status, 200);
  assertEquals((await inbox("owner1", "REVIEW_NEW")).length, 1);
  assertEquals((await call("owner1", { action: "reply", reviewId: "b", text: "First" }, "OWNER")).status, 200);
  assertEquals((await call("owner1", { action: "reply", reviewId: "b", text: "Edited" }, "OWNER")).status, 200);
  assertEquals((await inbox("player1", "REVIEW_REPLIED")).length, 1);
  assertEquals((await fs.get("reviews/b"))!.notificationGeneration, first);
  assertEquals((await call("player1", { action: "delete", reviewId: "b" })).status, 200);
  assertEquals((await call("player1", createBody("b"))).status, 201);
  assert((await fs.get("reviews/b"))!.notificationGeneration !== first);
  assertEquals((await inbox("owner1", "REVIEW_NEW")).length, 2);
  assertEquals(sent, []);
});

Deno.test("R1: legacy first reply assigns generation, excludes actor and inactive/deleting recipient, no backfill", async () => {
  for (const author of ["player1", "owner1", "locked1", "deleting", "gone"]) {
    await seed();
    if (author === "deleting") await fs.commit([fs.createWrite("users/deleting", { accountStatus: "ACTIVE", deletionRequestedAt: new Date() })]);
    await fs.commit([fs.createWrite("reviews/legacy", { userId: author, venueId: "venueA", ownerReply: null })]);
    assertEquals((await call("owner1", { action: "reply", reviewId: "legacy", text: "Reply" }, "OWNER")).status, 200);
    assert(typeof (await fs.get("reviews/legacy"))!.notificationGeneration === "string");
    assertEquals((await inbox(author, "REVIEW_REPLIED")).length, author === "player1" ? 1 : 0);
    assertEquals((await inbox("owner1", "REVIEW_NEW")).length, 0);
  }
});

Deno.test("R1: source transaction failure commits neither review/rating nor inbox/job", async () => {
  await seed({ b: done(1) });
  const failing: typeof deps = { ...deps, firestore: { ...fs, runTransaction: fn => fs.runTransaction(async tx => {
    await fn(tx);
    throw new Error("injected failure after source and notification preparation");
  }) } };
  assertEquals((await call("player1", createBody("b"), "PLAYER", failing)).status, 500);
  assertEquals(await fs.get("reviews/b"), null);
  assertEquals((await fs.get("venues/venueA"))!.ratingCount, 0);
  assertEquals((await inbox("owner1", "REVIEW_NEW")).length, 0);
  assertEquals((await fs.query("notificationDeliveries", [])).length, 0);
});

Deno.test("R1: fake FCM failure after source commit retries the job, preserves inbox and never repeats accepted token", async () => {
  await seed({ b: done(1) });
  await fs.commit([fs.updateWrite("users/owner1", { fcmTokens: ["accepted", "transient"] })]);
  assertEquals((await call("player1", createBody("b"))).status, 201);
  const saved = await inbox("owner1", "REVIEW_NEW");
  let now = deps.now();
  const calls: string[] = [];
  await runDeliveryWorker(fs, async token => { calls.push(token); return { kind: token === "transient" ? "retry" : "accepted" }; }, () => now);
  assertEquals((await inbox("owner1", "REVIEW_NEW")).map(n => n.id), saved.map(n => n.id));
  now = new Date(now.getTime() + 60000);
  await runDeliveryWorker(fs, async token => { calls.push(token); return { kind: "accepted" }; }, () => now);
  assertEquals(calls.filter(t => t === "accepted").length, 1);
  assertEquals(calls.filter(t => t === "transient").length, 2);
  assertEquals((await fs.query("notificationDeliveries", [])).map(n => n.data.state), ["SENT"]);
  assertEquals((await call("player1", createBody("b"))).status, 200);
  assertEquals((await inbox("owner1", "REVIEW_NEW")).length, 1);
});
