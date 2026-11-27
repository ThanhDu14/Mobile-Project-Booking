import { ownerBookingCommand, type OwnerBookingDependencies } from "../_shared/owner-booking-command.ts";
export type OwnerApproveBookingDeps = OwnerBookingDependencies;
export const handleOwnerApproveBooking = (request: Request, deps: OwnerApproveBookingDeps = {}): Promise<Response> =>
  ownerBookingCommand(request, "APPROVE", deps);
