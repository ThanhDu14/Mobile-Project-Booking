// In-memory REST transport for regression tests. Never contacts a Firebase project.
import * as fs from "./firestore.ts";
import { bearer, emulatorToken } from "./testing.ts";

export async function adminFixture(run: (fixture: {
  records: Map<string, Record<string, unknown>>;
  request: (body: unknown) => Request;
  failCommit: (number: number) => void;
}) => Promise<void>) {
  const previous = globalThis.fetch;
  const keys = ["FIREBASE_PROJECT_ID", "FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"];
  const saved = keys.map((k) => Deno.env.get(k));
  Deno.env.set(keys[0], "demo-admin-regression");
  Deno.env.set(keys[1], "127.0.0.1:8080");
  Deno.env.set(keys[2], "127.0.0.1:9099");
  const records = new Map<string, Record<string, unknown>>([
    ["users/admin", { role: "ADMIN", accountStatus: "ACTIVE" }],
  ]);
  let commits = 0;
  let failure = -1;
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    if (!url.startsWith("http://127.0.0.1:8080/")) throw new Error("Unexpected non-emulator request");
    const path = decodeURIComponent(url.split("/documents/")[1] ?? "");
    const body = JSON.parse(String(init?.body ?? "{}"));
    const result = (data: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(data), { status }));
    if (url.endsWith(":beginTransaction")) return result({ transaction: "tx" });
    if (url.endsWith(":rollback")) return result({});
    if (url.endsWith(":batchGet")) {
      const name = body.documents[0];
      const value = records.get(name.split("/documents/")[1]);
      return result([value ? { found: { name, fields: fs.encodeFields(value) } } : { missing: name }]);
    }
    if (url.endsWith(":commit")) {
      if (++commits === failure) return result({ error: { status: "UNAVAILABLE" } }, 503);
      for (const w of body.writes) {
        const key = w.update.name.split("/documents/")[1];
        if (w.currentDocument?.exists === false && records.has(key)) return result({ error: { status: "ALREADY_EXISTS" } }, 409);
      }
      for (const w of body.writes) {
        const key = w.update.name.split("/documents/")[1];
        records.set(key, { ...(records.get(key) ?? {}), ...fs.decodeFields(w.update.fields) });
      }
      return result({});
    }
    const value = records.get(path);
    return value ? result({ fields: fs.encodeFields(value) }) : result({}, 404);
  };
  try {
    await run({ records, request: (body) => bearer(emulatorToken({ sub: "admin", appRole: "ADMIN" }), { body: JSON.stringify(body) }), failCommit: (n) => { failure = n; } });
  } finally {
    globalThis.fetch = previous;
    keys.forEach((k, i) => saved[i] === undefined ? Deno.env.delete(k) : Deno.env.set(k, saved[i]!));
  }
}
