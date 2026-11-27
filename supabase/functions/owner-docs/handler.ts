import { HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as firestore from "../_shared/firestore.ts";
import { publishApprovedVenuePhotos } from "../_shared/venue-photo-publication.ts";
import { applicationPurpose, requireApprovedApplicant } from "../_shared/owner-application-purpose.ts";

const ALLOWED_MIME = new Set(["image/jpeg", "image/png", "image/webp"]);
const MAX_BYTES = 2 * 1024 * 1024;
const BUCKET = "owner-docs";

export async function handleOwnerDocs(req: Request): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  try {
    const user = await requireUser(req);
    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    switch (body.action) {
      case "SIGN_UPLOAD": return await signUpload(user.uid, user.appRole, body);
      case "SIGN_VENUE_UPLOAD": return await signVenueUpload(user.uid, user.appRole, body);
      case "PUBLISH_VENUE_PHOTOS": return await publishVenuePhotos(user.uid,user.appRole,body);
      case "SIGN_DOWNLOAD": return await signDownload(user.uid, user.appRole, body);
      case "DELETE_STAGED": return await deleteStaged(user.uid, body);
      default: throw new HttpError(400, "INVALID_ACTION", "Thao tác tệp không hợp lệ");
    }
  } catch (error) {
    return toErrorResponse(error);
  }
}

async function publishVenuePhotos(uid:string,role:string,body:Record<string,unknown>):Promise<Response> {
  const venueId=string(body.venueId);
  if(!venueId || venueId.includes("/")) throw new HttpError(400,"INVALID_VENUE","Cơ sở không hợp lệ");
  const venue=await firestore.get(`venues/${venueId}`);
  const user=await firestore.get(`users/${uid}`);
  if(role!=="OWNER" || user?.role!=="OWNER" || user.ownerStatus!=="APPROVED" || venue?.ownerId!==uid || venue.status==="LOCKED") throw new HttpError(403,"FORBIDDEN","Không có quyền sửa ảnh cơ sở");
  const original=Array.isArray(venue.photoPaths)?venue.photoPaths as string[]:[];
  const legacy=original.filter(path=>path.split("/").length===3 && !path.startsWith("http"));
  const application=await firestore.get(`ownerApplications/${venueId}`);
  if(legacy.length && (application?.uid!==uid || application.status!=="APPROVED")) throw new HttpError(409,"APPLICATION_CONFLICT","Không xác minh được nguồn ảnh cơ sở");
  const documents=Array.isArray(application?.documentPaths)?application!.documentPaths as string[]:[];
  const published=await publishApprovedVenuePhotos(uid,venueId,legacy,documents);
  const paths=Object.fromEntries(legacy.map((path,index)=>[path,published[index]]));
  await firestore.runTransaction(async tx=>{
    const current=await tx.get(`venues/${venueId}`);
    const owner=await tx.get(`users/${uid}`);
    if(current?.ownerId!==uid || current.status==="LOCKED" || owner?.role!=="OWNER" || owner.ownerStatus!=="APPROVED" || owner.accountStatus!=="ACTIVE" || owner.deletionRequestedAt!=null) throw new HttpError(403,"FORBIDDEN","Quyền quản lý đã thay đổi");
    if(JSON.stringify(current.photoPaths)!==JSON.stringify(original)) throw new HttpError(409,"VENUE_CONFLICT","Ảnh cơ sở vừa thay đổi, hãy tải lại");
    if(legacy.length) tx.write(firestore.updateWrite(`venues/${venueId}`,{photoPaths:original.map(path=>paths[path]??path)}));
  });
  return json(200,{paths});
}

async function signVenueUpload(uid: string, role: string, body: Record<string, unknown>): Promise<Response> {
  const venueId = string(body.venueId);
  const contentType = string(body.contentType).toLowerCase();
  const size = body.sizeBytes;
  if (!venueId || venueId.includes("/") || !ALLOWED_MIME.has(contentType) || typeof size !== "number" || !Number.isSafeInteger(size) || size <= 0 || size > MAX_BYTES) {
    throw new HttpError(400, "INVALID_FILE", "Chọn ảnh JPEG, PNG hoặc WebP tối đa 2 MB");
  }
  const user = await firestore.get(`users/${uid}`);
  const venue = await firestore.get(`venues/${venueId}`);
  if (role !== "OWNER" || user?.role !== "OWNER" || user.ownerStatus !== "APPROVED" || user.accountStatus !== "ACTIVE" ||
      user.deletionRequestedAt != null || venue?.ownerId !== uid || venue.status === "LOCKED") {
    throw new HttpError(403, "FORBIDDEN", "Không có quyền tải ảnh cơ sở này");
  }
  const extension = contentType === "image/jpeg" ? "jpg" : contentType === "image/png" ? "png" : "webp";
  const path = `${venueId}/${crypto.randomUUID()}.${extension}`;
  const signed = await storageRequest(`/object/upload/sign/venue-photos/${encodePath(path)}`, {method:"POST",body:JSON.stringify({upsert:false})});
  const token = string(signed.token);
  if (!token) throw new HttpError(503,"STORAGE_ERROR","Không tạo được quyền tải ảnh");
  return json(200,{path,token});
}

async function signUpload(uid: string, role: string | null, body: Record<string, unknown>): Promise<Response> {
  const applicationId = string(body.applicationId);
  const fileName = string(body.fileName);
  const contentType = string(body.contentType).toLowerCase();
  const sizeBytes = body.sizeBytes;
  if (!/^[0-9a-f-]{36}$/i.test(applicationId) || !/^[\w.-]{1,100}$/.test(fileName)) {
    throw new HttpError(400, "INVALID_UPLOAD", "Thông tin tệp không hợp lệ");
  }
  if (!ALLOWED_MIME.has(contentType) || typeof sizeBytes !== "number" || sizeBytes <= 0 || sizeBytes > MAX_BYTES) {
    throw new HttpError(400, "INVALID_FILE", "Chỉ nhận JPEG, PNG hoặc WebP tối đa 2 MB mỗi tệp");
  }
  const user = await firestore.get(`users/${uid}`);
  const purpose = applicationPurpose(body.purpose);
  const app = await firestore.get(`ownerApplications/${applicationId}`);
  if (purpose === "VENUE_ADDITION") {
    if (role !== "OWNER") throw new HttpError(403,"ROLE_NOT_ALLOWED","Cần quyền chủ sân");
    requireApprovedApplicant(user);
    if (app && (app.uid !== uid || applicationPurpose(app.purpose) !== purpose)) throw new HttpError(403,"FORBIDDEN","Không có quyền sửa hồ sơ này");
    if (app && app.status !== "REJECTED") throw new HttpError(409,"APPLICATION_NOT_EDITABLE","Hồ sơ đang xử lý hoặc đã được duyệt");
  } else {
  if (!user || user.role !== "PLAYER") throw new HttpError(403, "ROLE_NOT_ALLOWED", "Tài khoản không thể tải hồ sơ");
  if (app && applicationPurpose(app.purpose) !== purpose) throw new HttpError(403,"FORBIDDEN","Loại hồ sơ không khớp");
  if (user.ownerStatus === "NONE") {
    if (app) throw new HttpError(409, "APPLICATION_CONFLICT", "Mã hồ sơ đã được sử dụng");
  } else if (user.ownerStatus === "REJECTED") {
    if (app?.uid !== uid || app.status !== "REJECTED") throw new HttpError(409, "APPLICATION_CONFLICT", "Chỉ có thể tải tệp cho hồ sơ bị từ chối hiện hành");
  } else {
    throw new HttpError(409, "APPLICATION_NOT_EDITABLE", "Hồ sơ đang được xét duyệt hoặc đã được duyệt");
  }
  }
  const extension = contentType === "image/jpeg" ? "jpg" : contentType === "image/png" ? "png" : "webp";
  const path = `${uid}/${applicationId}/${crypto.randomUUID()}.${extension}`;
  const signed = await storageRequest(`/object/upload/sign/${BUCKET}/${encodePath(path)}`, {
    method: "POST",
    body: JSON.stringify({ upsert: false }),
  });
  const token = string(signed.token);
  const signedUrl = string(signed.signedURL ?? signed.signedUrl ?? signed.url);
  if (!token || !signedUrl) throw new HttpError(503, "STORAGE_ERROR", "Không tạo được quyền tải tệp, vui lòng thử lại");
  return json(200, { path, token, signedUrl, contentType });
}

async function signDownload(uid: string, appRole: string, body: Record<string, unknown>): Promise<Response> {
  const applicationId = string(body.applicationId);
  const path = string(body.path);
  if (!applicationId || !path || path.split("/").length !== 3 || path.includes("..")) {
    throw new HttpError(403, "FORBIDDEN", "Không có quyền xem giấy tờ này");
  }
  const app = await firestore.get(`ownerApplications/${applicationId}`);
  if (!app) throw new HttpError(404, "APPLICATION_NOT_FOUND", "Không tìm thấy hồ sơ");
  if (!path.startsWith(`${app.uid}/${applicationId}/`)) throw new HttpError(403, "FORBIDDEN", "Không có quyền xem giấy tờ này");
  const isApplicant = app.uid === uid;
  const caller = appRole === "ADMIN" ? await firestore.get(`users/${uid}`) : null;
  const isAdmin = appRole === "ADMIN" && caller?.role === "ADMIN";
  if (!isApplicant && !isAdmin) throw new HttpError(403, "FORBIDDEN", "Không có quyền xem giấy tờ này");
  const docs = Array.isArray(app.documentPaths) ? app.documentPaths : [];
  if (!docs.includes(path)) throw new HttpError(404, "DOCUMENT_NOT_FOUND", "Không tìm thấy giấy tờ trong hồ sơ");
  const signed = await storageRequest(`/object/sign/${BUCKET}/${encodePath(path)}`, {
    method: "POST",
    body: JSON.stringify({ expiresIn: 300 }),
  });
  const base = Deno.env.get("SUPABASE_URL")!;
  const signedUrl = string(signed.signedURL);
  if (!signedUrl) throw new HttpError(503, "STORAGE_ERROR", "Không tạo được đường dẫn tải tệp");
  // Reverse-proxied local deployments may have a private service URL and a client-facing URL.
  const publicBase = Deno.env.get("PUBLIC_STORAGE_URL") || base;
  return json(200, { signedUrl: absoluteStorageUrl(signedUrl, publicBase), expiresInSeconds: 300 });
}

async function deleteStaged(uid: string, body: Record<string, unknown>): Promise<Response> {
  const path = string(body.path);
  const parts = path.split("/");
  if (parts.length !== 3 || parts[0] !== uid || !parts[1] || !parts[2] || path.includes("..")) {
    throw new HttpError(403, "FORBIDDEN", "Không có quyền xóa tệp này");
  }
  const app = await firestore.get(`ownerApplications/${parts[1]}`);
  if (app?.uid && app.uid !== uid) throw new HttpError(403, "FORBIDDEN", "Không có quyền xóa tệp này");
  if (app) {
    const referenced = [
      ...(Array.isArray(app.documentPaths) ? app.documentPaths : []),
      ...(Array.isArray((app.venueDraft as Record<string, unknown> | undefined)?.photoPaths)
        ? (app.venueDraft as Record<string, unknown>).photoPaths as unknown[] : []),
    ];
    if (referenced.includes(path)) throw new HttpError(409, "FILE_REFERENCED", "Tệp đang được hồ sơ tham chiếu");
  }
  await storageRequest(`/object/${BUCKET}`, { method: "DELETE", body: JSON.stringify({ prefixes: [path] }) });
  return json(200, { deleted: true });
}

async function storageRequest(path: string, init: RequestInit): Promise<Record<string, unknown>> {
  const base = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !key) throw new Error("Thiếu cấu hình Supabase Storage server");
  const response = await fetch(`${base}/storage/v1${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${key}`, apikey: key, "Content-Type": "application/json", ...init.headers },
  });
  if (!response.ok) {
    if (response.status === 404) throw new HttpError(404, "FILE_NOT_FOUND", "Không tìm thấy tệp");
    throw new HttpError(response.status >= 500 ? 503 : response.status, "STORAGE_ERROR", "Không thể xử lý tệp, vui lòng thử lại");
  }
  if (response.status === 204) return {};
  return await response.json() as Record<string, unknown>;
}

function encodePath(path: string): string { return path.split("/").map(encodeURIComponent).join("/"); }
function string(value: unknown): string { return typeof value === "string" ? value : ""; }
function absoluteStorageUrl(value: string, base: string): string {
  const url = new URL(value, base);
  const storageOrigin = new URL(base).origin;
  if (url.origin === storageOrigin && !url.pathname.startsWith("/storage/v1/")) {
    url.pathname = `/storage/v1${url.pathname}`;
  }
  return url.toString();
}
function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
