import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite, FirestoreError } from "../_shared/firestore.ts";
import { mergeClaims as defaultMergeClaims } from "../_shared/identity.ts";
import { reconcileAdminClaims } from "../_shared/admin-claims.ts";

export interface AdminReviewDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  mergeClaims: (uid: string, claims: Record<string, unknown>) => Promise<void>;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function normalize(s: string): string {
  return s
    .replace(/[đĐ]/g, "d")
    .normalize("NFD")
    .replace(/\p{Mn}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function generateKeywords(name: string): string[] {
  const words = normalize(name).split(" ").filter(Boolean);
  const out = new Set<string>();
  for (const w of words) {
    if (w.length === 1) out.add(w);
    else {
      for (let k = 2; k <= Math.min(w.length, 15); k++) {
        out.add(w.slice(0, k));
      }
    }
  }
  return [...out].sort().slice(0, 60);
}

export function geohash(lat: number, lng: number, precision = 10): string {
  const base32 = "0123456789bcdefghjkmnpqrstuvwxyz";
  const range = [[-90, 90], [-180, 180]];
  let hash = "";
  let bit = 0;
  let ch = 0;
  let even = true;
  while (hash.length < precision) {
    const [r, v] = even ? [range[1], lng] : [range[0], lat];
    const mid = (r[0] + r[1]) / 2;
    if (v >= mid) {
      ch = (ch << 1) | 1;
      r[0] = mid;
    } else {
      ch = (ch << 1);
      r[1] = mid;
    }
    even = !even;
    if (++bit === 5) {
      hash += base32[ch];
      bit = 0;
      ch = 0;
    }
  }
  return hash;
}

export async function handleAdminReviewApplication(
  req: Request,
  deps: AdminReviewDeps = { firestore: defaultFirestore, mergeClaims: defaultMergeClaims },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const user = await requireUser(req);
    requireRole(user, "ADMIN");
    requireCurrentAdmin(user);

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const applicationId = typeof body.applicationId === "string" ? body.applicationId.trim() : "";
    const decision = typeof body.decision === "string" ? body.decision.trim().toUpperCase() : "";
    const rejectReason = typeof body.rejectReason === "string" ? body.rejectReason.trim() : "";
    const expectedSubmittedAt = body.expectedSubmittedAt;
    const targetStatus = decision === "APPROVE" ? "APPROVED" : "REJECTED";

    if (!applicationId || applicationId.includes("/")) {
      throw new HttpError(400, "INVALID_APPLICATION_ID", "Mã hồ sơ không hợp lệ");
    }
    if (body.action === "RECOVER_CLAIMS") {
      const application = await deps.firestore.get(`ownerApplications/${applicationId}`);
      if (!application) throw new HttpError(404,"APPLICATION_NOT_FOUND","Không tìm thấy hồ sơ");
      if (application.status !== "APPROVED" && application.status !== "REJECTED") throw new HttpError(409,"APPLICATION_CONFLICT","Hồ sơ chưa có quyết định để phục hồi quyền");
      const uid = String(application.uid ?? application.applicantId ?? "");
      if (!uid) throw new HttpError(400,"INVALID_APPLICATION","Hồ sơ thiếu người nộp");
      await reconcileAdminClaims(uid,deps.firestore.get,deps.mergeClaims);
      return json(200,{success:true,applicationId,status:application.status,claimsSynced:true});
    }
    if (decision !== "APPROVE" && decision !== "REJECT") {
      throw new HttpError(400, "INVALID_DECISION", "Quyết định phải là APPROVE hoặc REJECT");
    }
    if (decision === "REJECT" && !rejectReason) {
      throw new HttpError(400, "INVALID_REASON", "Lý do từ chối không được để trống");
    }

    const nowIso = new Date().toISOString();
    let targetApplicantUid = "";

    const outcome = await deps.firestore.runTransaction(async (tx) => {
      const app = await tx.get(`ownerApplications/${applicationId}`);
      if (!app) {
        throw new HttpError(404, "APPLICATION_NOT_FOUND", "Không tìm thấy hồ sơ");
      }

      const applicantUid = (app.uid ?? app.applicantId) as string | undefined;
      if (!applicantUid) {
        throw new HttpError(400, "INVALID_APPLICATION", "Hồ sơ không có thông tin người nộp");
      }
      targetApplicantUid = applicantUid;
      if (app.status === "PENDING" && (typeof expectedSubmittedAt !== "string" || !Number.isFinite(Date.parse(expectedSubmittedAt)))) {
        throw new HttpError(400, "MISSING_APPLICATION_VERSION", "Vui lòng tải lại hồ sơ trước khi quyết định");
      }
      if (expectedSubmittedAt != null && new Date(String(app.submittedAt)).getTime() !== new Date(String(expectedSubmittedAt)).getTime()) {
        throw new HttpError(409, "APPLICATION_CONFLICT", "Hồ sơ đã được nộp lại. Vui lòng tải trạng thái mới.");
      }

      const applicant = await tx.get(`users/${applicantUid}`);
      if (!applicant) {
        throw new HttpError(404, "APPLICANT_NOT_FOUND", "Không tìm thấy tài khoản người nộp hồ sơ");
      }

      if (app.status !== "PENDING") {
        if (app.status === targetStatus && app.reviewedBy === user.uid &&
            (decision !== "REJECT" || app.rejectReason === rejectReason)) {
          return { success: true, status: targetStatus, alreadyCommitted: true, applicationId };
        }
        throw new HttpError(409, "APPLICATION_CONFLICT", "Hồ sơ đã được xử lý bởi quản trị viên khác");
      }

      const notificationId = crypto.randomUUID();

      if (decision === "APPROVE") {
        tx.write(updateWrite(`ownerApplications/${applicationId}`, {
          status: "APPROVED",
          reviewedBy: user.uid,
          reviewedAt: nowIso,
        }));

        tx.write(updateWrite(`users/${applicantUid}`, {
          role: "OWNER",
          ownerStatus: "APPROVED",
          updatedAt: nowIso,
        }));

        const draft = (app.venueDraft ?? {}) as Record<string, unknown>;
        const venueName = typeof draft.name === "string" ? draft.name.trim() : "Cơ sở cầu lông";
        const location = (draft.location ?? { latitude: 10.7769, longitude: 106.7009 }) as Record<string, unknown>;
        const lat = typeof location.latitude === "number" ? location.latitude : 10.7769;
        const lng = typeof location.longitude === "number" ? location.longitude : 106.7009;

        const venueData = {
          ownerId: applicantUid,
          name: venueName,
          nameKeywords: generateKeywords(venueName),
          address: typeof draft.address === "string" ? draft.address.trim() : "",
          districtId: typeof draft.districtId === "string" ? draft.districtId.trim() : "",
          location: { latitude: lat, longitude: lng },
          geohash: geohash(lat, lng),
          openTime: typeof draft.openTime === "string" ? draft.openTime : "0600",
          closeTime: typeof draft.closeTime === "string" ? draft.closeTime : "2200",
          phone: typeof draft.phone === "string" ? draft.phone.trim() : (typeof draft.representativePhone === "string" ? draft.representativePhone.trim() : ""),
          amenityIds: Array.isArray(draft.amenityIds) ? draft.amenityIds : [],
          photoPaths: Array.isArray(draft.photoPaths) ? draft.photoPaths : [],
          courtCount: typeof draft.courtCount === "number" ? draft.courtCount : 1,
          minPricePerHour: 0,
          ratingAvg: 0.0,
          ratingCount: 0,
          acceptsRecurring: true,
          status: "ACTIVE",
          createdAt: nowIso,
          updatedAt: nowIso,
        };
        tx.write(createWrite(`venues/${applicationId}`, venueData));

        tx.write(createWrite(`users/${applicantUid}/notifications/${notificationId}`, {
          type: "OWNER_APPROVED",
          title: "Hồ sơ chủ sân đã được duyệt",
          body: `Chúc mừng! Hồ sơ đăng ký cơ sở ${venueName} của bạn đã được phê duyệt.`,
          data: { applicationId, venueId: applicationId },
          isRead: false,
          createdAt: nowIso,
        }));
      } else {
        tx.write(updateWrite(`ownerApplications/${applicationId}`, {
          status: "REJECTED",
          rejectReason: rejectReason,
          reviewedBy: user.uid,
          reviewedAt: nowIso,
        }));

        tx.write(updateWrite(`users/${applicantUid}`, {
          role: "PLAYER",
          ownerStatus: "REJECTED",
          updatedAt: nowIso,
        }));

        tx.write(createWrite(`users/${applicantUid}/notifications/${notificationId}`, {
          type: "OWNER_REJECTED",
          title: "Hồ sơ chủ sân bị từ chối",
          body: `Hồ sơ đăng ký của bạn không được duyệt. Lý do: ${rejectReason}`,
          data: { applicationId, rejectReason },
          isRead: false,
          createdAt: nowIso,
        }));
      }

      return { success: true, status: targetStatus, applicationId };
    });

    await reconcileAdminClaims(targetApplicantUid, deps.firestore.get, deps.mergeClaims);

    return json(200, outcome);
  } catch (error) {
    if (error instanceof FirestoreError && error.code === "ABORTED") {
      return json(409, { error: { code: "APPLICATION_CONFLICT", message: "Hồ sơ vừa thay đổi bởi quản trị viên khác. Vui lòng làm mới." } });
    }
    return toErrorResponse(error);
  }
}
