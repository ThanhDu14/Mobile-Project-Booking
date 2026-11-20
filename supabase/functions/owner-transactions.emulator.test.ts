import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "./_shared/firestore.ts";
import { bearer, emulatorToken } from "./_shared/testing.ts";
import { handleManageSlotBlocks } from "./manage-slot-blocks/handler.ts";
import { handleOwnerApproveBooking } from "./owner-approve-booking/handler.ts";
import { handleOwnerRejectBooking } from "./owner-reject-booking/handler.ts";
import { handleOwnerCancelBooking } from "./owner-cancel-booking/handler.ts";
import { handleOwnerStatisticsSource } from "./owner-statistics-source/handler.ts";
import { handleCheckinBooking } from "./checkin-booking/handler.ts";

const host=Deno.env.get("FIRESTORE_EMULATOR_HOST");
const local=!!host && /^(127\.0\.0\.1|localhost):\d+$/.test(host);
if(local) {
  Deno.env.set("FIREBASE_PROJECT_ID","demo-owner-concurrency");
  Deno.env.set("FIREBASE_AUTH_EMULATOR_HOST",Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") ?? "127.0.0.1:9099");
}
const request=(body:Record<string,unknown>)=>bearer(emulatorToken({sub:"owner",appRole:"OWNER"}),{body:JSON.stringify({venueId:"v1",courtId:"c1",...body})});
async function seed(suffix:string) {
  if (!await fs.get("users/owner")) await fs.commit([
    fs.createWrite("users/owner",{role:"OWNER",ownerStatus:"APPROVED",accountStatus:"ACTIVE"}),
    fs.createWrite("venues/v1",{ownerId:"owner",status:"ACTIVE",openTime:"0600",closeTime:"2200"}),
    fs.createWrite("venues/v1/courts/c1",{isActive:true}),
  ]);
  return `c1_20991201_${suffix}`;
}

Deno.test({name:"owner slot transaction: concurrent LOCK requests have exactly one winner",ignore:!local,fn:async()=>{
  const slot=await seed("0800");
  const lock=()=>handleManageSlotBlocks(request({action:"LOCK",date:"20991201",slotIds:[slot],blockReason:"MAINTENANCE"}));
  const responses=await Promise.all([lock(),lock()]);
  assertEquals(responses.map(r=>r.status).sort(),[200,409]);
  for(const response of responses) await response.body?.cancel();
  assertEquals((await fs.get(`slots/${slot}`))?.status,"BLOCKED");
}});

Deno.test({name:"owner slot transaction: any booked slot rolls back the entire range",ignore:!local,fn:async()=>{
  const free=await seed("0900"); const booked=await seed("0930");
  await fs.commit([fs.createWrite(`slots/${booked}`,{venueId:"v1",courtId:"c1",status:"BOOKED"})]);
  const response=await handleManageSlotBlocks(request({action:"LOCK",date:"20991201",slotIds:[free,booked],blockReason:"EVENT"}));
  assertEquals(response.status,409); await response.body?.cancel();
  assertEquals(await fs.get(`slots/${free}`),null);
  assertEquals((await fs.get(`slots/${booked}`))?.status,"BOOKED");
}});

Deno.test({name:"owner slot transaction: rejects forged unlock from a different venue",ignore:!local,fn:async()=>{
  const slot=await seed("1000");
  await fs.commit([fs.createWrite(`slots/${slot}`,{venueId:"foreign",courtId:"other",status:"BLOCKED",blockReason:"MAINTENANCE"})]);
  const response=await handleManageSlotBlocks(request({action:"UNLOCK",slotIds:[slot]}));
  assertEquals(response.status,403); await response.body?.cancel();
  assertEquals((await fs.get(`slots/${slot}`))?.venueId,"foreign");
}});

Deno.test({name:"owner slot transaction: can block AVAILABLE then safely unlock without losing a booking",ignore:!local,fn:async()=>{
  const slot=await seed("1100");
  await fs.commit([fs.createWrite(`slots/${slot}`,{venueId:"v1",courtId:"c1",status:"AVAILABLE"})]);
  const locked=await handleManageSlotBlocks(request({action:"LOCK",date:"20991201",slotIds:[slot],blockReason:"WALK_IN"}));
  assertEquals(locked.status,200); await locked.body?.cancel();
  const [unlocked,booked]=await Promise.all([
    handleManageSlotBlocks(request({action:"UNLOCK",slotIds:[slot]})),
    fs.runTransaction(async tx=>{
      const value=await tx.get(`slots/${slot}`);
      if(value?.status === "BLOCKED") return false;
      tx.write(fs.createWrite(`slots/${slot}`,{venueId:"v1",courtId:"c1",status:"BOOKED"}));
      return true;
    }),
  ]);
  assertEquals(unlocked.status,200); await unlocked.body?.cancel();
  const result=await fs.get(`slots/${slot}`);
  assert(booked ? result?.status === "BOOKED" : result === null);
}});

async function seedBooking(id:string,status="PENDING",paymentStatus="UNPAID",method="AT_VENUE") {
  await seed("1200");
  const customer=`player-${id}`;
  await fs.commit([
    fs.createWrite(`users/${customer}`,{role:"PLAYER",accountStatus:"ACTIVE",walletBalance:5000}),
    fs.createWrite(`bookings/${id}`,{venueId:"v1",userId:customer,status,paymentStatus,paymentMethod:method,total:100000,
      depositAmount:30000,slotIds:[`slot-${id}`],approvalDeadline:new Date(Date.now()+600000),startAt:new Date("2099-12-01T12:00:00Z")}),
    fs.createWrite(`slots/slot-${id}`,{venueId:"v1",courtId:"c1",bookingId:id,status:"BOOKED"}),
  ]);
  return customer;
}

Deno.test({name:"owner booking transaction: approve/reject race commits exactly one decision",ignore:!local,fn:async()=>{
  await seedBooking("race");
  const responses=await Promise.all([
    handleOwnerApproveBooking(request({bookingId:"race"})),
    handleOwnerRejectBooking(request({bookingId:"race",reason:"Audit"})),
  ]);
  assertEquals(responses.map(r=>r.status).sort(),[200,409]);
  for(const response of responses) await response.body?.cancel();
  const booking=await fs.get("bookings/race");
  const slot=await fs.get("slots/slot-race");
  assert(booking?.status==="CONFIRMED" ? slot?.bookingId==="race" : slot===null);
}});

Deno.test({name:"owner booking transaction: full refund is credited once; unpaid at-venue is never credited",ignore:!local,fn:async()=>{
  const customer=await seedBooking("refund","CONFIRMED","PAID","MOCK_WALLET");
  const responses=await Promise.all([1,2].map(()=>handleOwnerCancelBooking(request({bookingId:"refund",reason:"Audit"}))));
  assertEquals(responses.map(r=>r.status),[200,200]);
  for(const response of responses) await response.body?.cancel();
  assertEquals((await fs.get(`users/${customer}`))?.walletBalance,105000);
  assertEquals((await fs.get("refunds/refund_refund"))?.refundAmount,100000);
  assertEquals(await fs.get("slots/slot-refund"),null);
  const unpaid=await seedBooking("unpaid","CONFIRMED");
  const response=await handleOwnerCancelBooking(request({bookingId:"unpaid",reason:"Audit"}));
  assertEquals(response.status,200);await response.body?.cancel();
  assertEquals((await fs.get(`users/${unpaid}`))?.walletBalance,5000);
  assertEquals((await fs.get("refunds/refund_unpaid"))?.refundAmount,0);
}});

Deno.test({name:"owner booking transaction: mismatched slot cannot be deleted or refunded",ignore:!local,fn:async()=>{
  const customer=await seedBooking("mismatch","CONFIRMED","PAID","MOCK_WALLET");
  await fs.commit([fs.updateWrite("slots/slot-mismatch",{bookingId:"another-booking"})]);
  const response=await handleOwnerCancelBooking(request({bookingId:"mismatch",reason:"Audit"}));
  assertEquals(response.status,409);await response.body?.cancel();
  assertEquals((await fs.get("bookings/mismatch"))?.status,"CONFIRMED");
  assertEquals((await fs.get(`users/${customer}`))?.walletBalance,5000);
  assertEquals((await fs.get("slots/slot-mismatch"))?.bookingId,"another-booking");
  assertEquals(await fs.get("refunds/refund_mismatch"),null);
}});

Deno.test({name:"statistics projection: real Firestore range/select queries exclude QR and identity",ignore:!local,fn:async()=>{
  await seed("0800");
  await fs.commit([
    fs.createWrite("bookings/statistics-report",{venueId:"v1",date:"20991210",startAt:new Date("2099-12-10T01:00:00Z"),endAt:new Date("2099-12-10T01:30:00Z"),status:"CONFIRMED",paymentStatus:"PAID",paymentMethod:"MOCK_WALLET",total:100000,qrToken:"PRIVATE_REPORT_QR",userId:"private-statistics-player"}),
    fs.createWrite("slots/statistics-report-slot",{venueId:"v1",courtId:"c1",date:"20991210",startTime:"0800",endTime:"0830",status:"BOOKED",bookingId:"statistics-report"}),
  ]);
  const response=await handleOwnerStatisticsSource(request({startDate:"2099-12-10",endDate:"2099-12-10"}));
  assertEquals(response.status,200);
  const data=await response.json();
  assertEquals(data.bookings.find((b:{bookingId:string})=>b.bookingId==="statistics-report")?.total,100000);
  assertEquals(data.slots.find((s:{slotId:string})=>s.slotId==="statistics-report-slot")?.startAt,"2099-12-10T01:00:00.000Z");
  const json=JSON.stringify(data);
  assert(!json.includes("qrToken"));assert(!json.includes("PRIVATE_REPORT_QR"));assert(!json.includes("private-statistics-player"));
}});

Deno.test({name:"owner reject requires an explicit reason and preserves the booking until supplied",ignore:!local,fn:async()=>{
  await seedBooking("reject-default");
  const response=await handleOwnerRejectBooking(request({bookingId:"reject-default"}));
  assertEquals(response.status,400);await response.body?.cancel();
  assertEquals((await fs.get("bookings/reject-default"))?.status,"PENDING");
  const accepted=await handleOwnerRejectBooking(request({bookingId:"reject-default",reason:"AUDIT required reason"}));
  assertEquals(accepted.status,200);await accepted.body?.cancel();
  const booking=await fs.get("bookings/reject-default");
  assertEquals(booking?.status,"REJECTED");
  assertEquals(booking?.rejectionReason,"AUDIT required reason");
  assertEquals(await fs.get("slots/slot-reject-default"),null);
}});

Deno.test({name:"booking check-in: preview is read-only and concurrent payment confirmations have one winner",ignore:!local,fn:async()=>{
  await seedBooking("checkin","CONFIRMED","UNPAID","AT_VENUE");
  await fs.commit([fs.updateWrite("bookings/checkin",{startAt:new Date(Date.now()+600000),endAt:new Date(Date.now()+3600000)})]);
  const preview=await handleCheckinBooking(request({bookingId:"checkin",confirmPayment:false}));
  assertEquals(preview.status,200);assertEquals((await preview.json()).remainingAmount,100000);
  assertEquals((await fs.get("bookings/checkin"))?.checkedInAt,undefined);
  const responses=await Promise.all([1,2].map(()=>handleCheckinBooking(request({bookingId:"checkin",confirmPayment:true}))));
  assertEquals(responses.map(r=>r.status).sort(),[200,409]);
  for(const response of responses)await response.body?.cancel();
  const booking=await fs.get("bookings/checkin");
  assert(booking?.checkedInAt instanceof Date);assertEquals(booking?.paymentStatus,"PAID");
  assertEquals(booking?.paidAmount,100000);assertEquals(booking?.remainingAmount,0);
}});
