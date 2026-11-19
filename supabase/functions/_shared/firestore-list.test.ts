import { assertEquals } from "jsr:@std/assert@1.0.19";
import { list } from "./firestore.ts";

Deno.test("R120-07: list follows page tokens and preserves document ID over stored UID", async () => {
  const fetch = globalThis.fetch;
  const saved = [Deno.env.get("FIREBASE_PROJECT_ID"), Deno.env.get("FIRESTORE_EMULATOR_HOST")];
  Deno.env.set("FIREBASE_PROJECT_ID", "demo-admin");
  Deno.env.set("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080");
  let calls = 0;
  globalThis.fetch = (input) => {
    calls++;
    const second = new URL(String(input)).searchParams.get("pageToken") === "next page";
    return Promise.resolve(new Response(JSON.stringify({ documents: [{ name: `projects/demo-admin/databases/(default)/documents/users/${second ? "two" : "one"}`, fields: { accountStatus: { stringValue: "ACTIVE" } } }], ...(!second ? { nextPageToken: "next page" } : {}) })));
  };
  try {
    const users = await list("users");
    assertEquals(calls, 2);
    assertEquals(users.map((u) => u.__documentId), ["one", "two"]);
  } finally {
    globalThis.fetch = fetch;
    ["FIREBASE_PROJECT_ID", "FIRESTORE_EMULATOR_HOST"].forEach((k, i) => saved[i] === undefined ? Deno.env.delete(k) : Deno.env.set(k, saved[i]!));
  }
});
