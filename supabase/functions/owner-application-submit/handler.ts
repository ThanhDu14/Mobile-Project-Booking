import { HttpError, toErrorResponse, type VerifiedToken } from "../_shared/auth.ts";
import * as defaultAuth from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError, updateWrite, type FirestoreTransaction, type Write } from "../_shared/firestore.ts";

type OwnerStatus = "NONE" | "PENDING" | "APPROVED" | "REJECTED";

interface VenueDraftInput {
  name: string;
  address: string;
  districtId: string;
  location: { latitude: number; longitude: number };
  phone: string;
  courtCount: number;
  openTime: string;
  closeTime: string;
  photoPaths: string[];
}

interface SubmitInput {
  /** Firestore auto-ID allocated by the Android client before file upload; it is never an idempotency key. */
  applicationId: string;
  venueDraft: VenueDraftInput;
  representativeName: string;
  representativePhone: string;
  documentPaths: string[];
}

export interface OwnerApplicationSubmitDeps {
  auth: Pick<typeof defaultAuth, "requireUser">;
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Fail closed until T002/T009's server-side Storage verifier is implemented. */
  verifyUploadedFiles(input: { uid: string; applicationId: string; photoPaths: string[]; documentPaths: string[] }): Promise<void>;
}

const defaultDeps: OwnerApplicationSubmitDeps = {
  auth: defaultAuth,
  firestore: defaultFirestore,
  async verifyUploadedFiles() {
    fail(503, "UPLOAD_VERIFICATION_UNAVAILABLE", "Chưa thể xác minh tệp đã tải lên; hồ sơ chưa được gửi");
  },
};

function fail(status: number, code: string, message: string): never {
  throw new HttpError(status, code, message);
}

async function readBody(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    return body && typeof body === "object" && !Array.isArray(body) ? body as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function text(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function stringPaths(value: unknown): value is string[] {
  return Array.isArray(value) && value.length > 0 && value.every(text);
}

function validateInput(body: Record<string, unknown>, uid: string): SubmitInput {
  const id = body.applicationId;
  const venue = body.venueDraft as Record<string, unknown> | null;
  if (typeof id !== "string" || !/^[A-Za-z0-9]{20}$/.test(id) || !venue || typeof venue !== "object") {
    fail(400, "VALIDATION_ERROR", "Thông tin hồ sơ không hợp lệ");
  }
  const location = venue.location as Record<string, unknown> | null;
  const latitude = location?.latitude;
  const longitude = location?.longitude;
  const courtCount = venue.courtCount;
  const openTime = venue.openTime;
  const closeTime = venue.closeTime;
  const photoPaths = venue.photoPaths;
  const documentPaths = body.documentPaths;
  if (
    !text(venue.name) || !text(venue.address) || !text(venue.districtId) || !text(venue.phone) ||
    typeof latitude !== "number" || !Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
    typeof longitude !== "number" || !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
    typeof courtCount !== "number" || !Number.isInteger(courtCount) || courtCount < 1 ||
    typeof openTime !== "string" || !/^([01]\d|2[0-3])[0-5]\d$/.test(openTime) ||
    typeof closeTime !== "string" || !/^([01]\d|2[0-3])[0-5]\d$/.test(closeTime) ||
    !stringPaths(photoPaths) || !stringPaths(documentPaths) ||
    !text(body.representativeName) || !text(body.representativePhone)
  ) {
    fail(400, "VALIDATION_ERROR", "Vui lòng kiểm tra các trường bắt buộc trong hồ sơ");
  }
  const prefix = `${uid}/${id}/`;
  if ([...photoPaths, ...documentPaths].some((path) => !path.startsWith(prefix) || path.includes("..") || path.includes("\\"))) {
    fail(400, "VALIDATION_ERROR", "Đường dẫn tệp không thuộc hồ sơ này");
  }
  return {
    applicationId: id,
    venueDraft: {
      name: venue.name.trim(),
      address: venue.address.trim(),
      districtId: venue.districtId.trim(),
      location: { latitude, longitude },
      phone: venue.phone.trim(),
      courtCount,
      openTime,
      closeTime,
      photoPaths,
    },
    representativeName: (body.representativeName as string).trim(),
    representativePhone: (body.representativePhone as string).trim(),
    documentPaths,
  };
}

function applicationFields(uid: string, input: SubmitInput): Record<string, unknown> {
  return {
    uid,
    venueDraft: input.venueDraft,
    representativeName: input.representativeName,
    representativePhone: input.representativePhone,
    documentPaths: input.documentPaths,
  };
}

async function matching(transaction: FirestoreTransaction, uid: string, status: string) {
  return await transaction.query("ownerApplications", [
    { field: "uid", op: "EQUAL", value: uid },
    { field: "status", op: "EQUAL", value: status },
  ]);
}

function success(applicationId: string): Response {
  // T003-RESULT: success payload is deliberately minimal; client refreshes authoritative status.
  return Response.json({ applicationId });
}

export async function handleOwnerApplicationSubmit(
  req: Request,
  deps: OwnerApplicationSubmitDeps = defaultDeps,
): Promise<Response> {
  if (req.method !== "POST") {
    return Response.json({ error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } }, { status: 405 });
  }
  try {
    const user: VerifiedToken & { appRole: "PLAYER" | "OWNER" | "ADMIN" } = await deps.auth.requireUser(req);
    const input = validateInput(await readBody(req), user.uid);
    await deps.verifyUploadedFiles({
      uid: user.uid,
      applicationId: input.applicationId,
      photoPaths: input.venueDraft.photoPaths,
      documentPaths: input.documentPaths,
    });
    const applicationId = await deps.firestore.runTransaction(async (transaction) => {
      const account = await transaction.get(`users/${user.uid}`);
      if (!account || account.accountStatus === "LOCKED" || account.role !== "PLAYER") {
        fail(403, "NOT_AUTHORIZED", "Tài khoản không được phép nộp hồ sơ");
      }
      const ownerStatus = account.ownerStatus as OwnerStatus;
      if (!["NONE", "PENDING", "APPROVED", "REJECTED"].includes(ownerStatus)) {
        fail(409, "AMBIGUOUS_RESULT", "Trạng thái tài khoản chưa xác định");
      }
      if (ownerStatus === "APPROVED") fail(403, "NOT_AUTHORIZED", "Hồ sơ đã được duyệt");

      if (ownerStatus === "PENDING") {
        const pending = await matching(transaction, user.uid, "PENDING");
        if (pending.length !== 1) fail(409, "AMBIGUOUS_RESULT", "Không thể xác định hồ sơ đang chờ duyệt");
        return { result: pending[0].id, writes: [] as Write[] };
      }

      if (ownerStatus === "NONE") {
        const pending = await matching(transaction, user.uid, "PENDING");
        const rejected = await matching(transaction, user.uid, "REJECTED");
        if (pending.length > 0 || rejected.length > 0) {
          fail(409, "AMBIGUOUS_RESULT", "Trạng thái tài khoản và hồ sơ chưa đồng bộ");
        }
        const existing = await transaction.get(`ownerApplications/${input.applicationId}`);
        if (existing) fail(409, "CONFLICT", "Mã hồ sơ đã được sử dụng; hãy làm mới biểu mẫu");
        const writes = [
          createWrite(`ownerApplications/${input.applicationId}`, {
            ...applicationFields(user.uid, input),
            status: "PENDING",
          }, ["submittedAt"]),
          updateWrite(`users/${user.uid}`, { ownerStatus: "PENDING" }),
        ];
        return { result: input.applicationId, writes };
      }

      const rejected = await matching(transaction, user.uid, "REJECTED");
      if (rejected.length !== 1) fail(409, "AMBIGUOUS_RESULT", "Không thể xác định duy nhất hồ sơ bị từ chối");
      const current = rejected[0];
      if (current.id !== input.applicationId) fail(409, "CONFLICT", "Hồ sơ đã thay đổi; hãy làm mới trước khi nộp lại");
      const currentDocument = await transaction.get(`ownerApplications/${current.id}`);
      if (!currentDocument || currentDocument.uid !== user.uid || currentDocument.status !== "REJECTED") {
        fail(409, "CONFLICT", "Hồ sơ đã thay đổi; bản nháp được giữ để khôi phục");
      }
      const writes = [
        updateWrite(`ownerApplications/${current.id}`, {
          ...applicationFields(user.uid, input),
          status: "PENDING",
          rejectReason: null,
          reviewedBy: null,
          reviewedAt: null,
        }, ["submittedAt"]),
        updateWrite(`users/${user.uid}`, { ownerStatus: "PENDING" }),
      ];
      return { result: current.id, writes };
    });
    return success(applicationId);
  } catch (e) {
    if (e instanceof FirestoreError && e.code === "ABORTED") {
      return Response.json({ error: { code: "RETRYABLE_ERROR", message: "Có thay đổi đồng thời; hãy tải lại trạng thái" } }, { status: 503 });
    }
    return toErrorResponse(e);
  }
}
