import { HttpError } from "./auth.ts";

export function requireCurrentAdmin(user: { profile: Record<string, unknown> | null }): void {
  if (user.profile?.role !== "ADMIN" || user.profile.accountStatus !== "ACTIVE" || user.profile.deletionRequestedAt != null) {
    throw new HttpError(403, "FORBIDDEN", "Quyền quản trị không còn hiệu lực");
  }
}
