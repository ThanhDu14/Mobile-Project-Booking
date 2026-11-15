// Read-only audit of the explicitly labelled PLAYER test application; never prints credentials or private paths.
const auth = require('C:/Users/ACER/AppData/Local/npm-cache/_npx/735f5eb4e60f1e66/node_modules/firebase-tools/lib/auth');
async function main() {
  const account = auth.getGlobalDefaultAccount();
  if (!account) throw new Error('Firebase CLI login required');
  const token = await auth.getAccessToken(account.tokens.refresh_token, ['https://www.googleapis.com/auth/cloud-platform']);
  async function request(path, body) {
    const response = await fetch('https://firestore.googleapis.com/v1/projects/mobile-22e6c/databases/(default)/documents'+path, {
      method: body ? 'POST' : 'GET', headers: {Authorization:'Bearer '+token.access_token,'Content-Type':'application/json'},
      ...(body ? {body:JSON.stringify(body)} : {})
    });
    const data=await response.json();
    if(!response.ok) throw new Error('Firestore '+response.status);
    return data;
  }
  const rows = await request(':runQuery', {structuredQuery:{from:[{collectionId:'ownerApplications'}],where:{fieldFilter:{field:{fieldPath:'venueDraft.name'},op:'EQUAL',value:{stringValue:'AUDIT151126'}}}}});
  const documents=rows.filter(r=>r.document).map(r=>r.document);
  console.log(JSON.stringify({applicationCount:documents.length}));
  for(const doc of documents) {
    const fields=doc.fields;
    const user=await request('/users/'+encodeURIComponent(fields.uid.stringValue));
    const venues=await request(':runQuery',{structuredQuery:{from:[{collectionId:'venues'}],where:{fieldFilter:{field:{fieldPath:'ownerId'},op:'EQUAL',value:{stringValue:fields.uid.stringValue}}}}});
    const auditVenue=venues.find(r=>r.document?.fields?.name?.stringValue==='AUDIT151126')?.document;
    const publicPhotoStatuses=[];
    for(const photo of auditVenue?.fields?.photoPaths?.arrayValue?.values||[]) {
      const path=photo.stringValue;
      const url=path.startsWith('https://')?path:'https://dbjzrpmtslncogipwpai.supabase.co/storage/v1/object/public/venue-photos/'+path.split('/').map(encodeURIComponent).join('/');
      publicPhotoStatuses.push((await fetch(url,{method:'HEAD'})).status);
    }
    console.log(JSON.stringify({applicationId:doc.name.split('/').pop(),status:fields.status?.stringValue,
      rejectReason:fields.rejectReason?.stringValue||null,
      ownerStatus:user.fields.ownerStatus?.stringValue,role:user.fields.role?.stringValue,
      photos:fields.venueDraft?.mapValue?.fields?.photoPaths?.arrayValue?.values?.length||0,
      documents:fields.documentPaths?.arrayValue?.values?.length||0,
      ownedVenues:venues.filter(r=>r.document).length,
      venueCreationTimes:venues.filter(r=>r.document).map(r=>({id:r.document.name.split('/').pop(),createdAt:r.document.fields.createdAt?.timestampValue||r.document.fields.createdAt?.stringValue||null})),
      submittedAt:fields.submittedAt?.timestampValue||fields.submittedAt?.stringValue||null,
      auditVenue:auditVenue?{id:auditVenue.name.split('/').pop(),status:auditVenue.fields.status?.stringValue,locationType:auditVenue.fields.location?.geoPointValue?'GeoPoint':'other',publicPhotoStatuses}:null}));
  }
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
