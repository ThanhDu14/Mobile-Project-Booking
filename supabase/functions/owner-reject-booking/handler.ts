import { ownerBookingCommand, type OwnerBookingDependencies } from "../_shared/owner-booking-command.ts";
export type OwnerRejectBookingDeps = OwnerBookingDependencies;
export const handleOwnerRejectBooking = (request: Request, deps: OwnerRejectBookingDeps = {}): Promise<Response> =>
  ownerBookingCommand(request, "REJECT", deps);
