import { handleOwnerCreateVoucher } from "./owner-create-voucher/handler.ts";
import { handleOwnerStatisticsSource } from "./owner-statistics-source/handler.ts";
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "./_shared/firestore.ts";
import { bearer, emulatorToken } from "./_shared/testing.ts";
import { handleSyncVenuePrice } from "./sync-venue-price/handler.ts";
import { handleOwnerCreateSession } from "./owner-create-session/handler.ts";
import { handleOwnerManageCourt } from "./owner-manage-court/handler.ts";
const host = Deno.env.get("FIRESTORE_EMULATOR_HOST");
const local = !!host && /^(127\.0\.0\.1|localhost):\d+$/.test(host);
if (local) Deno.env.set("FIREBASE_PROJECT_ID", "demo-owner-feature11");
const request = (body: Record<string, unknown>, role = "OWNER") =>
  bearer(emulatorToken({ sub: "owner", appRole: role }), {
    body: JSON.stringify({ venueId: "v1", ...body }),
  });
const price = (startTime = "0800", endTime = "1000", pricePerHour = 60000) => ({
  daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
  startTime,
  endTime,
  pricePerHour,
  label: "NORMAL",
});
async function seed() {
  const response = await fetch(
    `http://${host}/emulator/v1/projects/demo-owner-feature11/databases/(default)/documents`,
    { method: "DELETE" },
  );
  await response.body?.cancel();
  assert(response.ok);
  await fs.commit([
    fs.createWrite("users/owner", {
      role: "OWNER",
      ownerStatus: "APPROVED",
      accountStatus: "ACTIVE",
    }),
    fs.createWrite("venues/v1", {
      ownerId: "owner",
      status: "ACTIVE",
      openTime: "0600",
      closeTime: "2200",
      minPricePerHour: null,
    }),
    fs.createWrite("venues/v1/courts/c1", { isActive: true }),
    fs.createWrite("bookings/history", {
      items: [{ price: 12345 }],
      venueId: "v1",
    }),
  ]);
}

const courtFields = (isActive = true, sortOrder = 1) => ({
  name: "TEST Court",
  surfaceType: "PU",
  isActive,
  sortOrder,
});
Deno.test({
  name:
    "Feature 11 courts: concurrent creates and identical retry derive one authoritative active count",
  ignore: !local,
  fn: async () => {
    await seed();
    await fs.commit([fs.updateWrite("venues/v1", { courtCount: 99 })]);
    const responses = await Promise.all(
      ["c2", "c3", "c2"].map((courtId) =>
        handleOwnerManageCourt(
          request({ action: "CREATE", courtId, court: courtFields() }),
        )
      ),
    );
    assertEquals(responses.map((r) => r.status), [200, 200, 200]);
    for (const response of responses) await response.body?.cancel();
    assertEquals((await fs.list("venues/v1/courts")).length, 3);
    assertEquals((await fs.get("venues/v1"))?.courtCount, 3);
    const edit = await handleOwnerManageCourt(
      request({
        action: "UPDATE",
        courtId: "c2",
        court: { ...courtFields(), name: "Edited" },
      }),
    );
    assertEquals(edit.status, 200);
    await edit.body?.cancel();
    const retry = await handleOwnerManageCourt(
      request({ action: "CREATE", courtId: "c2", court: courtFields() }),
    );
    assertEquals(retry.status, 409);
    await retry.body?.cancel();
    assertEquals((await fs.get("venues/v1/courts/c2"))?.name, "Edited");
    assertEquals((await fs.get("venues/v1"))?.courtCount, 3);
  },
});
Deno.test({
  name:
    "Feature 11 courts: concurrent deactivation permits zero, reactivation/reordering preserve history",
  ignore: !local,
  fn: async () => {
    await seed();
    await fs.commit([
      fs.updateWrite("venues/v1/courts/c1", courtFields(true, 0)),
      fs.createWrite("venues/v1/courts/c2", courtFields(true, 1)),
      fs.createWrite("slots/history", {
        status: "BOOKED",
        courtId: "c1",
        bookingId: "history",
      }),
    ]);
    const responses = await Promise.all(
      ["c1", "c2"].map((courtId, i) =>
        handleOwnerManageCourt(
          request({ action: "UPDATE", courtId, court: courtFields(false, i) }),
        )
      ),
    );
    for (const r of responses) {
      assertEquals(r.status, 200);
      await r.body?.cancel();
    }
    assertEquals((await fs.get("venues/v1"))?.courtCount, 0);
    const activate = await handleOwnerManageCourt(
      request({ action: "UPDATE", courtId: "c1", court: courtFields(true, 0) }),
    );
    assertEquals(activate.status, 200);
    await activate.body?.cancel();
    const swap = {
      action: "REORDER",
      courtId: "c1",
      secondCourtId: "c2",
      firstOrder: 0,
      secondOrder: 1,
    };
    const reorder = await handleOwnerManageCourt(request(swap));
    assertEquals(reorder.status, 200);
    await reorder.body?.cancel();
    const stale = await handleOwnerManageCourt(request(swap));
    assertEquals(stale.status, 409);
    await stale.body?.cancel();
    assertEquals((await fs.get("venues/v1"))?.courtCount, 1);
    assertEquals((await fs.get("slots/history"))?.status, "BOOKED");
    assertEquals((await fs.get("bookings/history"))?.items, [{ price: 12345 }]);
  },
});
Deno.test({
  name:
    "Feature 11 courts: forged ownership, revoked/deleting accounts, stale claims and locked venue cannot mutate count",
  ignore: !local,
  fn: async () => {
    for (
      const [path, patch, role] of [
        ["users/owner", { role: "PLAYER" }, "OWNER"],
        ["users/owner", { ownerStatus: "REJECTED" }, "OWNER"],
        ["users/owner", { accountStatus: "LOCKED" }, "OWNER"],
        ["users/owner", { deletionRequestedAt: new Date() }, "OWNER"],
        ["venues/v1", { ownerId: "other" }, "OWNER"],
        ["venues/v1", { status: "LOCKED" }, "OWNER"],
        ["users/owner", {}, "ADMIN"],
      ] as const
    ) {
      await seed();
      await fs.commit([fs.updateWrite(path, patch)]);
      const response = await handleOwnerManageCourt(
        request(
          { action: "CREATE", courtId: "c2", court: courtFields() },
          role,
        ),
      );
      assert([403, 404].includes(response.status));
      await response.body?.cancel();
      assertEquals(await fs.get("venues/v1/courts/c2"), null);
    }
  },
});
Deno.test({
  name:
    "Feature 11 courts: validate fields and IDs, allow duplicate names and HIDDEN maintenance",
  ignore: !local,
  fn: async () => {
    await seed();
    for (
      const patch of [
        { name: " " },
        { surfaceType: "INVALID" },
        { isActive: "true" },
        { sortOrder: -1 },
        { sortOrder: 1.5 },
      ]
    ) {
      const response = await handleOwnerManageCourt(
        request({
          action: "CREATE",
          courtId: "new",
          court: { ...courtFields(), ...patch },
        }),
      );
      assertEquals(response.status, 400);
      await response.body?.cancel();
    }
    const foreign = await handleOwnerManageCourt(
      request({ action: "CREATE", courtId: "../escape", court: courtFields() }),
    );
    assertEquals(foreign.status, 400);
    await foreign.body?.cancel();
    await fs.commit([fs.updateWrite("venues/v1", { status: "HIDDEN" })]);
    for (const courtId of ["new", "another"]) {
      const response = await handleOwnerManageCourt(
        request({ action: "CREATE", courtId, court: courtFields() }),
      );
      assertEquals(response.status, 200);
      await response.body?.cancel();
    }
    assertEquals((await fs.get("venues/v1"))?.courtCount, 3);
  },
});
Deno.test({
  name:
    "Feature 11 pricing: identical concurrent retries create one rule and synchronize minimum",
  ignore: !local,
  fn: async () => {
    await seed();
    const responses = await Promise.all(
      [1, 2].map(() =>
        handleSyncVenuePrice(request({ action: "CREATE", rule: price() }))
      ),
    );
    assertEquals(
      responses.map((r) => r.status),
      [200, 200],
    );
    const bodies = await Promise.all(responses.map((r) => r.json()));
    assertEquals(bodies[0].ruleId, bodies[1].ruleId);
    assertEquals((await fs.list("venues/v1/priceRules")).length, 1);
    assertEquals((await fs.get("venues/v1"))?.minPricePerHour, 60000);
    assertEquals((await fs.get("bookings/history"))?.items, [{ price: 12345 }]);
    for (const action of ["DELETE", "DELETE"]) {
      const response = await handleSyncVenuePrice(
        request({ action, ruleId: bodies[0].ruleId }),
      );
      assertEquals(response.status, 200);
      await response.body?.cancel();
      assertEquals((await fs.get("venues/v1"))?.minPricePerHour, null);
    }
  },
});
Deno.test({
  name:
    "Feature 11 pricing: overlapping concurrent creates have one winner and no partial minimum",
  ignore: !local,
  fn: async () => {
    await seed();
    const responses = await Promise.all(
      [price(), price("0830", "1030", 80000)].map((rule) =>
        handleSyncVenuePrice(request({ action: "CREATE", rule }))
      ),
    );
    assertEquals(responses.map((r) => r.status).sort(), [200, 409]);
    for (const r of responses) await r.body?.cancel();
    const rules = await fs.list("venues/v1/priceRules");
    assertEquals(rules.length, 1);
    assertEquals(
      (await fs.get("venues/v1"))?.minPricePerHour,
      rules[0].pricePerHour,
    );
  },
});
Deno.test({
  name:
    "Feature 11 pricing: current ownership, revocation, locked/deleting account and locked venue deny SYNC and CRUD",
  ignore: !local,
  fn: async () => {
    for (
      const [path, patch, role] of [
        ["users/owner", { role: "PLAYER" }, "OWNER"],
        ["users/owner", { ownerStatus: "REJECTED" }, "OWNER"],
        ["users/owner", { accountStatus: "LOCKED" }, "OWNER"],
        ["users/owner", { deletionRequestedAt: new Date() }, "OWNER"],
        ["venues/v1", { ownerId: "other" }, "OWNER"],
        ["venues/v1", { status: "LOCKED" }, "OWNER"],
        ["users/owner", {}, "PLAYER"],
      ] as const
    ) {
      await seed();
      await fs.commit([fs.updateWrite(path, patch)]);
      for (const action of ["SYNC", "CREATE"]) {
        const response = await handleSyncVenuePrice(
          request({ action, rule: price() }, role),
        );
        assert([403, 404].includes(response.status));
        await response.body?.cancel();
      }
      assertEquals((await fs.list("venues/v1/priceRules")).length, 0);
      assertEquals((await fs.get("venues/v1"))?.minPricePerHour, null);
    }
  },
});
Deno.test({
  name:
    "Feature 11 pricing: server validates payload, allows adjacent periods, and stale retry cannot undo edits",
  ignore: !local,
  fn: async () => {
    await seed();
    for (
      const rule of [
        { ...price(), daysOfWeek: [1, 1] },
        { ...price(), startTime: "0815" },
        { ...price(), pricePerHour: 61000 },
        { ...price(), endTime: "0700" },
      ]
    ) {
      const response = await handleSyncVenuePrice(
        request({ action: "CREATE", rule }),
      );
      assertEquals(response.status, 400);
      await response.body?.cancel();
    }
    const create = await handleSyncVenuePrice(
      request({ action: "CREATE", rule: price() }),
    );
    const { ruleId } = await create.json();
    const update = await handleSyncVenuePrice(
      request({ action: "UPDATE", ruleId, rule: price("0800", "1000", 80000) }),
    );
    assertEquals(update.status, 200);
    await update.body?.cancel();
    const stale = await handleSyncVenuePrice(
      request({ action: "CREATE", rule: price() }),
    );
    assertEquals(stale.status, 409);
    await stale.body?.cancel();
    const adjacent = await handleSyncVenuePrice(
      request({ action: "CREATE", rule: price("1000", "1100", 40000) }),
    );
    assertEquals(adjacent.status, 200);
    await adjacent.body?.cancel();
    assertEquals((await fs.get("venues/v1"))?.minPricePerHour, 40000);
  },
});
Deno.test({
  name:
    "Feature 11 drop-in: lost response retry returns the original session; changed payload conflicts",
  ignore: !local,
  fn: async () => {
    await seed();
    const priced = await handleSyncVenuePrice(
      request({ action: "CREATE", rule: price("0600", "2200") }),
    );
    assertEquals(priced.status, 200);
    await priced.body?.cancel();
    const body = {
      courtIds: ["c1"],
      date: "20991201",
      startTime: "0800",
      endTime: "0900",
      capacity: 8,
      pricePerPerson: 0,
      level: "BEGINNER",
      note: "TEST",
    };
    const responses = await Promise.all(
      [1, 2].map(() => handleOwnerCreateSession(request(body))),
    );
    assertEquals(responses.map((r) => r.status), [200, 200]);
    const result = await Promise.all(responses.map((r) => r.json()));
    assertEquals(result[0].sessionId, result[1].sessionId);
    assertEquals((await fs.list("dropInSessions")).length, 1);
    assertEquals((await fs.list("slots")).length, 2);
    const changed = await handleOwnerCreateSession(
      request({ ...body, capacity: 9 }),
    );
    assertEquals(changed.status, 409);
    await changed.body?.cancel();
    assertEquals(
      (await fs.get(`dropInSessions/${result[0].sessionId}`))?.capacity,
      8,
    );
  },
});

const draft = {
  venueId: "v1",
  code: "AUDIT10",
  type: "PERCENT",
  value: 10,
  maxDiscount: 40000,
  minOrderTotal: 100000,
  validFrom: "2026-11-01T00:00:00+07:00",
  validTo: "2026-12-01T00:00:00+07:00",
  usageLimit: 10,
  perUserLimit: 1,
  isActive: true,
};

Deno.test({
  name:
    "Feature 11 statistics: each projection refreshes configuration in the same snapshot and rejects deletion",
  ignore: !local,
  fn: async () => {
    await seed();
    await fs.commit([fs.createWrite("venues/v1/priceRules/p1", price())]);
    const report = () =>
      handleOwnerStatisticsSource(
        request({ startDate: "2099-12-01", endDate: "2099-12-01" }),
      );
    const first = await report();
    assertEquals(first.status, 200);
    const a = await first.json();
    assertEquals(a.availability.courtIds, ["c1"]);
    assertEquals(a.availability.priceRules[0].pricePerHour, 60000);
    await fs.commit([
      fs.updateWrite("venues/v1/courts/c1", { isActive: false }),
      fs.updateWrite("venues/v1/priceRules/p1", { pricePerHour: 80000 }),
      fs.updateWrite("venues/v1", { closeTime: "2100" }),
    ]);
    const second = await report();
    assertEquals(second.status, 200);
    const b = await second.json();
    assertEquals(b.availability.courtIds, []);
    assertEquals(b.availability.priceRules[0].pricePerHour, 80000);
    assertEquals(b.availability.closeTime, "2100");
    await fs.commit([
      fs.updateWrite("users/owner", { deletionRequestedAt: new Date() }),
    ]);
    const denied = await report();
    assertEquals(denied.status, 403);
    await denied.body?.cancel();
  },
});
Deno.test({
  name:
    "voucher create retry reads the existing result and never resets counters or changed fields",
  ignore: !local,
  fn: async () => {
    await seed();
    const request = (body: unknown) =>
      bearer(emulatorToken({ sub: "owner", appRole: "OWNER" }), {
        body: JSON.stringify(body),
      });
    for (let i = 0; i < 2; i++) {
      const response = await handleOwnerCreateVoucher(request(draft), {
        firestore: fs,
      });
      assertEquals(response.status, 200);
      await response.body?.cancel();
    }
    await fs.commit([
      fs.updateWrite("venues/v1/vouchers/AUDIT10", { usedCount: 3 }),
    ]);
    const retry = await handleOwnerCreateVoucher(request(draft), {
      firestore: fs,
    });
    assertEquals(retry.status, 200);
    await retry.body?.cancel();
    assertEquals((await fs.get("venues/v1/vouchers/AUDIT10"))?.usedCount, 3);
    const changed = await handleOwnerCreateVoucher(
      request({ ...draft, value: 20 }),
      { firestore: fs },
    );
    assertEquals(changed.status, 409);
    await changed.body?.cancel();
    assertEquals((await fs.get("venues/v1/vouchers/AUDIT10"))?.value, 10);
  },
});
import { handleManageSlotBlocks } from "./manage-slot-blocks/handler.ts";
import { handleOwnerUpdateSession } from "./owner-update-session/handler.ts";
import { handleOwnerCancelSession } from "./owner-cancel-session/handler.ts";
import { handleOwnerCheckinDropIn } from "./owner-checkin-dropin-registration/handler.ts";
Deno.test({
  name:
    "Feature 11 operations: stale ADMIN or PLAYER claims cannot bypass current authority",
  ignore: !local,
  fn: async () => {
    for (
      const [patch, role] of [[{ role: "PLAYER" }, "ADMIN"], [{
        role: "ADMIN",
        accountStatus: "LOCKED",
      }, "ADMIN"], [
        { role: "ADMIN", deletionRequestedAt: new Date() },
        "ADMIN",
      ], [{}, "PLAYER"]] as const
    ) {
      await seed();
      await fs.commit([fs.updateWrite("users/owner", patch)]);
      const body = {
        courtIds: ["c1"],
        courtId: "c1",
        date: "20991201",
        startTime: "0800",
        endTime: "0900",
        capacity: 8,
        pricePerPerson: 0,
        level: "BEGINNER",
        note: "TEST",
        sessionId: "not-created",
        requestId: crypto.randomUUID(),
        action: "LOCK",
        slotIds: ["c1_20991201_0800"],
        blockReason: "MAINTENANCE",
        reason: "TEST",
        credential: "DI-ABCDEFGH",
      };
      for (
        const handler of [
          handleOwnerCreateSession,
          handleOwnerUpdateSession,
          handleManageSlotBlocks,
          handleOwnerCancelSession,
          handleOwnerCheckinDropIn,
        ]
      ) {
        const response = await handler(request(body, role));
        assertEquals(response.status, 403);
        await response.body?.cancel();
      }
      assertEquals((await fs.list("dropInSessions")).length, 0);
      assertEquals((await fs.list("slots")).length, 0);
    }
  },
});

Deno.test({
  name:
    "Feature 11 drop-in mutations: locked and hidden venues block cancellation and check-in",
  ignore: !local,
  fn: async () => {
    for (const status of ["LOCKED", "HIDDEN"]) {
      await seed();
      await fs.commit([fs.updateWrite("venues/v1", { status })]);
      for (
        const handler of [handleOwnerCancelSession, handleOwnerCheckinDropIn]
      ) {
        const response = await handler(
          request({
            sessionId: "not-created",
            reason: "TEST",
            credential: "DI-ABCDEFGH",
          }),
        );
        assertEquals(response.status, 403);
        await response.body?.cancel();
      }
    }
  },
});
Deno.test({
  name:
    "Feature 11 drop-in check-in: exact time boundaries, replay, concurrent scan and invalid states",
  ignore: !local,
  fn: async () => {
    const start = new Date("2099-12-01T01:00:00Z"),
      end = new Date("2099-12-01T02:00:00Z");
    async function fixture() {
      await seed();
      await fs.commit([
        fs.createWrite("dropInSessions/window", {
          venueId: "v1",
          ownerId: "owner",
          status: "OPEN",
          startAt: start,
          endAt: end,
          registeredCount: 1,
        }),
        fs.createWrite("dropInSessions/window/registrations/player", {
          sessionId: "window",
          venueId: "v1",
          userId: "player",
          status: "REGISTERED",
          ticketCode: "DI-ABCDEFGH",
          qrToken: "x".repeat(43),
          paymentStatus: "UNPAID",
          amount: 50000,
        }),
      ]);
    }
    const scan = (instant: number, credential = "DI-ABCDEFGH") =>
      handleOwnerCheckinDropIn(request({ sessionId: "window", credential }), {
        firestore: fs,
        clock: () => new Date(instant),
      });
    for (
      const [instant, status] of [
        [start.getTime() - 1800001, 400],
        [start.getTime() - 1800000, 200],
        [end.getTime() - 1, 200],
        [end.getTime(), 400],
      ] as const
    ) {
      await fixture();
      const response = await scan(instant);
      assertEquals(response.status, status);
      await response.body?.cancel();
      assertEquals(
        (await fs.get("dropInSessions/window/registrations/player"))?.status,
        status === 200 ? "CHECKED_IN" : "REGISTERED",
      );
    }
    await fixture();
    const race = await Promise.all([
      scan(start.getTime()),
      scan(start.getTime(), "x".repeat(43)),
    ]);
    assertEquals(race.map((r) => r.status).sort(), [200, 409]);
    for (const response of race) await response.body?.cancel();
    assertEquals((await fs.get("dropInSessions/window"))?.registeredCount, 1);
    const replay = await scan(start.getTime());
    assertEquals(replay.status, 409);
    await replay.body?.cancel();
    for (
      const [path, patch] of [
        ["dropInSessions/window", { status: "CANCELLED" }],
        ["dropInSessions/window", { status: "COMPLETED" }],
        ["dropInSessions/window/registrations/player", {
          status: "WAITLISTED",
        }],
        ["dropInSessions/window/registrations/player", { status: "CANCELLED" }],
      ] as const
    ) {
      await fixture();
      await fs.commit([fs.updateWrite(path, patch)]);
      const response = await scan(start.getTime());
      assertEquals(response.status, 409);
      await response.body?.cancel();
    }
  },
});
