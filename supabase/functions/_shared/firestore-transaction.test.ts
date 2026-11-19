import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { createWrite, FirestoreError, runTransaction } from "./firestore.ts";

async function transactionFixture(
  failure: "ABORTED" | "PERMISSION_DENIED",
  releaseAfterYield: boolean,
  run: (counts: { begin: number; commit: number; rollback: number }) => Promise<void>,
) {
  const originalFetch = globalThis.fetch;
  const keys = ["FIREBASE_PROJECT_ID", "FIRESTORE_EMULATOR_HOST"];
  const previous = keys.map(key => Deno.env.get(key));
  Deno.env.set(keys[0], "demo-transaction-retry");
  Deno.env.set(keys[1], "127.0.0.1:8080");
  const counts = { begin: 0, commit: 0, rollback: 0 };
  let released = true;
  let releaseTimer: ReturnType<typeof setTimeout> | undefined;
  globalThis.fetch = async input => {
    const url = String(input);
    if (!url.startsWith("http://127.0.0.1:8080/")) throw new Error("Unexpected non-emulator request");
    if (url.endsWith(":beginTransaction")) {
      counts.begin++;
      // A lock released by the server cannot recover while retries monopolize the event loop.
      if (!released) return Response.json({ error: { status: "ABORTED", message: "Lock still held" } }, { status: 409 });
      return Response.json({ transaction: `tx-${counts.begin}` });
    }
    if (url.endsWith(":commit")) {
      counts.commit++;
      if (releaseAfterYield && counts.commit > 1) return Response.json({});
      if (releaseAfterYield) {
        released = false;
        releaseTimer = setTimeout(() => { released = true; }, 0);
      }
      return Response.json({ error: { status: failure, message: "Injected transaction failure" } }, { status: failure === "ABORTED" ? 409 : 403 });
    }
    if (url.endsWith(":rollback")) {
      counts.rollback++;
      return Response.json({});
    }
    throw new Error("Unexpected Firestore operation");
  };
  try {
    await run(counts);
  } finally {
    clearTimeout(releaseTimer);
    globalThis.fetch = originalFetch;
    keys.forEach((key, index) => previous[index] === undefined ? Deno.env.delete(key) : Deno.env.set(key, previous[index]!));
  }
}

Deno.test("Firestore transaction: yields after ABORTED so a contended lock can be released", async () => {
  await transactionFixture("ABORTED", true, async counts => {
    const result = await runTransaction(async tx => {
      tx.write(createWrite("slots/retry", { status: "BLOCKED" }));
      return "committed";
    });
    assertEquals(result, "committed");
    assertEquals(counts, { begin: 2, commit: 2, rollback: 1 });
  });
});

Deno.test("Firestore transaction: exhausted ABORTED attempts roll back and remain errors", async () => {
  await transactionFixture("ABORTED", false, async counts => {
    await assertRejects(() => runTransaction(async tx => {
      tx.write(createWrite("slots/retry", { status: "BLOCKED" }));
    }, 2), FirestoreError, "Injected transaction failure");
    assertEquals(counts, { begin: 2, commit: 2, rollback: 2 });
  });
});

Deno.test("Firestore transaction: permission errors are not retried or swallowed", async () => {
  await transactionFixture("PERMISSION_DENIED", false, async counts => {
    await assertRejects(() => runTransaction(async tx => {
      tx.write(createWrite("slots/retry", { status: "BLOCKED" }));
    }), FirestoreError, "Injected transaction failure");
    assertEquals(counts, { begin: 1, commit: 1, rollback: 1 });
  });
});
