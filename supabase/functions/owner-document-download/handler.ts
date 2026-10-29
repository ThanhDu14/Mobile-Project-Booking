import { HttpError, toErrorResponse, type VerifiedToken } from "../_shared/auth.ts";
import * as defaultAuth from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";

const SIGNED_URL_SECONDS = 300;
const OBJECT_PATH = /^[A-Za-z0-9_-]+\/[A-Za-z0-9]{20}\/[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/;

export interface OwnerDocumentDownloadDeps {
  auth: Pick<typeof defaultAuth, "requireUser">;
  firestore: Pick<typeof defaultFirestore, "get">;
  signUrl(path: string, expiresInSeconds: number): Promise<string>;
}

function fail(status: number, code: string, message: string): never {
  throw new HttpError(status, code, message);
}

async function readBody(req: Request): Promise<Record<string, unknown>> {
  try {
    const value = await req.json();
    return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

async function signStorageUrl(path: string, expiresInSeconds: number): Promise<string> {
  const baseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!baseUrl || !serviceKey) fail(503, "STORAGE_UNAVAILABLE", "Dịch vụ tài liệu hiện chưa khả dụng");
  const encodedPath = path.split("/").map(encodeURIComponent).join("/");
  const response = await fetch(`${baseUrl.replace(/\/$/, "")}/storage/v1/object/sign/owner-docs/${encodedPath}`, {
    method: "POST",
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ expiresIn: expiresInSeconds }),
  });
  if (!response.ok) {
    await response.body?.cancel();
    fail(response.status === 404 ? 404 : 503, "STORAGE_UNAVAILABLE", "Không thể tạo liên kết tải tài liệu");
  }
  const body = await response.json() as { signedURL?: unknown };
  if (typeof body.signedURL !== "string") fail(503, "STORAGE_UNAVAILABLE", "Dịch vụ tài liệu trả kết quả không hợp lệ");
  return new URL(body.signedURL.replace(/^\/+/, ""), `${baseUrl.replace(/\/$/, "")}/storage/v1/`).toString();
}

const defaultDeps: OwnerDocumentDownloadDeps = {
  auth: defaultAuth,
  firestore: defaultFirestore,
  signUrl: signStorageUrl,
};

export async function handleOwnerDocumentDownload(
  req: Request,
  deps: OwnerDocumentDownloadDeps = defaultDeps,
): Promise<Response> {
  if (req.method !== "POST") {
    return Response.json({ error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } }, { status: 405 });
  }
  try {
    const user: VerifiedToken & { appRole: "PLAYER" | "OWNER" | "ADMIN" } = await deps.auth.requireUser(req);
    const body = await readBody(req);
    const applicationId = body.applicationId;
    const path = body.path;
    if (typeof applicationId !== "string" || !/^[A-Za-z0-9]{20}$/.test(applicationId) ||
      typeof path !== "string" || !OBJECT_PATH.test(path) || !path.startsWith(`${user.uid}/${applicationId}/`)) {
      fail(400, "VALIDATION_ERROR", "Yêu cầu tài liệu không hợp lệ");
    }
    const application = await deps.firestore.get(`ownerApplications/${applicationId}`);
    const paths = application?.documentPaths;
    if (!application || application.uid !== user.uid || !Array.isArray(paths) || !paths.includes(path)) {
      fail(404, "NOT_FOUND", "Không tìm thấy tài liệu được yêu cầu");
    }
    const signedUrl = await deps.signUrl(path, SIGNED_URL_SECONDS);
    return Response.json({ signedUrl, expiresInSeconds: SIGNED_URL_SECONDS }, {
      headers: { "Cache-Control": "private, no-store" },
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
