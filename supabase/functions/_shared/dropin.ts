// Shared drop-in helper functions for Feature 112 / Feature 080 / 081
import * as defaultFirestore from "./firestore.ts";
import { updateWrite, deleteWrite } from "./firestore.ts";

export interface DropInRegistration {
  id: string;
  sessionId: string;
  userId: string;
  status: "REGISTERED" | "WAITLISTED" | "CHECKED_IN" | "CANCELLED";
  paymentStatus: "PAID" | "REFUNDED";
  paidAmount: number;
}

export function cancelRegistrationInTx(
  registrationId: string,
  reason: string,
  paidAmount: number,
): defaultFirestore.Write {
  return updateWrite(`registrations/${registrationId}`, {
    status: "CANCELLED",
    cancelReason: reason,
    cancelledAt: new Date().toISOString(),
    refundAmount: paidAmount,
    refundStatus: "COMPLETED",
    updatedAt: new Date().toISOString(),
  });
}

export function closeWaitlistInTx(
  sessionId: string,
  reason: string,
): defaultFirestore.Write {
  return updateWrite(`dropInSessions/${sessionId}`, {
    waitlistStatus: "CLOSED",
    closeReason: reason,
    updatedAt: new Date().toISOString(),
  });
}
