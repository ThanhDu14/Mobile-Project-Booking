// Independent assertions after physical-device UI actions; local demo backend only.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../../supabase/functions/_shared/firestore.ts";
assert(fs.projectId() === "demo-admin-repair");
assert(Deno.env.get("FIRESTORE_EMULATOR_HOST") === "127.0.0.1:18080");
const deviceLog = await Deno.readTextFile(
  Deno.args[0] ?? ".gradle/handoff-device-instrumentation.log",
);
const passedTests = Number(deviceLog.match(/OK \((\d+) tests\)/)?.[1]);
assertEquals(passedTests, 3);
assert(!deviceLog.includes("FAILURES!!!"));
const deviceDuration = Number(
  deviceLog.match(/Time:\s*([\d,.]+)/)?.[1].replace(",", "."),
);
const post = (await fs.get("groups/device-group/posts/device-hide"))!;
const message = (await fs.get("chats/device-chat/messages/device-message"))!;
const group = (await fs.get("groups/device-group"))!;
assertEquals([post.status, post.hiddenBy, post.hiddenByUid], [
  "HIDDEN",
  "SYSTEM",
  null,
]);
assertEquals([message.status, message.text], ["HIDDEN", ""]);
assertEquals(group.status, "ACTIVE"); // Admin unlocked it after the owner deletion was denied.
const increase = (await fs.get("dropInSessions/device-increase"))!;
const decrease = (await fs.get("dropInSessions/device-decrease"))!;
assertEquals([
  increase.status,
  increase.registeredCount,
  increase.waitlistCount,
  increase.waitlistTickets,
], ["CANCELLED", 0, 0, []]);
assertEquals([
  decrease.capacity,
  decrease.registeredCount,
  decrease.waitlistCount,
  decrease.waitlistTickets,
], [5, 4, 1, [11]]);
const registrations = await fs.query("registrations", [], {
  parent: "dropInSessions/device-increase",
});
const registrationSummary = registrations.map(({ id, data }) => ({
  uid: id,
  status: data.status,
  paymentStatus: data.paymentStatus,
  refundAmount: data.refundAmount,
  waitlistEndReason: data.waitlistEndReason ?? null,
  hasQrToken: data.qrToken != null,
}));
assert(
  registrationSummary.every((reg) =>
    reg.status === "CANCELLED" && !reg.hasQrToken
  ),
);
assertEquals(
  registrationSummary.find((reg) => reg.uid === "device-existing")
    ?.refundAmount,
  150000,
);
assertEquals(
  registrationSummary.find((reg) => reg.uid === "device-party-a")
    ?.paymentStatus,
  null,
);

// Cron has no UI. Exercise the real local Edge endpoint separately from the device cases.
const now = new Date(), cronId = `device-cron-${now.getTime()}`;
const cronPath = `dropInSessions/${cronId}`;
await fs.commit([
  {
    update: {
      name: fs.documentName(cronPath),
      fields: fs.encodeFields({
        status: "FULL",
        capacity: 1,
        registeredCount: 1,
        startAt: new Date(now.getTime() + 29 * 60000),
        endAt: new Date(now.getTime() + 89 * 60000),
        waitlistTickets: [1],
        waitlistCount: 1,
      }),
    },
  },
  {
    update: {
      name: fs.documentName(`${cronPath}/registrations/device-party-a`),
      fields: fs.encodeFields({
        userId: "device-party-a",
        status: "WAITLISTED",
        waitlistTicket: 1,
        paymentStatus: null,
      }),
    },
  },
  {
    update: {
      name: fs.documentName(`${cronPath}-ended`),
      fields: fs.encodeFields({
        status: "FULL",
        startAt: new Date(now.getTime() - 120 * 60000),
        endAt: new Date(now.getTime() - 60 * 60000),
        waitlistCount: 0,
        waitlistTickets: [],
      }),
    },
  },
]);
async function cron(secret: string) {
  const response = await fetch(
    "http://127.0.0.1:54321/functions/v1/dropin-close",
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-cron-secret": secret },
      body: "{}",
    },
  );
  return { status: response.status, body: await response.json() };
}
assertEquals((await cron("wrong-local-test-secret")).status, 401);
const first = await cron("handoff-device-local-only");
assertEquals(first.status, 200);
assert(first.body.closedEntries >= 1 && first.body.completedSessions >= 1);
const closed = await fs.get(`${cronPath}/registrations/device-party-a`);
assertEquals([
  closed?.status,
  closed?.cancelledBy,
  closed?.waitlistEndReason,
  closed?.paymentStatus,
], ["CANCELLED", "SYSTEM", "CLOSED", null]);
assertEquals((await fs.get(`${cronPath}-ended`))?.status, "COMPLETED");
const notifications = () =>
  fs.query("notifications", [], { parent: "users/device-party-a" });
const beforeRetry = (await notifications()).filter((row) =>
  (row.data.data as Record<string, unknown>)?.sessionId === cronId
);
const retry = await cron("handoff-device-local-only");
assertEquals(retry, {
  status: 200,
  body: { closedSessions: 0, closedEntries: 0, completedSessions: 0 },
});
assertEquals(
  (await notifications()).filter((row) =>
    (row.data.data as Record<string, unknown>)?.sessionId === cronId
  ).length,
  beforeRetry.length,
);
assertEquals(beforeRetry.length, 1);
console.log(JSON.stringify(
  {
    scope:
      "Physical vivo V2247 / Android 15; isolated adminlocal APK; Firebase Emulator + Supabase local",
    uiTests: {
      passed: passedTests,
      failed: 0,
      durationSeconds: deviceDuration,
    },
    moderation: {
      postStatus: post.status,
      hiddenBy: post.hiddenBy,
      hiddenByUid: post.hiddenByUid,
      messageStatus: message.status,
      messageText: message.text,
      groupAfterAdminUnlock: group.status,
      ownerDeletion: "PERMISSION_DENIED (device instrumentation)",
    },
    owner: {
      increasedThenCancelled: {
        status: increase.status,
        registeredCount: increase.registeredCount,
        waitlistTickets: increase.waitlistTickets,
        registrations: registrationSummary,
      },
      decreased: {
        capacity: decrease.capacity,
        registeredCount: decrease.registeredCount,
        waitlistTickets: decrease.waitlistTickets,
      },
    },
    cronLocal: {
      invalidSecretStatus: 401,
      first,
      retry,
      closedRegistrationReason: closed?.waitlistEndReason,
      notificationsForClosedEntry: beforeRetry.length,
    },
    limitations: [
      "Not a hosted-backend UI test",
      "Locked-group deletion was checked through Rules; group-manage is covered separately by the community suite",
      "Admin unlock uses API because there is no unlock screen",
      "No push-delivery or camera test",
    ],
  },
  null,
  2,
));
