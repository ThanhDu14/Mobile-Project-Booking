import { assertEquals } from "jsr:@std/assert@1.0.19";
import type { VerifiedToken } from "../_shared/auth.ts";
import type { OwnerDocumentDownloadDeps } from "./handler.ts";
import { handleOwnerDocumentDownload } from "./handler.ts";

const uid = "player-1";
const applicationId = "Abcdefghijklmnopqrst";
const path = `${uid}/${applicationId}/identity.webp`;
const player: VerifiedToken & { appRole: "PLAYER" } = {
  uid, appRole: "PLAYER", email: null, emailVerified: false, phone: null, name: null, signInProvider: "password",
};

function makeDeps(options: { uid?: string; documentPaths?: string[] } = {}) {
  const calls: { path: string; expires: number }[] = [];
  const deps: OwnerDocumentDownloadDeps = {
    auth: { requireUser: async () => ({ ...player, uid: options.uid ?? uid }) },
    firestore: { get: async () => ({ uid, documentPaths: options.documentPaths ?? [path] }) },
    signUrl: async (requestedPath, expires) => {
      calls.push({ path: requestedPath, expires });
      return "https://storage.example/signed";
    },
  };
  return { deps, calls };
}

async function call(deps: OwnerDocumentDownloadDeps, payload: unknown = { applicationId, path }) {
  return await handleOwnerDocumentDownload(new Request("http://localhost", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  }), deps);
}

Deno.test("issues a five-minute signed URL only for a document in the caller's application", async () => {
  const { deps, calls } = makeDeps();
  const response = await call(deps);
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { signedUrl: "https://storage.example/signed", expiresInSeconds: 300 });
  assertEquals(response.headers.get("Cache-Control"), "private, no-store");
  assertEquals(calls, [{ path, expires: 300 }]);
});

Deno.test("does not sign a path belonging to another applicant", async () => {
  const { deps, calls } = makeDeps({ uid: "other-user" });
  const response = await call(deps);
  assertEquals(response.status, 400);
  assertEquals(calls.length, 0);
});

Deno.test("does not sign an object absent from the application's documentPaths", async () => {
  const { deps, calls } = makeDeps({ documentPaths: [] });
  const response = await call(deps);
  assertEquals(response.status, 404);
  const error = await response.json();
  assertEquals(error.error.code, "NOT_FOUND");
  assertEquals(JSON.stringify(error).includes(path), false);
  assertEquals(calls.length, 0);
});

Deno.test("a retry issues a new five-minute URL instead of reusing an expired URL", async () => {
  let issued = 0;
  const { deps } = makeDeps();
  const retryDeps: OwnerDocumentDownloadDeps = {
    ...deps,
    signUrl: async (_requestedPath, expires) => {
      if (expires !== 300) throw new Error("unexpected expiry");
      issued += 1;
      return `https://storage.example/signed-${issued}`;
    },
  };
  const first = await (await call(retryDeps)).json();
  const second = await (await call(retryDeps)).json();
  assertEquals(first.signedUrl, "https://storage.example/signed-1");
  assertEquals(second.signedUrl, "https://storage.example/signed-2");
  assertEquals(issued, 2);
});

Deno.test("does not reveal whether another applicant's application exists", async () => {
  const { deps, calls } = makeDeps({ uid: "other-user" });
  const response = await call(deps, { applicationId, path: `${uid}/${applicationId}/identity.webp` });
  assertEquals(response.status, 400);
  assertEquals(calls.length, 0);
});
