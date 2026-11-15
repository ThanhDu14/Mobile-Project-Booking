import { HttpError, errorResponse, verifyIdToken, type VerifiedToken } from "./auth.ts";
import * as fs from "./firestore.ts";

export interface OwnerBookingDependencies {
  firestore?: Pick<typeof fs,"get"|"commit"|"runTransaction">;
  verifyToken?: (request:Request)=>Promise<VerifiedToken>;
}

type Decision="APPROVE"|"REJECT"|"CANCEL";
const timestamp=(value:unknown)=>value instanceof Date ? value.getTime() : typeof value === "string" ? Date.parse(value) : NaN;
const money=(value:unknown)=>typeof value === "number" && Number.isSafeInteger(value) && value>=0 ? value : 0;

export async function ownerBookingCommand(req:Request,decision:Decision,deps:OwnerBookingDependencies={}):Promise<Response> {
  if(req.method!=="POST") return errorResponse(405,"METHOD_NOT_ALLOWED","Chỉ hỗ trợ POST");
  try {
    const token=await (deps.verifyToken ?? verifyIdToken)(req);
    const source=deps.firestore ?? fs;
    const body=await req.json().catch(()=>({}));
    const venueId=typeof body.venueId === "string" ? body.venueId.trim() : "";
    const bookingId=typeof body.bookingId === "string" ? body.bookingId.trim() : "";
    const reason=typeof body.reason === "string" ? body.reason.trim() : "";
    if(!venueId || venueId.includes("/") || !bookingId || bookingId.includes("/")) throw new HttpError(400,"INVALID_TARGET","Đơn đặt hoặc cơ sở không hợp lệ");
    if(decision!=="APPROVE" && !reason) throw new HttpError(400,"INVALID_REASON","Nhập lý do từ chối hoặc hủy");
    const result=await source.runTransaction(async tx=>{
      const user=await tx.get(`users/${token.uid}`);
      const venue=await tx.get(`venues/${venueId}`);
      const admin=token.appRole==="ADMIN" && user?.role==="ADMIN";
      if(!user || user.accountStatus!=="ACTIVE" || user.deletionRequestedAt!=null ||
        (!admin && (token.appRole!=="OWNER" || user.role!=="OWNER" || user.ownerStatus!=="APPROVED")) ||
        !venue || (!admin && venue.ownerId!==token.uid)) throw new HttpError(403,"FORBIDDEN","Không có quyền vận hành cơ sở này");
      if(venue.status!=="ACTIVE") throw new HttpError(403,"VENUE_UNAVAILABLE","Cơ sở đang ẩn hoặc bị khóa");
      const path=`bookings/${bookingId}`;
      const booking=await tx.get(path);
      if(!booking || booking.venueId!==venueId) throw new HttpError(404,"NOT_FOUND","Không tìm thấy đơn đặt trong cơ sở");
      const ledgerPath=`refunds/refund_${bookingId}`;
      const ledger=decision!=="APPROVE" ? await tx.get(ledgerPath) : null;
      if(decision==="CANCEL" && booking.status==="CANCELLED" && ledger) return {success:true,bookingId,status:"CANCELLED",refundAmount:money(ledger.refundAmount)};
      const required=decision==="CANCEL"?"CONFIRMED":"PENDING";
      if(booking.status!==required) throw new HttpError(409,"INVALID_BOOKING_STATUS","Trạng thái đơn đã thay đổi. Hãy tải lại.");
      const now=Date.now();
      if(decision!=="CANCEL" && (!Number.isFinite(timestamp(booking.approvalDeadline)) || timestamp(booking.approvalDeadline)<=now)) {
        throw new HttpError(409,"DEADLINE_EXPIRED","Đơn đã quá hạn hoặc thiếu hạn duyệt hợp lệ");
      }
      if(decision==="CANCEL" && (booking.checkedInAt || !Number.isFinite(timestamp(booking.startAt)) || timestamp(booking.startAt)<=now)) {
        throw new HttpError(409,"BOOKING_ALREADY_STARTED","Đơn đã bắt đầu hoặc đã check-in, không thể hủy");
      }
      const items=Array.isArray(booking.items)?booking.items:[];
      const slotIds=[...new Set([...(Array.isArray(booking.slotIds)?booking.slotIds:[]),...items.map(item=>item?.slotId)]
        .filter((id):id is string=>typeof id==="string" && id.length>0))];
      if(!slotIds.length) throw new HttpError(409,"SLOT_MISMATCH","Đơn thiếu khung giờ hợp lệ");
      for(const id of slotIds) {
        if(id.includes("/")) throw new HttpError(409,"SLOT_MISMATCH","Khung giờ không hợp lệ");
        const slot=await tx.get(`slots/${id}`);
        if(!slot || slot.venueId!==venueId || slot.bookingId!==bookingId || slot.status!=="BOOKED") {
          throw new HttpError(409,"SLOT_MISMATCH","Khung giờ đã thay đổi hoặc không thuộc đơn này");
        }
      }
      let refundAmount=0;
      let customer:Record<string,unknown>|null=null;
      const customerId=typeof booking.userId==="string" ? booking.userId : "";
      if(!customerId || customerId.includes("/")) throw new HttpError(409,"INVALID_CUSTOMER","Đơn thiếu thông tin người đặt");
      if(decision!=="APPROVE") {
        if(ledger) throw new HttpError(409,"REFUND_CONFLICT","Đơn đã có bản ghi hoàn tiền, hãy tải lại");
        refundAmount=booking.paymentStatus==="PAID" ? money(booking.paidAmount ?? booking.total) :
          booking.paymentStatus==="DEPOSITED" ? money(booking.paidAmount ?? booking.depositAmount) : 0;
        if(booking.paymentMethod==="AT_VENUE" && booking.paymentStatus!=="PAID") refundAmount=0;
        if(refundAmount>0) {
          customer=await tx.get(`users/${customerId}`);
          if(!customer) throw new HttpError(409,"REFUND_UNAVAILABLE","Không tìm thấy ví người đặt để hoàn tiền");
        }
      }
      const at=new Date(now);
      const status=decision==="APPROVE"?"CONFIRMED":decision==="REJECT"?"REJECTED":"CANCELLED";
      const fields:Record<string,unknown>={status,updatedAt:at};
      if(decision==="APPROVE") { fields.qrToken=crypto.randomUUID();fields.confirmedAt=at; }
      else {
        Object.assign(fields,{refundAmount,refundStatus:"COMPLETED",...(refundAmount>0?{paymentStatus:"REFUNDED"}:{})});
        if(decision==="REJECT") Object.assign(fields,{rejectedAt:at,rejectionReason:reason});
        else Object.assign(fields,{cancelledAt:at,cancelReason:reason,cancelledBy:"OWNER"});
        for(const id of slotIds) tx.write(fs.deleteWrite(`slots/${id}`,true));
        if(customer) tx.write(fs.updateWrite(`users/${customerId}`,{walletBalance:money(customer.walletBalance)+refundAmount,updatedAt:at}));
        tx.write(fs.createWrite(ledgerPath,{bookingId,userId:customerId,requestId:`owner_${decision}_${bookingId}`,refundAmount,penaltyAmount:0,createdAt:at}));
      }
      tx.write(fs.updateWrite(path,fields));
      tx.write(fs.createWrite(`users/${customerId}/notifications/${crypto.randomUUID()}`,{
        type:decision==="APPROVE"?"BOOKING_CONFIRMED":decision==="REJECT"?"BOOKING_REJECTED":"BOOKING_CANCELLED",
        title:decision==="APPROVE"?"Đơn đặt đã được xác nhận":"Đơn đặt đã được từ chối hoặc hủy",
        body:decision==="APPROVE"?"Chủ sân đã duyệt đơn của bạn.":`${reason}. Đã hoàn ${refundAmount} đ vào ví.`,
        data:{bookingId,venueId},isRead:false,createdAt:at,
      }));
      return {success:true,bookingId,status,refundAmount,confirmedAt:decision==="APPROVE"?at.toISOString():undefined};
    });
    return Response.json(result);
  } catch(error) {
    if(error instanceof HttpError) return errorResponse(error.status,error.code,error.message);
    if(error instanceof fs.FirestoreError && ["ABORTED","ALREADY_EXISTS","NOT_FOUND"].includes(error.code)) return errorResponse(409,"BOOKING_CONFLICT","Đơn vừa thay đổi. Hãy tải lại trước khi thử lại.");
    return errorResponse(500,"INTERNAL_ERROR","Không xác định được kết quả. Hãy tải lại đơn để kiểm tra.");
  }
}
