// Hosted owner-operation fixtures, not a PLAYER checkout test. Dry-run by default.
// Only creates three new bookings + matching slots in the explicitly authorized AUDIT venue.
const auth = require('C:/Users/ACER/AppData/Local/npm-cache/_npx/735f5eb4e60f1e66/node_modules/firebase-tools/lib/auth');
const project='mobile-22e6c', venueId='1b9faac0-11df-47c3-8110-bf928265fb4f';
const root='projects/'+project+'/databases/(default)/documents/';
const apply=process.argv.includes('--apply');
function encode(value) {
  if(value==null)return {nullValue:null};
  if(value instanceof Date)return {timestampValue:value.toISOString()};
  if(typeof value==='string')return {stringValue:value};
  if(typeof value==='boolean')return {booleanValue:value};
  if(typeof value==='number'&&Number.isSafeInteger(value))return {integerValue:String(value)};
  if(Array.isArray(value))return {arrayValue:{values:value.map(encode)}};
  if(typeof value==='object')return {mapValue:{fields:Object.fromEntries(Object.entries(value).map(([key,val])=>[key,encode(val)]))}};
  throw new Error('Unsupported fixture value');
}
const str=(doc,key)=>doc?.fields?.[key]?.stringValue;
const num=(doc,key)=>Number(doc?.fields?.[key]?.integerValue);
const hhmm=minutes=>String(Math.floor(minutes/60)).padStart(2,'0')+String(minutes%60).padStart(2,'0');
async function main() {
  const account=auth.getGlobalDefaultAccount();if(!account)throw new Error('Firebase CLI login required');
  const token=await auth.getAccessToken(account.tokens.refresh_token,['https://www.googleapis.com/auth/cloud-platform']);
  async function request(path,body) {
    const response=await fetch('https://firestore.googleapis.com/v1/projects/'+project+'/databases/(default)/'+path,{
      method:body?'POST':'GET',headers:{Authorization:'Bearer '+token.access_token,'Content-Type':'application/json'},
      ...(body?{body:JSON.stringify(body)}:{})});
    const result=await response.json();
    if(!response.ok)throw new Error('Firestore '+response.status+' '+(result.error?.status||'request failed'));
    return result;
  }
  const application=await request('documents/ownerApplications/'+venueId);
  const uid=str(application,'uid');
  if(!uid||uid.includes('/')||str(application,'status')!=='APPROVED')throw new Error('Approved AUDIT application required');
  const courts=await request('documents/venues/'+venueId+'/courts');
  const court=courts.documents?.find(doc=>str(doc,'name')==='AUDIT151126C1'&&doc.fields.isActive?.booleanValue===true);
  const courtId=court?.name.split('/').pop();
  if(!courtId||!/^[A-Za-z0-9]{20}$/.test(courtId))throw new Error('Create the AUDIT151126C1 court through the app first');
  if(process.argv.includes('--inspect')) {
    for(const kind of ['checkin','reject','cancel']) {
      const id='owner-audit151126-'+kind;
      const doc=await request('documents/bookings/'+id);
      const fields=doc.fields||{};
      const allowed=['status','paymentStatus','paymentMethod','total','paidAmount','remainingAmount','checkedInAt','startAt','endAt','rejectReason','cancelReason','refundAmount'];
      console.log(JSON.stringify({id,...Object.fromEntries(allowed.filter(key=>fields[key]).map(key=>[key,Object.values(fields[key])[0]]))}));
    }
    return;
  }
  const now=new Date(), local=new Date(now.getTime()+7*3600000);
  const today=local.toISOString().slice(0,10), tomorrow=new Date(local.getTime()+86400000).toISOString().slice(0,10);
  const minute=local.getUTCHours()*60+local.getUTCMinutes()+local.getUTCSeconds()/60;
  const first=Math.max(22*60+30,Math.ceil((minute+10)/30)*30);
  if(first>23*60)throw new Error('No remaining same-day check-in test window; no data written');
  const timestamp=(day,time)=>new Date(day+'T'+time.slice(0,2)+':'+time.slice(2)+':00+07:00');
  const plans=[['checkin',today,hhmm(first)],['reject',tomorrow,'2230'],['cancel',tomorrow,'2300']].map(([kind,day,time])=>{
    const startAt=timestamp(day,time),endAt=new Date(startAt.getTime()+1800000);
    return {id:'owner-audit151126-'+kind,kind,day,date:day.replaceAll('-',''),time,startAt,endAt,
      endTime:new Date(endAt.getTime()+7*3600000).toISOString().slice(11,16).replace(':',''),
      slotId:courtId+'_'+day.replaceAll('-','')+'_'+time};
  });
  const rules=await request('documents/venues/'+venueId+'/priceRules');
  const rule=rules.documents?.find(doc=>num(doc,'pricePerHour')===4000&&str(doc,'startTime')==='2230'&&str(doc,'endTime')==='2330');
  if(!rule)throw new Error('Create the 22:30–23:30 / 4000 VND-hour test price rule through the app first');
  const ruleId=rule.name.split('/').pop();
  const gatePaths=['ownerApplications/'+venueId,'venues/'+venueId,'users/'+uid,'venues/'+venueId+'/courts/'+courtId,'venues/'+venueId+'/priceRules/'+ruleId];
  const targetPaths=plans.flatMap(plan=>['bookings/'+plan.id,'slots/'+plan.slotId]);
  const begin=await request('documents:beginTransaction',{options:{readWrite:{}}});
  const tx=begin.transaction;
  let committed=false;
  try {
    const rows=await request('documents:batchGet',{documents:[...gatePaths,...targetPaths].map(path=>root+path),transaction:tx});
    const docs=new Map(rows.filter(row=>row.found).map(row=>[row.found.name.slice(root.length),row.found]));
    const venue=docs.get(gatePaths[1]),user=docs.get(gatePaths[2]),currentCourt=docs.get(gatePaths[3]),currentRule=docs.get(gatePaths[4]);
    if(str(docs.get(gatePaths[0]),'status')!=='APPROVED'||str(venue,'name')!=='AUDIT151126'||str(venue,'ownerId')!==uid||
      str(venue,'status')!=='ACTIVE'||str(user,'role')!=='OWNER'||str(user,'ownerStatus')!=='APPROVED'||str(user,'accountStatus')!=='ACTIVE'||
      str(currentCourt,'name')!=='AUDIT151126C1'||currentCourt.fields.isActive?.booleanValue!==true||num(currentRule,'pricePerHour')!==4000)
      throw new Error('AUDIT ownership/configuration guard failed; no data written');
    const weekdays=currentRule.fields.daysOfWeek?.arrayValue?.values?.map(value=>Number(value.integerValue))||[];
    for(const plan of plans) {
      const dayOfWeek=new Date(plan.startAt.getTime()+7*3600000).getUTCDay()||7;
      if(!weekdays.includes(dayOfWeek)||plan.time<str(venue,'openTime')||plan.endTime>str(venue,'closeTime')||
        plan.time<str(currentRule,'startTime')||plan.endTime>str(currentRule,'endTime'))throw new Error('Fixture is outside configured hours/price coverage');
    }
    const existing=plans.filter(plan=>docs.has('bookings/'+plan.id));
    if(existing.length)throw new Error('Fixture IDs already exist; refusing to overwrite or reset existing records');
    if(targetPaths.some(path=>docs.has(path)))throw new Error('A target slot is already occupied; no data written');
    const summary={mode:apply?'apply':'dry-run',venueId,courtId,amountPerBooking:2000,bookings:plans.map(plan=>({id:plan.id,startAt:plan.startAt.toISOString(),endAt:plan.endAt.toISOString()}))};
    if(!apply){console.log(JSON.stringify(summary));return;}
    const writes=plans.flatMap(plan=>{
      const booking={userId:uid,venueId,status:'PENDING',paymentMethod:'AT_VENUE',paymentStatus:'UNPAID',
        customerDisplayName:'AUDIT151126',courtNames:['AUDIT151126C1'],date:plan.date,
        startAt:plan.startAt,endAt:plan.endAt,subtotal:2000,discount:0,total:2000,depositAmount:0,paidAmount:0,remainingAmount:2000,
        slotIds:[plan.slotId],items:[{slotId:plan.slotId,courtId,courtName:'AUDIT151126C1',startAt:plan.startAt,endAt:plan.endAt,price:2000}],
        createdAt:now,updatedAt:now,pendingAt:now,approvalDeadline:new Date(Math.min(now.getTime()+3600000,plan.startAt.getTime()))};
      const slot={venueId,courtId,date:plan.date,startTime:plan.time,endTime:plan.endTime,startAt:plan.startAt,endAt:plan.endAt,status:'BOOKED',bookingId:plan.id};
      return [['bookings/'+plan.id,booking],['slots/'+plan.slotId,slot]].map(([path,value])=>({update:{name:root+path,fields:encode(value).mapValue.fields},currentDocument:{exists:false}}));
    });
    await request('documents:commit',{transaction:tx,writes});committed=true;
    console.log(JSON.stringify({...summary,createdBookings:3,createdSlots:3}));
  } finally {
    if(!committed)await request('documents:rollback',{transaction:tx}).catch(()=>{});
  }
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
