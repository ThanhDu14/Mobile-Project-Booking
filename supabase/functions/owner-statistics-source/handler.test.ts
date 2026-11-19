import { assertEquals, assert } from "jsr:@std/assert@1.0.19";
import { handleOwnerStatisticsSource } from "./handler.ts";
import type { VerifiedToken } from "../_shared/auth.ts";
import type { FirestoreTransaction, Write } from "../_shared/firestore.ts";

const request = (body = {venueId:"v1", startDate:"2099-12-01", endDate:"2099-12-01"}) => new Request("https://localhost/owner-statistics-source",{method:"POST",body:JSON.stringify(body)});
const verifyToken = () => Promise.resolve({uid:"owner",appRole:"OWNER"} as VerifiedToken);
function dependencies(role="OWNER") {
  let queries=0;
  const tx: FirestoreTransaction = {
    get: path=>Promise.resolve(path==="users/owner" ? {role,ownerStatus:"APPROVED",accountStatus:"ACTIVE"} : {ownerId:"owner"}),
    query: (collection,filters,options)=>{
      queries++;
      assert(filters.some(f=>f.field==="venueId" && f.value==="v1"));
      assert(options?.select?.length);
      assert(!options.select.includes("qrToken"));
      assert(options.limit && options.limit<=5001);
      const common={qrToken:"MUST_NOT_LEAVE_SERVER",userId:"private-player",userName:"Private",date:"20991201",startTime:"0800",endTime:"0830"};
      return Promise.resolve([{id:collection==="bookings"?"b1":"s1",path:`${collection}/one`,data:{...common,status:collection==="bookings"?"CONFIRMED":"BOOKED",paymentStatus:"PAID",paymentMethod:"WALLET",total:100000,courtId:"c1",bookingId:"b1"}}]);
    },
    write: ()=>{throw new Error("Report must not write");},
  };
  return {get queryCount(){return queries;},verifyToken,runTransaction: async <T>(operation: (tx: FirestoreTransaction)=>Promise<T | {result:T;writes:Write[]}>):Promise<T> => {
    const result=await operation(tx);
    return result && typeof result==="object" && "result" in result && "writes" in result ? result.result : result as T;
  }};
}

Deno.test("statistics projection is venue-scoped, deduplicated and never returns QR or player identity",async()=>{
  const deps=dependencies();
  const response=await handleOwnerStatisticsSource(request(),deps);
  assertEquals(response.status,200);
  const body=await response.json();
  assertEquals(body.bookings.length,1);
  assertEquals(body.slots.length,1);
  assertEquals(body.bookings[0].startAt,"2099-12-01T01:00:00.000Z");
  assertEquals(body.bookings[0].total,100000);
  const json=JSON.stringify(body);
  for(const secret of ["qrToken","MUST_NOT_LEAVE_SERVER","private-player","userName","Private"]) assert(!json.includes(secret));
  assertEquals(deps.queryCount,4);
});

Deno.test("statistics rejects current PLAYER authority before reading any financial data",async()=>{
  const deps=dependencies("PLAYER");
  assertEquals((await handleOwnerStatisticsSource(request(),deps)).status,403);
  assertEquals(deps.queryCount,0);
});

Deno.test("statistics rejects invalid calendar dates, reversed periods and more than 30 days",async()=>{
  for(const [startDate,endDate] of [["2099-02-30","2099-03-01"],["2099-12-02","2099-12-01"],["2099-01-01","2099-01-31"]]) {
    const deps=dependencies();
    assertEquals((await handleOwnerStatisticsSource(request({venueId:"v1",startDate,endDate}),deps)).status,400);
    assertEquals(deps.queryCount,0);
  }
});
