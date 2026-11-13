import { assertEquals } from "jsr:@std/assert@1.0.19";
import { commit, createWrite, updateWrite } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken } from "../_shared/testing.ts";
import { handleOwnerDocs } from "./handler.ts";

const uid = "owner-docs-test-user";
const otherUid = "owner-docs-other-user";
const applicationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const documentPath = `${uid}/${applicationId}/license.jpg`;
const token = (sub = uid, appRole = "PLAYER") => emulatorToken({ sub, appRole });

Deno.test("owner-docs enforces applicant/admin paths and signs only authorized staged objects", async () => {
  const previousFetch = globalThis.fetch;
  Deno.env.set("SUPABASE_URL", "http://supabase.local");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "test-service-key");
  Deno.env.set("PUBLIC_STORAGE_URL", "http://127.0.0.1:54321");
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    if (!url.includes("/storage/v1/")) return await previousFetch(input, init);
    if (url.includes("/object/upload/sign/")) {
      return Response.json({ token: "upload-token", signedURL: "/object/upload/sign/owner-docs/test" });
    }
    if (url.includes("/object/sign/")) {
      return Response.json({ signedURL: "/object/sign/owner-docs/test?token=short-lived" });
    }
    return new Response(null, { status: 204 });
  };

  try {
    await clearEmulators();
    await commit([createWrite(`users/${uid}`, { role: "PLAYER", ownerStatus: "NONE", accountStatus: "ACTIVE" })]);
    const upload = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "SIGN_UPLOAD", applicationId, fileName: "license.jpg", contentType: "image/jpeg", sizeBytes: 1024 }),
    }));
    assertEquals(upload.status, 200);
    const uploadBody = await upload.json();
    assertEquals(String(uploadBody.path).startsWith(`${uid}/${applicationId}/`), true);
    assertEquals(uploadBody.token, "upload-token");

    const invalidMime = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "SIGN_UPLOAD", applicationId, fileName: "license.pdf", contentType: "application/pdf", sizeBytes: 1024 }),
    }));
    assertEquals(invalidMime.status, 400);
    await commit([updateWrite(`users/${uid}`, { role: "PLAYER", ownerStatus: "PENDING", accountStatus: "ACTIVE" })]);
    const pendingUpload = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "SIGN_UPLOAD", applicationId, fileName: "license.jpg", contentType: "image/jpeg", sizeBytes: 1024 }),
    }));
    assertEquals(pendingUpload.status, 409);

    await commit([
      createWrite(`ownerApplications/${applicationId}`, { uid, documentPaths: [documentPath], venueDraft: { photoPaths: [] }, status: "PENDING" }),
      createWrite(`users/${otherUid}`, { role: "PLAYER", ownerStatus: "NONE", accountStatus: "ACTIVE" }),
    ]);
    const ownDownload = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "SIGN_DOWNLOAD", applicationId, path: documentPath }),
    }));
    assertEquals(ownDownload.status, 200);
    assertEquals((await ownDownload.json()).expiresInSeconds, 300);

    const unreferenced = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "SIGN_DOWNLOAD", applicationId, path: `${uid}/${applicationId}/other.jpg` }),
    }));
    assertEquals(unreferenced.status, 404);
    const crossUser = await handleOwnerDocs(bearer(token(otherUid), {
      body: JSON.stringify({ action: "SIGN_DOWNLOAD", applicationId, path: documentPath }),
    }));
    assertEquals(crossUser.status, 403);

    const staleAdminClaim = await handleOwnerDocs(bearer(token(otherUid, "ADMIN"), {
      body: JSON.stringify({ action: "SIGN_DOWNLOAD", applicationId, path: documentPath }),
    }));
    assertEquals(staleAdminClaim.status, 403);
    await commit([updateWrite(`users/${otherUid}`, { role: "ADMIN", ownerStatus: "NONE", accountStatus: "ACTIVE" })]);
    const adminDownload = await handleOwnerDocs(bearer(token(otherUid, "ADMIN"), {
      body: JSON.stringify({ action: "SIGN_DOWNLOAD", applicationId, path: documentPath }),
    }));
    assertEquals(adminDownload.status, 200);
    assertEquals(String((await adminDownload.json()).signedUrl).startsWith("http://127.0.0.1:54321/storage/v1/"), true);

    const referencedDelete = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "DELETE_STAGED", path: documentPath }),
    }));
    assertEquals(referencedDelete.status, 409);
    const unreferencedDelete = await handleOwnerDocs(bearer(token(), {
      body: JSON.stringify({ action: "DELETE_STAGED", path: `${uid}/${applicationId}/orphan.jpg` }),
    }));
    assertEquals(unreferencedDelete.status, 200);
    const crossUserDelete = await handleOwnerDocs(bearer(token(otherUid), {
      body: JSON.stringify({ action: "DELETE_STAGED", path: `${uid}/${applicationId}/orphan.jpg` }),
    }));
    assertEquals(crossUserDelete.status, 403);
  } finally {
    globalThis.fetch = previousFetch;
    Deno.env.delete("SUPABASE_URL");
    Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
    Deno.env.delete("PUBLIC_STORAGE_URL");
  }
});

