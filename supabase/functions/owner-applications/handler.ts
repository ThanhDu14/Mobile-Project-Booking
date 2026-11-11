import { HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite, createWrite, FirestoreError } from "../_shared/firestore.ts";

export interface OwnerApplicationDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "latestOwnerApplication" | "runTransaction">;
  verifyObjects(paths: string[]): Promise<boolean>;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const TIME = /^(?:[01]\d|2[0-3])[0-5]\d$/;
const PHONE = /^(?:0\d{9,10}|\+84\d{9,10})$/;
const MIME = new Set(["image/jpeg", "image/png", "image/webp"]);
const ALLOWED_MIME = MIME;
const MAX_BYTES = 2 * 1024 * 1024;

type ApplicantDraft = {
  venueDraft: Record<string, unknown>;
  representativeName: string;
  representativePhone: string;
  documentPaths: string[];
};

export async function handleOwnerApplications(
  req: Request,
  deps: OwnerApplicationDeps = { firestore: defaultFirestore, verifyObjects: verifyStagedObjects },
): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  try {
    const user = await requireUser(req);
    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    if (body.action === "STATUS") return await readStatus(user.uid, deps.firestore);
    if (body.action === "SUBMIT") return await submit(user.uid, body, deps);
    throw new HttpError(400, "INVALID_ACTION", "Thao tác hồ sơ không hợp lệ");
  } catch (error) {
    if (error instanceof FirestoreError && error.code === "ABORTED") {
      return json(409, { error: { code: "APPLICATION_CONFLICT", message: "Hồ sơ vừa thay đổi. Hãy làm mới trạng thái." } });
    }
    return toErrorResponse(error);
  }
}

async function readStatus(uid: string, firestore: OwnerApplicationDeps["firestore"]): Promise<Response> {
  const user = await firestore.get(`users/${uid}`);
  if (!user) throw new HttpError(404, "USER_NOT_FOUND", "Không tìm thấy tài khoản");
  const ownerStatus = enumValue(user.ownerStatus, ["NONE", "PENDING", "APPROVED", "REJECTED"] as const);
  const role = enumValue(user.role, ["PLAYER", "OWNER"] as const);
  if (!ownerStatus || !role) throw new HttpError(409, "OWNER_STATE_INVALID", "Trạng thái tài khoản chưa nhất quán");
  const expectedRole = ownerStatus === "APPROVED" ? "OWNER" : "PLAYER";
  if (role !== expectedRole) throw new HttpError(409, "OWNER_STATE_INVALID", "Vai trò và trạng thái chủ sân chưa được đồng bộ");
  const app = await firestore.latestOwnerApplication(uid);
  if (app && app.uid !== uid) throw new HttpError(409, "OWNER_STATE_INVALID", "Hồ sơ không khớp tài khoản");
  const appStatus = app?.status;
  if (ownerStatus === "PENDING" && appStatus !== "PENDING" || ownerStatus === "REJECTED" && appStatus !== "REJECTED" || ownerStatus === "APPROVED" && appStatus !== "APPROVED") {
    throw new HttpError(409, "OWNER_STATE_INVALID", "Trạng thái hồ sơ chưa được đồng bộ");
  }
  const { __documentId, ...application } = app ?? {};
  return json(200, {
    ownerStatus,
    role,
    application: app ? { applicationId: __documentId, ...application } : null,
  });
}

async function submit(
  uid: string,
  body: Record<string, unknown>,
  deps: OwnerApplicationDeps,
): Promise<Response> {
  const firestore = deps.firestore;
  const applicationId = string(body.applicationId);
  if (!UUID.test(applicationId)) throw new HttpError(400, "INVALID_APPLICATION_ID", "Mã hồ sơ không hợp lệ");
  const draft = parseDraft(body, uid, applicationId);
  if (!await deps.verifyObjects([...(draft.venueDraft.photoPaths as string[]), ...draft.documentPaths])) {
    throw new HttpError(400, "UPLOADED_FILES_REQUIRED", "Ảnh hoặc giấy tờ chưa tải lên đầy đủ");
  }

  try {
    const result = await firestore.runTransaction(async (tx) => {
      // All transaction reads happen before writes.
      const districtId = String(draft.venueDraft.districtId);
      const [user, app, district] = await Promise.all([
        tx.get(`users/${uid}`),
        tx.get(`ownerApplications/${applicationId}`),
        tx.get(`districts/${districtId}`),
      ]);
      if (!user) throw new HttpError(404, "USER_NOT_FOUND", "Không tìm thấy tài khoản");
      if (user.role !== "PLAYER" && user.role !== "OWNER") {
        throw new HttpError(403, "ROLE_NOT_ALLOWED", "Tài khoản không thể đăng ký hồ sơ chủ sân");
      }
      if (district?.isActive !== true) throw new HttpError(400, "INVALID_DISTRICT", "Quận/huyện không còn hoạt động");

      if (user.ownerStatus === "PENDING" || user.ownerStatus === "APPROVED") {
        const roleMatchesStatus = user.ownerStatus === "PENDING" ? user.role === "PLAYER" : user.role === "OWNER";
        if (roleMatchesStatus && app?.uid === uid && payloadMatches(app, draft) && app.status === user.ownerStatus) {
          return { ownerStatus: user.ownerStatus, alreadyCommitted: true };
        }
        throw new HttpError(409, "APPLICATION_CONFLICT", "Đã có hồ sơ đang xử lý hoặc tài khoản đã được duyệt");
      }

      if (user.ownerStatus === "NONE") {
        if (user.role !== "PLAYER") throw new HttpError(403, "ROLE_NOT_ALLOWED", "Chỉ người chơi được đăng ký");
        if (app) throw new HttpError(409, "APPLICATION_CONFLICT", "Mã hồ sơ đã được sử dụng; hãy làm mới trạng thái");
        tx.write(createWrite(`ownerApplications/${applicationId}`, {
          uid,
          ...draft,
          status: "PENDING",
        }, ["submittedAt"]));
        tx.write(updateWrite(`users/${uid}`, { ownerStatus: "PENDING" }));
        return { ownerStatus: "PENDING", alreadyCommitted: false };
      }

      if (user.ownerStatus === "REJECTED") {
        if (user.role !== "PLAYER") throw new HttpError(403, "ROLE_NOT_ALLOWED", "Chỉ người chơi được nộp lại hồ sơ bị từ chối");
        if (!app || app.uid !== uid || app.status !== "REJECTED") {
          throw new HttpError(409, "APPLICATION_CONFLICT", "Không tìm thấy hồ sơ bị từ chối hiện hành");
        }
        const write = updateWrite(`ownerApplications/${applicationId}`, {
          ...draft,
          status: "PENDING",
        }, ["submittedAt"]);
        // A resubmission clears the previous server-owned reason in the same transaction.
        if (write.updateMask) write.updateMask.fieldPaths.push("rejectReason");
        tx.write(write);
        tx.write(updateWrite(`users/${uid}`, { ownerStatus: "PENDING" }));
        return { ownerStatus: "PENDING", alreadyCommitted: false };
      }
      throw new HttpError(409, "APPLICATION_CONFLICT", "Trạng thái đăng ký hiện tại không cho phép gửi hồ sơ");
    });
    return json(200, { ...result, applicationId });
  } catch (error) {
    if (error instanceof FirestoreError && error.code === "ALREADY_EXISTS") {
      // A competing request may have committed. Resolve it from trusted state.
      const current = await firestore.get(`ownerApplications/${applicationId}`);
      const user = await firestore.get(`users/${uid}`);
      if (current?.uid === uid && user?.ownerStatus === current.status && payloadMatches(current, draft)) {
        return json(200, { ownerStatus: current.status, alreadyCommitted: true, applicationId });
      }
      throw new HttpError(409, "APPLICATION_CONFLICT", "Hồ sơ đã thay đổi bởi một yêu cầu khác");
    }
    throw error;
  }
}

function parseDraft(body: Record<string, unknown>, uid: string, applicationId: string): ApplicantDraft {
  const venue = object(body.venueDraft);
  const requiredStrings = ["name", "address", "districtId", "phone", "openTime", "closeTime"];
  for (const field of requiredStrings) {
    if (!string(venue[field]).trim()) throw new HttpError(400, "INVALID_APPLICATION", `Thiếu thông tin ${field}`);
  }
  const courtCount = venue.courtCount;
  const location = object(venue.location);
  const latitude = location.latitude;
  const longitude = location.longitude;
  if (typeof courtCount !== "number" || !Number.isInteger(courtCount) || courtCount <= 0) {
    throw new HttpError(400, "INVALID_APPLICATION", "Số sân phải là số nguyên dương");
  }
  if (typeof latitude !== "number" || latitude < -90 || latitude > 90 || typeof longitude !== "number" || longitude < -180 || longitude > 180) {
    throw new HttpError(400, "INVALID_APPLICATION", "Vị trí bản đồ không hợp lệ");
  }
  if (!TIME.test(string(venue.openTime)) || !TIME.test(string(venue.closeTime)) || string(venue.closeTime) <= string(venue.openTime)) {
    throw new HttpError(400, "INVALID_APPLICATION", "Giờ mở cửa hoặc đóng cửa không hợp lệ");
  }
  if (!PHONE.test(string(venue.phone).replace(/\s/g, ""))) throw new HttpError(400, "INVALID_APPLICATION", "Số điện thoại cơ sở không hợp lệ");
  const representativeName = string(body.representativeName).trim();
  const representativePhone = string(body.representativePhone).replace(/\s/g, "");
  if (!representativeName || !PHONE.test(representativePhone)) throw new HttpError(400, "INVALID_APPLICATION", "Thông tin người đại diện không hợp lệ");
  const photoPaths = pathArray(venue.photoPaths, uid, applicationId, "venue photo");
  const documentPaths = pathArray(body.documentPaths, uid, applicationId, "document");
  if (photoPaths.length < 1 || photoPaths.length > 5 || documentPaths.length < 1) {
    throw new HttpError(400, "INVALID_APPLICATION", "Cần ít nhất một ảnh cơ sở và một giấy tờ; tối đa 5 ảnh cơ sở");
  }
  return {
    venueDraft: {
      name: string(venue.name).trim(),
      address: string(venue.address).trim(),
      districtId: string(venue.districtId).trim(),
      location: { latitude, longitude },
      phone: string(venue.phone).trim(),
      courtCount,
      openTime: string(venue.openTime),
      closeTime: string(venue.closeTime),
      photoPaths,
    },
    representativeName,
    representativePhone,
    documentPaths,
  };
}

function pathArray(value: unknown, uid: string, applicationId: string, label: string): string[] {
  if (!Array.isArray(value) || value.some((p) => typeof p !== "string" || !p.startsWith(`${uid}/${applicationId}/`) || p.includes(".."))) {
    throw new HttpError(400, "INVALID_OBJECT_PATH", `Đường dẫn ${label} không hợp lệ`);
  }
  return [...new Set(value as string[])];
}

function payloadMatches(current: Record<string, unknown>, draft: ApplicantDraft): boolean {
  return stable(current.venueDraft) === stable(draft.venueDraft)
    && current.representativeName === draft.representativeName
    && current.representativePhone === draft.representativePhone
    && stable(current.documentPaths) === stable(draft.documentPaths);
}

function stable(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.entries(value as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => `${JSON.stringify(k)}:${stable(v)}`).join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

function enumValue<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  return typeof value === "string" && allowed.includes(value as T) ? value as T : null;
}
function string(value: unknown): string { return typeof value === "string" ? value : ""; }
function object(value: unknown): Record<string, unknown> { return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

async function verifyStagedObjects(paths: string[]): Promise<boolean> {
  const base = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !key) throw new Error("Thiếu cấu hình Supabase Storage server");
  for (const path of paths) {
    const encoded = path.split("/").map(encodeURIComponent).join("/");
    // Supabase storage-api route is /object/info/authenticated/{bucket}/* or /object/info/{bucket}/*
    let response = await fetch(`${base}/storage/v1/object/info/authenticated/owner-docs/${encoded}`, {
      headers: { Authorization: `Bearer ${key}`, apikey: key },
    });
    if (!response.ok) {
      response = await fetch(`${base}/storage/v1/object/info/owner-docs/${encoded}`, {
        headers: { Authorization: `Bearer ${key}`, apikey: key },
      });
    }
    if (!response.ok) {
      // Fallback: check HEAD on authenticated object route
      const head = await fetch(`${base}/storage/v1/object/authenticated/owner-docs/${encoded}`, {
        method: "HEAD",
        headers: { Authorization: `Bearer ${key}`, apikey: key },
      });
      if (!head.ok) {
        console.error(`[verifyStagedObjects] failed for ${path}: infoStatus=${response.status}, headStatus=${head.status}`);
        return false;
      }
      const headSize = Number(head.headers.get("content-length") ?? 0);
      const headMime = String(head.headers.get("content-type") ?? "").toLowerCase();
      if (!ALLOWED_MIME.has(headMime) || headSize <= 0 || headSize > MAX_BYTES) {
        console.error(`[verifyStagedObjects] invalid head metadata for ${path}: mime=${headMime}, size=${headSize}`);
        return false;
      }
      continue;
    }
    const info = await response.json() as {
      size?: number;
      content_type?: string;
      metadata?: { size?: number; mimetype?: string; contentType?: string };
    };
    const size = Number(info.size ?? info.metadata?.size ?? 0);
    const mime = String(info.content_type ?? info.metadata?.mimetype ?? info.metadata?.contentType ?? "").toLowerCase();
    if (!ALLOWED_MIME.has(mime) || size <= 0 || size > MAX_BYTES) {
      console.error(`[verifyStagedObjects] invalid info metadata for ${path}: mime=${mime}, size=${size}`);
      return false;
    }
  }
  return true;
}
