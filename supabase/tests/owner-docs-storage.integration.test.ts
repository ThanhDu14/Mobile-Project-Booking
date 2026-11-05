import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerDocs } from "../functions/owner-docs/handler.ts";
import { commit, createWrite, deleteWrite } from "../functions/_shared/firestore.ts";

// Run only against the local Supabase + Firebase Auth/Firestore emulators. This file exercises
// Storage's real signed URLs and bucket limits; it is intentionally outside handler.test.ts.
const firebaseProject = Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow";
const authHost = Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") ?? "127.0.0.1:9099";
const firestoreHost = Deno.env.get("FIRESTORE_EMULATOR_HOST") ?? "127.0.0.1:8080";
const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "http://127.0.0.1:54321";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

Deno.test({
  name: "owner-docs signed URL integration against local Storage and Firebase emulators",
  async fn() {
    if (!serviceKey) throw new Error("Set SUPABASE_SERVICE_ROLE_KEY from `supabase status --output env`; local Storage integration must not be skipped.");
    Deno.env.set("FIREBASE_PROJECT_ID", firebaseProject);
    Deno.env.set("FIREBASE_AUTH_EMULATOR_HOST", authHost);
    Deno.env.set("FIRESTORE_EMULATOR_HOST", firestoreHost);
    Deno.env.set("SUPABASE_URL", supabaseUrl);

    const applicant = await createFirebaseUser("PLAYER");
    const other = await createFirebaseUser("PLAYER");
    const admin = await createFirebaseUser("ADMIN");
    await seedUser(applicant.uid, "PLAYER", "NONE");
    await seedUser(other.uid, "PLAYER", "NONE");
    await seedUser(admin.uid, "ADMIN", "NONE");

    const applicationId = crypto.randomUUID();
    const upload = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_UPLOAD", applicationId, fileName: "license.jpg", contentType: "image/jpeg", sizeBytes: 12,
    });
    assertEquals(upload.status, 200);
    const uploadBody = await upload.json() as { path: string; signedUrl: string; token: string };
    assert(uploadBody.path.startsWith(`${applicant.uid}/${applicationId}/`));
    const signedUploadUrl = new URL(uploadBody.signedUrl, supabaseUrl);
    signedUploadUrl.searchParams.set("token", uploadBody.token);
    const uploaded = await fetch(signedUploadUrl, {
      method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: new Uint8Array([1, 2, 3, 4]),
    });
    assert(uploaded.ok, `signed upload failed (${uploaded.status})`);
    await uploaded.body?.cancel();

    const badMimeRequest = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_UPLOAD", applicationId, fileName: "invalid.pdf", contentType: "application/pdf", sizeBytes: 12,
    });
    assertEquals(badMimeRequest.status, 400, "unsupported MIME is rejected before URL issuance");
    const tooLargeRequest = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_UPLOAD", applicationId, fileName: "large.jpg", contentType: "image/jpeg", sizeBytes: 2 * 1024 * 1024 + 1,
    });
    assertEquals(tooLargeRequest.status, 400, "oversized declaration is rejected before URL issuance");

    const forgedMime = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_UPLOAD", applicationId, fileName: "forged.jpg", contentType: "image/jpeg", sizeBytes: 4,
    });
    const forgedMimeBody = await forgedMime.json() as { signedUrl: string; token: string; path: string };
    const forgedMimeUrl = new URL(forgedMimeBody.signedUrl, supabaseUrl);
    forgedMimeUrl.searchParams.set("token", forgedMimeBody.token);
    const rejectedMimeUpload = await fetch(forgedMimeUrl, {
      method: "PUT", headers: { "Content-Type": "application/pdf" }, body: new Uint8Array([1, 2, 3, 4]),
    });
    assert(!rejectedMimeUpload.ok, "Storage bucket must enforce MIME independently of the client declaration");
    await rejectedMimeUpload.body?.cancel();

    const undersizedDeclaration = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_UPLOAD", applicationId, fileName: "oversized.jpg", contentType: "image/jpeg", sizeBytes: 4,
    });
    const oversizedBody = await undersizedDeclaration.json() as { signedUrl: string; token: string };
    const oversizedUrl = new URL(oversizedBody.signedUrl, supabaseUrl);
    oversizedUrl.searchParams.set("token", oversizedBody.token);
    const rejectedOversizedUpload = await fetch(oversizedUrl, {
      method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: new Uint8Array(2 * 1024 * 1024 + 1),
    });
    assert(!rejectedOversizedUpload.ok, "Storage bucket must enforce the 2 MiB limit independently of the client declaration");
    await rejectedOversizedUpload.body?.cancel();

    await commit([createWrite(`ownerApplications/${applicationId}`, {
      uid: applicant.uid,
      status: "PENDING",
      documentPaths: [uploadBody.path],
      venueDraft: { photoPaths: [] },
    })]);

    const privateRead = await fetch(`${supabaseUrl}/storage/v1/object/owner-docs/${uploadBody.path}`);
    assert(!privateRead.ok, "private object must not be readable without a signed URL");
    await privateRead.body?.cancel();

    const ownDownload = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_DOWNLOAD", applicationId, path: uploadBody.path,
    });
    assertEquals(ownDownload.status, 200);
    const ownDownloadBody = await ownDownload.json() as { signedUrl: string; expiresInSeconds: number };
    assertEquals(ownDownloadBody.expiresInSeconds, 300);
    const download = await fetch(ownDownloadBody.signedUrl);
    assert(download.ok, "applicant should fetch a referenced private document using the signed URL");
    assertEquals(new Uint8Array(await download.arrayBuffer()), new Uint8Array([1, 2, 3, 4]));

    const foreignDownload = await callOwnerDocs(other.idToken, {
      action: "SIGN_DOWNLOAD", applicationId, path: uploadBody.path,
    });
    assertEquals(foreignDownload.status, 403, "another applicant cannot get a signed URL");
    const adminDownload = await callOwnerDocs(admin.idToken, {
      action: "SIGN_DOWNLOAD", applicationId, path: uploadBody.path,
    });
    assertEquals(adminDownload.status, 200, "verified admin can get a signed URL");
    const unreferenced = await callOwnerDocs(applicant.idToken, {
      action: "SIGN_DOWNLOAD", applicationId, path: `${applicant.uid}/${applicationId}/not-referenced.jpg`,
    });
    assertEquals(unreferenced.status, 404, "unreferenced paths are never signed");

    const expiredUrl = await createStorageSignedUrl(uploadBody.path, 1);
    await new Promise((resolve) => setTimeout(resolve, 1_500));
    const expiredRead = await fetch(expiredUrl);
    assert(!expiredRead.ok, "Storage must reject an expired signed URL");
    await expiredRead.body?.cancel();

    await cleanupStorageObject(uploadBody.path);
    await commit([
      deleteWrite(`ownerApplications/${applicationId}`),
      deleteWrite(`users/${applicant.uid}`),
      deleteWrite(`users/${other.uid}`),
      deleteWrite(`users/${admin.uid}`),
    ]);
  },
});

async function createFirebaseUser(appRole: "PLAYER" | "ADMIN") {
  const email = `owner-docs-${crypto.randomUUID()}@example.test`;
  const password = "LocalTest123!";
  const created = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-test`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  assert(created.ok, `Auth Emulator sign up failed (${created.status})`);
  const first = await created.json() as { localId: string; idToken: string };
  const custom = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:update?key=local-test`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken: first.idToken, customAttributes: JSON.stringify({ appRole }), returnSecureToken: true }),
  });
  assert(custom.ok, `Auth Emulator claim setup failed (${custom.status})`);
  const signIn = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=local-test`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  assert(signIn.ok, `Auth Emulator sign in failed (${signIn.status})`);
  const authenticated = await signIn.json() as { localId: string; idToken: string };
  return { uid: authenticated.localId, idToken: authenticated.idToken };
}

async function seedUser(uid: string, role: "PLAYER" | "ADMIN", ownerStatus: "NONE") {
  await commit([createWrite(`users/${uid}`, { role, ownerStatus, accountStatus: "ACTIVE" })]);
}

async function callOwnerDocs(idToken: string, body: Record<string, unknown>): Promise<Response> {
  return await handleOwnerDocs(new Request("http://local.test/functions/v1/owner-docs", {
    method: "POST",
    headers: { Authorization: `Bearer ${idToken}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }));
}

async function createStorageSignedUrl(path: string, expiresIn: number): Promise<string> {
  const response = await fetch(`${supabaseUrl}/storage/v1/object/sign/owner-docs/${encodePath(path)}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey!, "Content-Type": "application/json" },
    body: JSON.stringify({ expiresIn }),
  });
  assert(response.ok, `Storage signing failed (${response.status})`);
  const body = await response.json() as { signedURL?: string; signedUrl?: string };
  return new URL(body.signedURL ?? body.signedUrl ?? "", supabaseUrl).toString();
}

async function cleanupStorageObject(path: string) {
  const response = await fetch(`${supabaseUrl}/storage/v1/object/owner-docs`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey!, "Content-Type": "application/json" },
    body: JSON.stringify({ prefixes: [path] }),
  });
  assert(response.ok, `Storage cleanup failed (${response.status})`);
  await response.body?.cancel();
}

function encodePath(path: string): string { return path.split("/").map(encodeURIComponent).join("/"); }
