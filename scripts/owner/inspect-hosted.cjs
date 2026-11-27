// Read-only verification. OAuth credentials stay in memory and are never written or printed.
const auth = require('C:/Users/ACER/AppData/Local/npm-cache/_npx/735f5eb4e60f1e66/node_modules/firebase-tools/lib/auth');
const project = 'mobile-22e6c';
async function main() {
  const account = auth.getGlobalDefaultAccount();
  if (!account) throw new Error('Firebase CLI login required');
  const token = await auth.getAccessToken(account.tokens.refresh_token, ['https://www.googleapis.com/auth/cloud-platform']);
  async function request(path, body) {
    const response = await fetch('https://firestore.googleapis.com/v1/projects/'+project+'/databases/(default)/'+path, {
      method:body?'POST':'GET', headers:{Authorization:'Bearer '+token.access_token,'Content-Type':'application/json'},
      ...(body?{body:JSON.stringify(body)}:{})
    });
    const data=await response.json();
    if(!response.ok) throw new Error('Firestore '+response.status+': '+(data.error?.status || 'request failed'));
    return data;
  }
  const field = await request('collectionGroups/vouchers/fields/code');
  const applicationIndexes=await request('collectionGroups/ownerApplications/indexes');
  console.log(JSON.stringify({applicationIndexes:applicationIndexes.indexes?.filter(i=>i.name.includes('/collectionGroups/ownerApplications/')).map(i=>({state:i.state,scope:i.queryScope,fields:i.fields.map(f=>({field:f.fieldPath,order:f.order}))}))}));
  const districts=await request('documents:runQuery',{structuredQuery:{from:[{collectionId:'districts'}],where:{fieldFilter:{field:{fieldPath:'isActive'},op:'EQUAL',value:{booleanValue:true}}}}});
  console.log(JSON.stringify({activeDistrictCount:districts.filter(r=>r.document).length}));
  console.log(JSON.stringify({voucherIndexes:field.indexConfig?.indexes?.map(i=>({scope:i.queryScope,state:i.state}))}));
  const venueId='1b8e5cda-a68f-4f53-913e-461c11b9bb2b'; // Venue explicitly authorized for this audit.
  const rows=[{document:await request('documents/venues/'+venueId)}];
  for(const row of rows){
    if(!row.document) continue;
    const id=row.document.name.split('/').pop();
    const response=await request('documents/venues/'+encodeURIComponent(id)+'/vouchers');
    console.log(JSON.stringify({venueId:id,vouchers:(response.documents||[]).map(doc=>({code:doc.fields.code?.stringValue,isActive:doc.fields.isActive?.booleanValue,usedCount:doc.fields.usedCount?.integerValue,value:doc.fields.value?.integerValue}))}));
    for(const collection of ['courts','priceRules']) {
      const data=await request('documents/venues/'+encodeURIComponent(id)+'/'+collection);
      console.log(JSON.stringify({collection,documents:(data.documents||[]).map(doc=>({id:doc.name.split('/').pop(),name:doc.fields.name?.stringValue,isActive:doc.fields.isActive?.booleanValue,
        start:doc.fields.startTime?.stringValue,end:doc.fields.endTime?.stringValue,pricePerHour:doc.fields.pricePerHour?.integerValue}))}));
    }
    const slots=await request('documents:runQuery',{structuredQuery:{from:[{collectionId:'slots'}],where:{fieldFilter:{field:{fieldPath:'venueId'},op:'EQUAL',value:{stringValue:id}}}}});
    console.log(JSON.stringify({minPricePerHour:row.document.fields.minPricePerHour,photoCount:row.document.fields.photoPaths?.arrayValue?.values?.length||0,slots:slots.filter(r=>r.document).map(r=>({id:r.document.name.split('/').pop(),status:r.document.fields.status?.stringValue,reason:r.document.fields.blockReason?.stringValue}))}));
    const photoStatuses=[];
    for(const value of (row.document.fields.photoPaths?.arrayValue?.values||[])) {
      const path=value.stringValue;
      if(!path)continue;
      const url=path.startsWith('https://')?path:'https://dbjzrpmtslncogipwpai.supabase.co/storage/v1/object/public/venue-photos/'+path.split('/').map(encodeURIComponent).join('/');
      photoStatuses.push((await fetch(url,{method:'HEAD'})).status);
    }
    console.log(JSON.stringify({venuePhotoHttpStatuses:photoStatuses}));
    const sessions=await request('documents:runQuery',{structuredQuery:{from:[{collectionId:'dropInSessions'}],where:{fieldFilter:{field:{fieldPath:'venueId'},op:'EQUAL',value:{stringValue:id}}}}});
    console.log(JSON.stringify({sessions:sessions.filter(r=>r.document).map(r=>({id:r.document.name.split('/').pop(),status:r.document.fields.status?.stringValue,capacity:r.document.fields.capacity?.integerValue,note:r.document.fields.note?.stringValue}))}));
  }
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
