// Read-only assertions against the isolated local instrumentation namespace.
import {assert, assertEquals} from "jsr:@std/assert@1.0.19";
import * as fs from "../../supabase/functions/_shared/firestore.ts";
import {signInEmail} from "../../supabase/functions/_shared/testing.ts";
assert(fs.projectId()==="demo-admin-repair");
assert(Deno.env.get("FIRESTORE_EMULATOR_HOST")==="127.0.0.1:18080");
assert(Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")==="127.0.0.1:19099");
assert(Deno.env.get("SUPABASE_URL")==="http://127.0.0.1:54321");
const owner=await fs.get("users/feature11-owner");
assertEquals([owner?.role,owner?.ownerStatus,owner?.accountStatus],["OWNER","APPROVED","ACTIVE"]);
const venues=await fs.query("venues",[{field:"ownerId",op:"EQUAL",value:"feature11-owner"}]);assertEquals(venues.length,3);
const courts=await fs.query("courts",[],{parent:"venues/feature11-a"});assertEquals(courts.length,3);
const edited=courts.find(row=>row.data.name==="TEST Updated Court");assert(edited);
assertEquals([edited.data.isActive,edited.data.sortOrder],[false,1]);
assertEquals((await fs.get("venues/feature11-a"))?.minPricePerHour,60000);
assertEquals((await fs.get("venues/feature11-a"))?.courtCount,2);
const savedLocation=(await fs.get("venues/feature11-a"))?.location;
assert(savedLocation instanceof fs.FirestoreGeoPoint);
assert(savedLocation.latitude!==10.7 || savedLocation.longitude!==106.6);
assertEquals((await fs.query("priceRules",[],{parent:"venues/feature11-a"})).length,1);
const bookingStates:Record<string,unknown>={};
for(const [id,status] of [["approve","CONFIRMED"],["reject","REJECTED"],["cancel","CANCELLED"]]) {
  const doc=await fs.get(`bookings/feature11-${id}`);assertEquals(doc?.status,status);bookingStates[id]=doc?.status;
}
assertEquals((await fs.get("users/feature11-player"))?.walletBalance,130000);
const checked=await fs.get("bookings/feature11-checkin");assertEquals(checked?.paymentStatus,"PAID");assert(checked?.checkedInAt instanceof Date);
const tomorrow=new Date(Date.now()+86400000+7*3600000).toISOString().slice(0,10).replaceAll("-","");
for(const time of ["1800","1830"]) assertEquals(await fs.get(`slots/feature11-a-c1_${tomorrow}_${time}`),null);
assertEquals((await fs.get(`slots/feature11-a-c1_${tomorrow}_1000`))?.status,"BOOKED");
const sessionId=`dropin_feature11-b-c1_${tomorrow}_1800`,session=await fs.get(`dropInSessions/${sessionId}`);
assertEquals(session?.capacity,10);assertEquals((session?.courtIds as unknown[]).length,2);
for(const slotId of session?.slotIds as string[]) {
  const slot=await fs.get(`slots/${slotId}`);assertEquals([slot?.status,slot?.blockReason,slot?.sessionId],["BLOCKED","DROP_IN",sessionId]);
}
assertEquals((await fs.get("dropInSessions/feature11-checkin-session/registrations/feature11-player"))?.status,"CHECKED_IN");
const voucher=await fs.get("venues/feature11-b/vouchers/FEATURE11TEST");assertEquals([voucher?.value,voucher?.isActive,voucher?.usedCount],[15,false,0]);
assertEquals(await fs.get("venues/feature11-a/vouchers/FEATURE11TEST"),null);
const signin=await signInEmail("feature11-owner@example.test","Test123456");assertEquals(signin.status,200);
const identity=await signin.json();assert(typeof identity.idToken==="string");
const date=new Date(Date.now()-86400000+7*3600000).toISOString().slice(0,10);
const response=await fetch("http://127.0.0.1:54321/functions/v1/owner-statistics-source",{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${identity.idToken}`,apikey:Deno.env.get("SUPABASE_ANON_KEY")!},body:JSON.stringify({venueId:"feature11-stats",startDate:date,endDate:date})});
assertEquals(response.status,200);const report=await response.json();
assertEquals(report.bookings.length,3);assertEquals(report.slots.length,3);
assertEquals(report.availability.courtIds.length,2);assertEquals([report.availability.openTime,report.availability.closeTime],["0600","1000"]);
const sum=(status:string,field:string)=>report.bookings.filter((b:Record<string,unknown>)=>b.paymentStatus===status).reduce((n:number,b:Record<string,number>)=>n+b[field],0);
const paid=sum("PAID","total"),deposited=sum("DEPOSITED","depositAmount"),refunded=sum("REFUNDED","refundAmount");assertEquals([paid,deposited,refunded],[100000,30000,20000]);
const blocked=report.slots.filter((slot:Record<string,unknown>)=>slot.status==="BLOCKED");assertEquals(blocked.length,1);
const occupied=report.slots.filter((slot:Record<string,unknown>)=>slot.status==="BOOKED");assertEquals(occupied.length,2);
assert(occupied.every((slot:Record<string,string>)=>new Date(Date.parse(slot.startAt)+7*3600000).getUTCHours()===8));
for(const secret of ["qrToken","PRIVATE_TEST_QR","feature11-player"]) assert(!JSON.stringify(report).includes(secret));
console.log(JSON.stringify({project:fs.projectId(),backend:"local",owner:{role:owner?.role,status:owner?.ownerStatus,venues:venues.length},courts:{count:courts.length,activeCount:2,editedCourtActive:edited.data.isActive,editedCourtOrder:edited.data.sortOrder},map:{latitude:savedLocation.latitude,longitude:savedLocation.longitude},minPricePerHour:60000,bookingStates,refundedWallet:130000,bookingCheckedIn:true,manualBlocksReleased:2,dropIn:{capacity:session?.capacity,courts:2,slotsPreserved:4,checkedIn:true},voucher:{value:15,isActive:false,usedCount:0,correctVenue:true},statistics:{paid,deposited,refunded,occupancyPercent:2/15*100,peakHour:8,peakMinutes:60,qrPrivate:true}}));
