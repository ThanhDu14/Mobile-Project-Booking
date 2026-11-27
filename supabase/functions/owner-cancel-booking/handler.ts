import { ownerBookingCommand, type OwnerBookingDependencies } from "../_shared/owner-booking-command.ts";
export type OwnerCancelBookingDeps = OwnerBookingDependencies;
export const handleOwnerCancelBooking = (request: Request, deps: OwnerCancelBookingDeps = {}): Promise<Response> =>
  ownerBookingCommand(request, "CANCEL", deps);
