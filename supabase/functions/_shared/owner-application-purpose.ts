import { HttpError } from "./auth.ts";

export type ApplicationPurpose = "OWNER_ONBOARDING" | "VENUE_ADDITION";

/** Missing purpose belongs to the legacy first-time onboarding lifecycle. */
export function applicationPurpose(value: unknown): ApplicationPurpose {
  if (value == null || value === "OWNER_ONBOARDING") return "OWNER_ONBOARDING";
  if (value === "VENUE_ADDITION") return value;
  throw new HttpError(400, "INVALID_APPLICATION_PURPOSE", "Loại hồ sơ không hợp lệ");
}

export function requireApprovedApplicant(user: Record<string, unknown> | null): void {
  if (!user || user.role !== "OWNER" || user.ownerStatus !== "APPROVED" ||
      user.accountStatus !== "ACTIVE" || user.deletionRequestedAt != null) {
    throw new HttpError(403, "OWNER_NOT_APPROVED", "Quyền chủ sân đã thay đổi. Hãy tải lại tài khoản.");
  }
}
